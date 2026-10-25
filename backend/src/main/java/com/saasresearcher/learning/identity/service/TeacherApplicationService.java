package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.common.TeacherStatus;
import com.saasresearcher.learning.identity.api.ApplicationStatus;
import com.saasresearcher.learning.identity.config.IdentityProperties;
import com.saasresearcher.learning.identity.dto.TeacherApplicationViews.ApplicationView;
import com.saasresearcher.learning.identity.dto.TeacherApplicationViews.Decision;
import com.saasresearcher.learning.identity.entity.TeacherApplication;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.infra.TeacherApplicationDecidedHandler;
import com.saasresearcher.learning.identity.infra.TeacherApplicationSubmittedHandler;
import com.saasresearcher.learning.identity.repository.TeacherApplicationRepository;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher review: PENDING → APPROVED | REJECTED. Any other active approved teacher decides; the first decision wins.
 * Lock order is workspace → affected accounts in UUID order → application, shared with account suspension so the
 * reviewer's approval is rechecked after every competing change has committed.
 */
@Service
public class TeacherApplicationService {

    private final TeacherApplicationRepository applications;
    private final UserAccountRepository accounts;
    private final WorkspaceService workspace;
    private final DurableJobs jobs;
    private final AuditService audit;
    private final IdentityProperties properties;
    private final Clock clock;
    private final RowLocks locks;

    TeacherApplicationService(TeacherApplicationRepository applications, UserAccountRepository accounts, WorkspaceService workspace,
            DurableJobs jobs, AuditService audit, IdentityProperties properties, RowLocks locks, Clock clock) {
        this.applications = applications;
        this.accounts = accounts;
        this.workspace = workspace;
        this.jobs = jobs;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.locks = locks;
    }

    /** Opens the first application of a newly verified teacher; runs in the verification transaction. */
    TeacherApplication open(UserAccount applicant) {
        TeacherApplication application = applications.save(new TeacherApplication(applicant.getId(), clock.instant()));
        applicant.setTeacherStatus(TeacherStatus.PENDING, clock.instant());
        jobs.enqueue(JobRequest.now(TeacherApplicationSubmittedHandler.TYPE, application.getId(), "teacher-application-submitted:" + application.getId(),
                clock.instant()));
        audit.record(AuditEntry.success(applicant.getId(), "teacher-application.submitted", "teacher-application", application.getId()));
        return application;
    }

    /** Resubmission after a rejection, allowed once the cooldown has passed; a pending application is returned as is. */
    @Transactional
    public ApplicationView submit(Actor actor) {
        actor.requireVerifiedTeacher();
        workspace.lockWorkspace();
        UserAccount applicant = accounts.lockById(actor.userId()).orElseThrow(ApiException::notFound);
        TeacherApplication latest = applications.findFirstByApplicantIdOrderByCreatedAtDesc(applicant.getId()).orElse(null);
        if (latest != null && latest.isPending()) {
            return view(latest, applicant);
        }
        if (applicant.getTeacherStatus() != TeacherStatus.REJECTED) {
            throw ApiException.conflict(IdentityErrors.APPLICATION_NOT_ALLOWED, "Only rejected teacher accounts can apply again");
        }
        Instant now = clock.instant();
        if (latest != null && latest.getDecidedAt().plus(properties.teacherReapplyCooldown()).isAfter(now)) {
            throw ApiException.conflict(IdentityErrors.APPLICATION_COOLDOWN, "You can apply again 24 hours after a rejection")
                    .with("retryAfter", latest.getDecidedAt().plus(properties.teacherReapplyCooldown()));
        }
        TeacherApplication application = open(applicant);
        applications.flush();
        return view(application, applicant);
    }

    @Transactional(readOnly = true)
    public ApplicationView mine(Actor actor) {
        if (actor.role() != Role.TEACHER) {
            throw ApiException.notFound();
        }
        UserAccount applicant = accounts.findById(actor.userId()).orElseThrow(ApiException::notFound);
        return applications.findFirstByApplicantIdOrderByCreatedAtDesc(actor.userId())
                .map(application -> view(application, applicant))
                .orElseThrow(ApiException::notFound);
    }

    /** The review inbox always shows the current pending set, even for teachers who missed a notification. */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationView> list(ApplicationStatus status, PageQuery query, Actor actor) {
        actor.requireApprovedTeacher();
        Sort order = Sort.by("createdAt", "id");
        Page<TeacherApplication> page = status == null ? applications.findAll(query.pageable(order)) : applications.findByStatus(status, query.pageable(order));
        Map<UUID, UserAccount> applicants = accounts.findByIdIn(page.map(TeacherApplication::getApplicantId).toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, account -> account));
        return PageResponse.of(page, application -> view(application, applicants.get(application.getApplicantId())));
    }

    @Transactional
    public ApplicationView decide(UUID applicationId, Decision decision, Actor actor) {
        actor.requireApprovedTeacher();
        TeacherApplication unlocked = applications.findById(applicationId).orElseThrow(ApiException::notFound);
        if (unlocked.getApplicantId().equals(actor.userId())) {
            throw ApiException.conflict(IdentityErrors.SELF_REVIEW, "You cannot review your own application");
        }
        workspace.lockWorkspace();
        Map<UUID, UserAccount> locked = accounts.lockInOrder(Set.of(unlocked.getApplicantId(), actor.userId())).stream().map(locks::forUpdate)
                .collect(Collectors.toMap(UserAccount::getId, account -> account));
        UserAccount reviewer = locked.get(actor.userId());
        if (reviewer == null || !reviewer.isActiveApprovedTeacher()) {
            throw ApiException.teacherApprovalRequired();
        }
        UserAccount applicant = locked.get(unlocked.getApplicantId());
        TeacherApplication application = locks.forUpdate(unlocked);
        if (!application.isPending()) {
            boolean sameDecision = actor.userId().equals(application.getDecidedBy())
                    && (application.getStatus() == ApplicationStatus.APPROVED) == decision.approve();
            if (sameDecision) {
                return view(application, applicant);
            }
            throw ApiException.conflict(ErrorCodes.APPLICATION_ALREADY_DECIDED, "Another teacher has already decided this application");
        }
        Revisions.require(decision.expectedRevision(), application.getVersion());
        Instant now = clock.instant();
        application.decide(decision.approve(), actor.userId(), AccountRules.reason(decision.reason()), now);
        applicant.setTeacherStatus(decision.approve() ? TeacherStatus.APPROVED : TeacherStatus.REJECTED, now);
        applications.flush();
        jobs.enqueue(JobRequest.now(TeacherApplicationDecidedHandler.TYPE, application.getId(), "teacher-application-decided:" + application.getId(), now));
        audit.record(AuditEntry.success(actor.userId(), decision.approve() ? "teacher-application.approved" : "teacher-application.rejected",
                "teacher-application", application.getId(), Map.of("applicant", applicant.getId())));
        return view(application, applicant);
    }

    private static ApplicationView view(TeacherApplication application, UserAccount applicant) {
        return new ApplicationView(application.getId(), applicant.getId(), applicant.getUsername(), applicant.getDisplayName(), applicant.getEmail(),
                application.getStatus(), application.getReason(), application.getCreatedAt(), application.getDecidedAt(), application.getVersion());
    }
}
