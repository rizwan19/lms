package com.saasresearcher.learning.enrollment.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.api.EnrollmentView;
import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import com.saasresearcher.learning.enrollment.api.LearnerEnrollment;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.MyEnrollment;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.RosterRow;
import com.saasresearcher.learning.enrollment.entity.CourseEnrollment;
import com.saasresearcher.learning.enrollment.repository.CourseEnrollmentRepository;
import com.saasresearcher.learning.enrollment.repository.CourseInvitationRepository;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentAccessServiceImpl implements EnrollmentAccessService {

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInvitationRepository courseInvitationRepository;
    private final CourseOwnershipService courseOwnershipService;
    private final IdentityAccess identityAccess;
    private final AuditService auditService;
    private final Clock clock;
    private final RowLocks locks;

    @Override
    @Transactional(readOnly = true)
    public EnrollmentView requireActive(UUID enrollmentId, Actor actor) {
        return requireUsable(getByEnrollmentId(enrollmentId, actor));
    }

    @Override
    @Transactional
    public EnrollmentView lockActive(UUID enrollmentId, Actor actor) {
        CourseEnrollment enrollment = getByEnrollmentId(enrollmentId, actor);
        AccountSummary learner = identityAccess.lockShared(List.of(actor.userId())).get(actor.userId());
        if (learner == null || !learner.isActive()) {
            throw ApiException.accessDenied("This account is suspended");
        }
        courseOwnershipService.lockShared(enrollment.getCourseId());
        return requireUsable(locks.forUpdate(enrollment));
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentView require(UUID enrollmentId) {
        return courseEnrollmentRepository.findById(enrollmentId).map(CourseEnrollment::toView).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional
    public EnrollmentView lock(UUID enrollmentId) {
        return courseEnrollmentRepository.findById(enrollmentId).map(locks::forUpdate).map(CourseEnrollment::toView).orElseThrow(ApiException::notFound);
    }

    @Override
    @Transactional(readOnly = true)
    public LearnerEnrollment requireOwn(UUID enrollmentId, Actor actor) {
        return learnerView(getByEnrollmentId(enrollmentId, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearnerEnrollment> enrollmentsOf(UUID learnerId) {
        return courseEnrollmentRepository.findByLearnerIdOrderByGrantedAtDesc(learnerId).stream().map(EnrollmentAccessServiceImpl::learnerView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentView> find(UUID courseId, UUID learnerId) {
        return courseEnrollmentRepository.findByCourseIdAndLearnerId(courseId, learnerId).map(CourseEnrollment::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLearningLocked(UUID enrollmentId) {
        return courseEnrollmentRepository.findById(enrollmentId).map(CourseEnrollment::isLearningLocked).orElse(false);
    }

    @Override
    @Transactional
    public void lockLearning(UUID enrollmentId) {
        courseEnrollmentRepository.findById(enrollmentId).orElseThrow(ApiException::notFound).lockLearning();
    }

    /** Includes historical enrollments, so learners keep their record after withdrawal or suspension. */
    @Transactional(readOnly = true)
    public List<MyEnrollment> mine(Actor actor) {
        return courseEnrollmentRepository.findByLearnerIdOrderByGrantedAtDesc(actor.userId()).stream()
                .map(enrollment -> new MyEnrollment(enrollment.getId(), enrollment.getCourseId(), courseOwnershipService.requireOwner(enrollment.getCourseId()).title(),
                        enrollment.getCourseVersionId(), enrollment.getStatus(), enrollment.getGrantedAt(), enrollment.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RosterRow> roster(UUID courseId, EnrollmentStatus status, PageQuery query, Actor actor) {
        courseOwnershipService.requireAuthor(courseId, actor);
        Sort order = Sort.by("grantedAt", "id");
        Page<CourseEnrollment> page = status == null ? courseEnrollmentRepository.findByCourseId(courseId, query.pageable(order))
                : courseEnrollmentRepository.findByCourseIdAndStatus(courseId, status, query.pageable(order));
        Map<UUID, AccountSummary> learners = identityAccess.summaries(page.map(CourseEnrollment::getLearnerId).toList());
        return PageResponse.of(page, enrollment -> RosterRow.of(enrollment, learners.get(enrollment.getLearnerId())));
    }

    /**
     * The author suspends an active enrollment or restores a suspended one. Suspension also revokes the learner's pending
     * invitations to the course, so no stale invitation can quietly undo it. Withdrawal belongs to the learner.
     */
    @Transactional
    public RosterRow changeStatus(UUID enrollmentId, EnrollmentStatus status, long expectedRevision, Actor actor) {
        CourseEnrollment unlocked = courseEnrollmentRepository.findById(enrollmentId).orElseThrow(ApiException::notFound);
        courseOwnershipService.requirePublishAuthor(unlocked.getCourseId(), actor);
        Map<UUID, AccountSummary> accounts = identityAccess.lockShared(List.of(actor.userId(), unlocked.getLearnerId()));
        if (!accounts.get(actor.userId()).isActiveApprovedTeacher()) {
            throw ApiException.teacherApprovalRequired();
        }
        courseOwnershipService.lock(unlocked.getCourseId());
        CourseEnrollment enrollment = locks.forUpdate(unlocked);
        Revisions.require(expectedRevision, enrollment.getVersion());
        boolean allowed = (status == EnrollmentStatus.SUSPENDED && enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                || (status == EnrollmentStatus.ACTIVE && enrollment.getStatus() == EnrollmentStatus.SUSPENDED);
        if (!allowed) {
            throw ApiException.conflict(EnrollmentErrors.INVALID_STATUS_CHANGE, "Authors can suspend active enrollments and restore suspended ones");
        }
        Instant now = clock.instant();
        enrollment.changeStatus(status, now);
        if (status == EnrollmentStatus.SUSPENDED) {
            courseInvitationRepository.lockByCourseAndEmail(enrollment.getCourseId(), accounts.get(enrollment.getLearnerId()).email(), InvitationStatus.PENDING)
                    .forEach(invitation -> invitation.revoke(now));
        }
        courseEnrollmentRepository.flush();
        auditService.record(AuditEntry.success(actor.userId(), "enrollment.status-changed", "enrollment", enrollmentId, Map.of("status", status)));
        return RosterRow.of(enrollment, accounts.get(enrollment.getLearnerId()));
    }

    /** The learner leaves the course; results and progress stay readable to them. */
    @Transactional
    public LearnerEnrollment withdraw(UUID enrollmentId, Actor actor) {
        lockActive(enrollmentId, actor);
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(enrollmentId).orElseThrow();
        enrollment.changeStatus(EnrollmentStatus.WITHDRAWN, clock.instant());
        courseEnrollmentRepository.flush();
        auditService.record(AuditEntry.success(actor.userId(), "enrollment.withdrawn", "enrollment", enrollmentId));
        return learnerView(enrollment);
    }

    private CourseEnrollment getByEnrollmentId(UUID enrollmentId, Actor actor) {
        return courseEnrollmentRepository.findById(enrollmentId)
                .filter(enrollment -> enrollment.getLearnerId().equals(actor.userId()))
                .orElseThrow(ApiException::notFound);
    }

    private static EnrollmentView requireUsable(CourseEnrollment enrollment) {
        if (!enrollment.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, EnrollmentErrors.ENROLLMENT_INACTIVE, "This enrollment is not active");
        }
        return enrollment.toView();
    }

    private static LearnerEnrollment learnerView(CourseEnrollment enrollment) {
        return new LearnerEnrollment(enrollment.toView(), enrollment.getGrantedAt(), enrollment.getStatusChangedAt(), enrollment.isActive());
    }
}
