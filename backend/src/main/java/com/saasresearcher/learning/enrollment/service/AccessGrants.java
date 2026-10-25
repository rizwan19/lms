package com.saasresearcher.learning.enrollment.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseOwner;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.api.GrantPolicy;
import com.saasresearcher.learning.enrollment.api.GrantSource;
import com.saasresearcher.learning.enrollment.entity.CourseEnrollment;
import com.saasresearcher.learning.enrollment.repository.CourseEnrollmentRepository;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The shared grant transaction of requests and invitations. Lock order: author and learner accounts {@code FOR SHARE}
 * in UUID order, the course {@code FOR UPDATE}, then the enrollment row, then the request or invitation. Every
 * decision input is re-read after locking, so concurrent approval and acceptance produce one enrollment.
 */
@Component
public class AccessGrants {

    private final CourseEnrollmentRepository enrollments;
    private final IdentityAccess identity;
    private final CourseOwnershipService ownership;
    private final CourseCatalogService catalog;
    private final ObjectProvider<GrantPolicy> policies;
    private final Notifications notifications;
    private final AuditService audit;
    private final Clock clock;

    AccessGrants(CourseEnrollmentRepository enrollments, IdentityAccess identity, CourseOwnershipService ownership, CourseCatalogService catalog,
                 ObjectProvider<GrantPolicy> policies, Notifications notifications, AuditService audit, Clock clock) {
        this.enrollments = enrollments;
        this.identity = identity;
        this.ownership = ownership;
        this.catalog = catalog;
        this.policies = policies;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    /** Locked, revalidated inputs of one grant. {@code existing} is the learner's enrollment row, already locked. */
    record GrantContext(CourseOwner course, AccountSummary author, AccountSummary learner, Optional<CourseEnrollment> existing) {
    }

    /**
     * Takes the grant locks and rechecks that the author is still the active approved author of an active published
     * course and that the learner is a different, active, verified account.
     */
    GrantContext lock(UUID courseId, UUID authorId, UUID learnerId, String authorProblemCode) {
        Map<UUID, AccountSummary> accounts = identity.lockShared(List.of(authorId, learnerId));
        CourseOwner course = ownership.lock(courseId);
        AccountSummary author = accounts.get(authorId);
        if (author == null || !author.isActiveApprovedTeacher() || !course.authorId().equals(authorId)) {
            throw ApiException.conflict(authorProblemCode, "The course author cannot grant access right now");
        }
        if (course.authorId().equals(learnerId)) {
            throw ApiException.conflict(EnrollmentErrors.OWN_COURSE, "Authors cannot enroll in their own course; use the author preview");
        }
        AccountSummary learner = accounts.get(learnerId);
        if (learner == null || !learner.isActive()) {
            throw ApiException.notFound();
        }
        if (!learner.emailVerified()) {
            throw ApiException.emailUnverified();
        }
        return new GrantContext(course, author, learner, enrollments.lockByCourseAndLearner(courseId, learnerId));
    }

    /**
     * Creates the enrollment on the latest published edition, returns the active one unchanged, or restores a suspended
     * or withdrawn one on its original edition. Callers decide beforehand whether a restore is a fresh grant.
     */
    CourseEnrollment grant(GrantContext context, GrantSource source) {
        Instant now = clock.instant();
        if (context.existing().isPresent()) {
            CourseEnrollment enrollment = context.existing().get();
            if (!enrollment.isActive()) {
                enrollment.changeStatus(EnrollmentStatus.ACTIVE, now);
                enrollments.flush();
                audit.record(AuditEntry.success(context.author().id(), "enrollment.restored", "enrollment", enrollment.getId(), Map.of("source", source)));
                notifyLearner(enrollment, context.course(), "restored:" + enrollment.getId() + ":" + now.toEpochMilli());
            }
            return enrollment;
        }
        if (context.course().isArchived()) {
            throw ApiException.conflict(EnrollmentErrors.COURSE_UNAVAILABLE, "This course is archived and accepts no new learners");
        }
        PublishedCourse edition = catalog.latestPublished(context.course().courseId())
                .orElseThrow(() -> ApiException.conflict(EnrollmentErrors.COURSE_UNAVAILABLE, "This course has no published edition"));
        policies.forEach(policy -> policy.blocker(edition.versionId()).ifPresent(blocker -> {
            throw new ApiException(HttpStatus.CONFLICT, blocker.code(), blocker.message());
        }));
        CourseEnrollment enrollment = enrollments.saveAndFlush(new CourseEnrollment(context.course().courseId(), edition.versionId(),
                context.learner().id(), source, context.author().id(), now));
        audit.record(AuditEntry.success(context.author().id(), "enrollment.granted", "enrollment", enrollment.getId(),
                Map.of("source", source, "edition", edition.edition())));
        notifyLearner(enrollment, context.course(), "enrolled:" + enrollment.getId());
        return enrollment;
    }

    private void notifyLearner(CourseEnrollment enrollment, CourseOwner course, String deduplicationKey) {
        notifications.notify(new NotificationRequest(enrollment.getLearnerId(), "enrollment.granted", "You can now study " + course.title(),
                "", "/me/enrollments/" + enrollment.getId(), deduplicationKey));
    }
}
