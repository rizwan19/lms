package com.saasresearcher.learning.enrollment.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseOwner;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import com.saasresearcher.learning.enrollment.api.GrantSource;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.AccessRequestView;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.Decision;
import com.saasresearcher.learning.enrollment.entity.AccessEvent;
import com.saasresearcher.learning.enrollment.entity.AccessRequest;
import com.saasresearcher.learning.enrollment.entity.CourseEnrollment;
import com.saasresearcher.learning.enrollment.repository.AccessEventRepository;
import com.saasresearcher.learning.enrollment.repository.AccessRequestRepository;
import com.saasresearcher.learning.enrollment.repository.CourseEnrollmentRepository;
import com.saasresearcher.learning.enrollment.service.AccessGrants.GrantContext;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.identity.api.RateLimits;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access requests: PENDING → APPROVED | REJECTED | CANCELLED, one current row per course and learner plus an event
 * history. Requesting is never access; only the course author's approval grants an enrollment, in the same transaction.
 */
@Service
public class CourseAccessRequestService {

    static final Duration REJECTION_COOLDOWN = Duration.ofHours(24);
    private static final int REQUESTS_PER_WINDOW = 20;

    private final AccessRequestRepository requests;
    private final AccessEventRepository events;
    private final CourseEnrollmentRepository enrollments;
    private final AccessGrants grants;
    private final CourseOwnershipService ownership;
    private final CourseCatalogService catalog;
    private final IdentityAccess identity;
    private final RateLimits rateLimits;
    private final Notifications notifications;
    private final AuditService audit;
    private final Clock clock;
    private final RowLocks locks;

    CourseAccessRequestService(AccessRequestRepository requests, AccessEventRepository events, CourseEnrollmentRepository enrollments,
                               AccessGrants grants, CourseOwnershipService ownership, CourseCatalogService catalog, IdentityAccess identity, RateLimits rateLimits,
                               Notifications notifications, AuditService audit, RowLocks locks, Clock clock) {
        this.requests = requests;
        this.events = events;
        this.enrollments = enrollments;
        this.grants = grants;
        this.ownership = ownership;
        this.catalog = catalog;
        this.identity = identity;
        this.rateLimits = rateLimits;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
        this.locks = locks;
    }

    /** A repeated request while one is pending returns it; a rejected one may be repeated after 24 hours. */
    @Transactional
    public AccessRequestView request(UUID courseId, Actor actor) {
        actor.requireVerified();
        rateLimits.acquire("access-request:" + actor.userId(), REQUESTS_PER_WINDOW);
        identity.lockShared(List.of(actor.userId()));
        CourseOwner course = ownership.lock(courseId);
        if (course.isArchived() || catalog.latestPublished(courseId).isEmpty()) {
            throw ApiException.notFound();
        }
        if (course.authorId().equals(actor.userId())) {
            throw ApiException.conflict(EnrollmentErrors.OWN_COURSE, "Authors cannot request access to their own course");
        }
        if (enrollments.findByCourseIdAndLearnerId(courseId, actor.userId()).filter(CourseEnrollment::isActive).isPresent()) {
            throw ApiException.conflict(EnrollmentErrors.ALREADY_ENROLLED, "You are already enrolled in this course");
        }
        Instant now = clock.instant();
        Optional<AccessRequest> existing = requests.lockByCourseAndLearner(courseId, actor.userId());
        if (existing.isPresent() && existing.get().isPending()) {
            return view(existing.get(), course, null);
        }
        AccessRequest request;
        if (existing.isPresent()) {
            request = existing.get();
            if (request.getStatus() == AccessRequestStatus.REJECTED && request.getDecidedAt().plus(REJECTION_COOLDOWN).isAfter(now)) {
                throw ApiException.conflict(ErrorCodes.ACCESS_REQUEST_COOLDOWN, "You can request access again 24 hours after a rejection")
                        .with("retryAfter", request.getDecidedAt().plus(REJECTION_COOLDOWN));
            }
            AccessRequestStatus previous = request.getStatus();
            request.reopen(now);
            events.save(new AccessEvent(request.getId(), actor.userId(), previous, AccessRequestStatus.PENDING, null, now));
        } else {
            request = requests.save(new AccessRequest(courseId, actor.userId(), now));
            events.save(new AccessEvent(request.getId(), actor.userId(), null, AccessRequestStatus.PENDING, null, now));
        }
        requests.flush();
        notifications.notify(new NotificationRequest(course.authorId(), "access-request.created", "New access request for " + course.title(),
                "", "/courses/" + courseId + "/access-requests", "access-request:" + request.getId() + ":" + now.toEpochMilli()));
        audit.record(AuditEntry.success(actor.userId(), "access-request.created", "course", courseId));
        return view(request, course, null);
    }

    @Transactional
    public AccessRequestView cancel(UUID requestId, Actor actor) {
        AccessRequest unlocked = requests.findById(requestId).filter(request -> request.getLearnerId().equals(actor.userId()))
                .orElseThrow(ApiException::notFound);
        identity.lockShared(List.of(actor.userId()));
        CourseOwner course = ownership.lock(unlocked.getCourseId());
        AccessRequest request = locks.forUpdate(unlocked);
        if (!request.isPending()) {
            throw ApiException.conflict(EnrollmentErrors.REQUEST_NOT_PENDING, "Only pending requests can be cancelled");
        }
        Instant now = clock.instant();
        request.cancel(actor.userId(), now);
        events.save(new AccessEvent(request.getId(), actor.userId(), AccessRequestStatus.PENDING, AccessRequestStatus.CANCELLED, null, now));
        requests.flush();
        return view(request, course, null);
    }

    /** Author-only. Approval runs the grant transaction; a duplicate identical decision returns the existing result. */
    @Transactional
    public AccessRequestView decide(UUID requestId, Decision decision, Actor actor) {
        AccessRequest unlocked = requests.findById(requestId).orElseThrow(ApiException::notFound);
        ownership.requirePublishAuthor(unlocked.getCourseId(), actor);
        GrantContext context = grants.lock(unlocked.getCourseId(), actor.userId(), unlocked.getLearnerId(), ErrorCodes.TEACHER_APPROVAL_REQUIRED);
        AccessRequest request = locks.forUpdate(unlocked);
        if (!request.isPending()) {
            boolean same = actor.userId().equals(request.getDecidedBy()) && (request.getStatus() == AccessRequestStatus.APPROVED) == decision.approve();
            if (same) {
                return view(request, context.course(), context.learner());
            }
            throw ApiException.conflict(EnrollmentErrors.REQUEST_ALREADY_DECIDED, "This request has already been resolved");
        }
        Revisions.require(decision.expectedRevision(), request.getVersion());
        Instant now = clock.instant();
        String reason = reason(decision.reason());
        if (decision.approve()) {
            CourseEnrollment enrollment = grants.grant(context, GrantSource.REQUEST);
            request.approve(actor.userId(), enrollment.getId(), now);
        } else {
            request.reject(actor.userId(), reason, now);
        }
        events.save(new AccessEvent(request.getId(), actor.userId(), AccessRequestStatus.PENDING, request.getStatus(), reason, now));
        requests.flush();
        notifications.notify(new NotificationRequest(request.getLearnerId(), "access-request.decided",
                (decision.approve() ? "Access approved: " : "Access request declined: ") + context.course().title(), reason == null ? "" : reason,
                "/me/access-requests", "access-request-decided:" + request.getId() + ":" + now.toEpochMilli()));
        audit.record(AuditEntry.success(actor.userId(), decision.approve() ? "access-request.approved" : "access-request.rejected",
                "access-request", request.getId()));
        return view(request, context.course(), context.learner());
    }

    @Transactional(readOnly = true)
    public List<AccessRequestView> mine(Actor actor) {
        return requests.findByLearnerIdOrderByRequestedAtDesc(actor.userId()).stream()
                .map(request -> view(request, ownership.requireOwner(request.getCourseId()), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessRequestView> inbox(UUID courseId, AccessRequestStatus status, PageQuery query, Actor actor) {
        CourseOwner course = ownership.requireAuthor(courseId, actor);
        Page<AccessRequest> page = requests.findByCourseIdAndStatus(courseId, status, query.pageable(Sort.by("requestedAt", "id")));
        Map<UUID, AccountSummary> learners = identity.summaries(page.map(AccessRequest::getLearnerId).toList());
        return PageResponse.of(page, request -> view(request, course, learners.get(request.getLearnerId())));
    }

    private static AccessRequestView view(AccessRequest request, CourseOwner course, AccountSummary learner) {
        Instant retryAfter = request.getStatus() == AccessRequestStatus.REJECTED ? request.getDecidedAt().plus(REJECTION_COOLDOWN) : null;
        return new AccessRequestView(request.getId(), request.getCourseId(), course.title(), request.getLearnerId(),
                learner == null ? null : learner.displayName(), learner == null ? null : learner.username(), request.getStatus(), request.getReason(),
                request.getRequestedAt(), request.getDecidedAt(), retryAfter, request.getVersion());
    }

    private static String reason(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > 1000) {
            throw ApiException.invalid("reason", "At most 1000 characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
