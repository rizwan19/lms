package com.saasresearcher.learning.certificates.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.certificates.api.CertificateService;
import com.saasresearcher.learning.certificates.api.CertificateStatus;
import com.saasresearcher.learning.certificates.api.PublicCertificateStatus;
import com.saasresearcher.learning.certificates.entity.CertificateRow;
import com.saasresearcher.learning.certificates.entity.CertificateSnapshot;
import com.saasresearcher.learning.certificates.repository.CertificateRepository;
import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.completion.api.CompletionListener;
import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.completion.api.CompletionSnapshot;
import com.saasresearcher.learning.completion.api.CompletionStatus;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.institution.api.InstitutionProfile;
import com.saasresearcher.learning.institution.api.InstitutionSettings;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
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
 * Certificate lifecycle (spec §13): PENDING_RENDER → ISSUED, or RENDER_FAILED (retryable); REVOKED by staff or by an
 * invalidated completion. One certificate per completion; retries and reinstatement reuse its ID and snapshot.
 */
@Service
public class CertificateIssueService implements CertificateService, CompletionListener {

    public static final String RENDER_JOB = "certificates.render";
    static final String SYSTEM_REVOCATION = "Course completion was invalidated by a grade correction";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificates;
    private final CompletionService completions;
    private final CourseCatalog courses;
    private final IdentityAccess identity;
    private final InstitutionSettings institution;
    private final TeachingAccess teaching;
    private final DurableJobs jobs;
    private final AuditLog audit;
    private final Clock clock;

    CertificateIssueService(CertificateRepository certificates, CompletionService completions, CourseCatalog courses, IdentityAccess identity,
            InstitutionSettings institution, TeachingAccess teaching, DurableJobs jobs, AuditLog audit, Clock clock) {
        this.certificates = certificates;
        this.completions = completions;
        this.courses = courses;
        this.identity = identity;
        this.institution = institution;
        this.teaching = teaching;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
    }

    /** A restored completion never silently un-revokes: issue() is a no-op when a certificate already exists. */
    @Override
    public void achieved(UUID completionId) {
        issue(completionId);
    }

    @Override
    public void invalidated(UUID completionId) {
        certificates.byCompletion(completionId).ifPresent(certificate -> {
            CertificateRow locked = certificates.lock(certificate.id()).orElseThrow();
            if (locked.status() != CertificateStatus.REVOKED) {
                certificates.revoke(locked.id(), SYSTEM_REVOCATION, null, clock.instant());
                audit.record(AuditEntry.success(null, "certificate.revoked", "certificate", locked.id(), Map.of("reason", SYSTEM_REVOCATION)));
            }
        });
    }

    @Override
    @Transactional
    public UUID issue(UUID completionId) {
        return certificates.byCompletion(completionId).map(CertificateRow::id).orElseGet(() -> {
            CompletionSnapshot completion = completions.get(completionId);
            InstitutionProfile profile = institution.get();
            PublishedCourse course = courses.requirePublished(completion.courseVersionId());
            String student = identity.summaries(List.of(completion.studentId())).get(completion.studentId()).displayName();
            String token = profile.certificateVerification() ? newToken() : null;
            CertificateSnapshot snapshot = new CertificateSnapshot(CertificateSnapshot.TEMPLATE_REVISION, profile.name(), profile.logoAssetId(), student,
                    course.title(), course.edition(), LocalDate.ofInstant(completion.achievedAt(), profile.timezone()), token != null,
                    profile.verificationShowsStudent(), token);
            UUID id = UUID.randomUUID();
            CertificateRow row = new CertificateRow(id, completionId, completion.studentId(), completion.classId(), CertificateStatus.PENDING_RENDER,
                    snapshot, "certificates/" + id, false, null, null, null);
            if (!certificates.insert(row, token == null ? null : sha256(token), clock.instant())) {
                return certificates.byCompletion(completionId).orElseThrow().id();
            }
            queueRender(id);
            audit.record(AuditEntry.success(null, "certificate.created", "certificate", id));
            return id;
        });
    }

    @Override
    @Transactional
    public void revoke(UUID certificateId, String reason, Actor actor) {
        CertificateRow certificate = requireStaffAccess(certificateId, actor);
        String trimmed = requireReason(reason);
        if (certificate.status() == CertificateStatus.REVOKED) {
            return;
        }
        certificates.revoke(certificateId, trimmed, actor.userId(), clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "certificate.revoked", "certificate", certificateId, Map.of("reason", trimmed)));
    }

    @Override
    @Transactional
    public void reinstate(UUID certificateId, String reason, Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Only administrators can reinstate certificates");
        }
        String trimmed = requireReason(reason);
        CertificateRow certificate = certificates.lock(certificateId).orElseThrow(ApiException::notFound);
        if (certificate.status() != CertificateStatus.REVOKED) {
            throw ApiException.conflict("NOT_REVOKED", "Only revoked certificates can be reinstated");
        }
        if (completions.get(certificate.completionId()).status() != CompletionStatus.ACHIEVED) {
            throw ApiException.conflict("NOT_ELIGIBLE", "The student does not currently meet the completion requirements");
        }
        certificates.setStatus(certificateId, CertificateStatus.PENDING_RENDER, null, clock.instant());
        queueRender(certificateId);
        audit.record(AuditEntry.success(actor.userId(), "certificate.reinstated", "certificate", certificateId, Map.of("reason", trimmed)));
    }

    @Transactional
    public void retry(UUID certificateId, Actor actor) {
        CertificateRow certificate = requireStaffAccess(certificateId, actor);
        if (certificate.status() != CertificateStatus.RENDER_FAILED) {
            throw ApiException.conflict("NOT_FAILED", "Only failed certificates can be retried");
        }
        certificates.setStatus(certificateId, CertificateStatus.PENDING_RENDER, null, clock.instant());
        queueRender(certificateId);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicCertificateStatus verify(String token) {
        if (!institution.get().certificateVerification() || token == null || token.length() > 100) {
            throw ApiException.notFound();
        }
        CertificateRow certificate = certificates.byVerificationHash(sha256(token)).orElseThrow(ApiException::notFound);
        return switch (certificate.status()) {
            case REVOKED -> PublicCertificateStatus.revoked();
            case ISSUED -> {
                CertificateSnapshot snapshot = certificate.snapshot();
                yield new PublicCertificateStatus("VALID", snapshot.institutionName(), snapshot.courseTitle(), snapshot.completedOn(),
                        snapshot.showStudentName() ? snapshot.studentName() : null);
            }
            case PENDING_RENDER, RENDER_FAILED -> throw ApiException.notFound();
        };
    }

    public CertificateRow require(UUID certificateId) {
        return certificates.find(certificateId).orElseThrow();
    }

    public List<CertificateRow> ofStudent(UUID studentId) {
        return certificates.ofStudent(studentId);
    }

    public List<CertificateRow> ofClass(UUID classId) {
        return certificates.ofClass(classId);
    }

    /** Owners always; staff only for classes they teach. Others see the certificate as missing. */
    public CertificateRow requireReadable(UUID certificateId, Actor actor) {
        CertificateRow certificate = certificates.find(certificateId).orElseThrow(ApiException::notFound);
        if (!certificate.studentId().equals(actor.userId())) {
            if (!actor.isAdministrator() && !actor.has(Role.TEACHER)) {
                throw ApiException.notFound();
            }
            teaching.requireClassTeacher(certificate.classId(), actor);
        }
        return certificate;
    }

    private CertificateRow requireStaffAccess(UUID certificateId, Actor actor) {
        CertificateRow certificate = certificates.lock(certificateId).orElseThrow(ApiException::notFound);
        teaching.requireClassTeacher(certificate.classId(), actor);
        return certificate;
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
