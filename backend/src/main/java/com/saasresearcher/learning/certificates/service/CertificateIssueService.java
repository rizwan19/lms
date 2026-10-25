package com.saasresearcher.learning.certificates.service;

import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.certificates.api.CertificateService;
import com.saasresearcher.learning.certificates.api.CertificateStatus;
import com.saasresearcher.learning.certificates.api.PublicCertificateStatus;
import com.saasresearcher.learning.certificates.entity.Certificate;
import com.saasresearcher.learning.certificates.repository.CertificateRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.completion.api.CompletionListener;
import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.completion.api.CompletionSnapshot;
import com.saasresearcher.learning.completion.api.CompletionStatus;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.workspace.api.WorkspaceData;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Certificate lifecycle: PENDING_RENDER → ISSUED, or RENDER_FAILED (retryable); REVOKED by the course author or by an
 * invalidated completion. One certificate per completion; retries and reinstatement reuse its ID and printed fields. Only
 * the learner and the course author can read it; workspace administration grants no access.
 */
@Service
public class CertificateIssueService implements CertificateService, CompletionListener {

    public static final String RENDER_JOB = "certificates.render";
    static final String SYSTEM_REVOCATION = "Course completion was invalidated by a grade correction";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificates;
    private final CompletionService completions;
    private final CourseCatalogService courses;
    private final CourseOwnershipService ownership;
    private final IdentityAccess identity;
    private final WorkspaceService workspace;
    private final DurableJobs jobs;
    private final AuditService audit;
    private final Clock clock;
    private final RowLocks locks;

    CertificateIssueService(CertificateRepository certificates, CompletionService completions, CourseCatalogService courses, CourseOwnershipService ownership,
                            IdentityAccess identity, WorkspaceService workspace, DurableJobs jobs, AuditService audit, RowLocks locks, Clock clock) {
        this.certificates = certificates;
        this.completions = completions;
        this.courses = courses;
        this.ownership = ownership;
        this.identity = identity;
        this.workspace = workspace;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
        this.locks = locks;
    }

    /** A restored completion never silently un-revokes: issue() is a no-op when a certificate already exists. */
    @Override
    public void achieved(UUID completionId) {
        issue(completionId);
    }

    @Override
    public void invalidated(UUID completionId) {
        certificates.findByCompletionId(completionId).ifPresent(certificate -> {
            Certificate locked = locks.forUpdate(certificate);
            if (locked.getStatus() != CertificateStatus.REVOKED) {
                locked.revoke(SYSTEM_REVOCATION, null, clock.instant());
                certificates.flush();
                audit.record(AuditEntry.success(null, "certificate.revoked", "certificate", locked.getId(), Map.of("reason", SYSTEM_REVOCATION)));
            }
        });
    }

    /** Runs under the enrollment lock of the reconciling transaction, so one completion gets exactly one certificate. */
    @Override
    @Transactional
    public UUID issue(UUID completionId) {
        return certificates.findByCompletionId(completionId).map(Certificate::getId).orElseGet(() -> {
            CompletionSnapshot completion = completions.get(completionId);
            WorkspaceData profile = workspace.getWorkspaceData();
            PublishedCourse course = courses.requirePublished(completion.courseVersionId());
            Map<UUID, AccountSummary> people = identity.summaries(List.of(completion.learnerId(), course.authorId()));
            String token = profile.certificateVerification() ? newToken() : null;
            Certificate certificate = certificates.saveAndFlush(new Certificate(completionId, completion.learnerId(), completion.courseId(),
                    profile.name(), profile.logoAssetId(), people.get(completion.learnerId()).displayName(), course.title(),
                    people.get(course.authorId()).displayName(), course.edition(), LocalDate.ofInstant(completion.achievedAt(), profile.timezone()),
                    profile.verificationShowsStudent(), token, token == null ? null : sha256(token), clock.instant()));
            queueRender(certificate.getId());
            audit.record(AuditEntry.success(null, "certificate.created", "certificate", certificate.getId()));
            return certificate.getId();
        });
    }

    @Override
    @Transactional
    public void revoke(UUID certificateId, String reason, Actor actor) {
        Certificate certificate = requireAuthorAccess(certificateId, actor);
        String trimmed = requireReason(reason);
        if (certificate.getStatus() == CertificateStatus.REVOKED) {
            return;
        }
        certificate.revoke(trimmed, actor.userId(), clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "certificate.revoked", "certificate", certificateId, Map.of("reason", trimmed)));
    }

    /** Approved course author only; requires the completion to be achieved again and re-renders from the original printed fields. */
    @Override
    @Transactional
    public void reinstate(UUID certificateId, String reason, Actor actor) {
        Certificate certificate = requireAuthorAccess(certificateId, actor);
        ownership.requirePublishAuthor(certificate.getCourseId(), actor);
        String trimmed = requireReason(reason);
        if (certificate.getStatus() != CertificateStatus.REVOKED) {
            throw ApiException.conflict("NOT_REVOKED", "Only revoked certificates can be reinstated");
        }
        if (completions.get(certificate.getCompletionId()).status() != CompletionStatus.ACHIEVED) {
            throw ApiException.conflict("NOT_ELIGIBLE", "The learner does not currently meet the completion requirements");
        }
        certificate.queueRender(clock.instant());
        queueRender(certificateId);
        audit.record(AuditEntry.success(actor.userId(), "certificate.reinstated", "certificate", certificateId, Map.of("reason", trimmed)));
    }

    @Transactional
    public void retry(UUID certificateId, Actor actor) {
        Certificate certificate = requireAuthorAccess(certificateId, actor);
        if (certificate.getStatus() != CertificateStatus.RENDER_FAILED) {
            throw ApiException.conflict("NOT_FAILED", "Only failed certificates can be retried");
        }
        certificate.queueRender(clock.instant());
        queueRender(certificateId);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicCertificateStatus verify(String token) {
        if (!workspace.getWorkspaceData().certificateVerification() || token == null || token.length() > 100) {
            throw ApiException.notFound();
        }
        Certificate certificate = certificates.findByVerificationHash(sha256(token)).orElseThrow(ApiException::notFound);
        return switch (certificate.getStatus()) {
            case REVOKED -> PublicCertificateStatus.revoked();
            case ISSUED -> new PublicCertificateStatus("VALID", certificate.getWorkspaceName(), certificate.getCourseTitle(), certificate.getCompletedOn(),
                    certificate.isShowLearnerName() ? certificate.getLearnerName() : null);
            case PENDING_RENDER, RENDER_FAILED -> throw ApiException.notFound();
        };
    }

    @Transactional(readOnly = true)
    public Certificate require(UUID certificateId) {
        return certificates.findById(certificateId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Certificate> ofLearner(UUID learnerId) {
        return certificates.findByLearnerIdOrderByCreatedAtDesc(learnerId);
    }

    @Transactional(readOnly = true)
    public List<Certificate> ofCourse(UUID courseId, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        return certificates.findByCourseIdOrderByCreatedAtDesc(courseId);
    }

    /** The learner always (also after withdrawal); otherwise only the course's author. Others see it as missing. */
    @Transactional(readOnly = true)
    public Certificate requireReadable(UUID certificateId, Actor actor) {
        Certificate certificate = certificates.findById(certificateId).orElseThrow(ApiException::notFound);
        if (!certificate.getLearnerId().equals(actor.userId())) {
            ownership.requireAuthor(certificate.getCourseId(), actor);
        }
        return certificate;
    }

    private Certificate requireAuthorAccess(UUID certificateId, Actor actor) {
        Certificate certificate = certificates.findById(certificateId).orElseThrow(ApiException::notFound);
        ownership.requireAuthor(certificate.getCourseId(), actor);
        return locks.forUpdate(certificate);
    }

    private void queueRender(UUID certificateId) {
        jobs.enqueue(JobRequest.now(RENDER_JOB, certificateId, null, clock.instant()));
    }

    private static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 1000) {
            throw ApiException.invalid("reason", "Give a reason of up to 1000 characters");
        }
        return trimmed;
    }

    /** 192 bits of randomness; only its SHA-256 is stored for lookup. */
    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
