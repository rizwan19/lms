package com.saasresearcher.learning.enrollment.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.courses.api.CourseOwner;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import com.saasresearcher.learning.enrollment.api.GrantSource;
import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.AcceptedInvitation;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.BulkInvitationResult;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.InvitationView;
import com.saasresearcher.learning.enrollment.entity.AccessEvent;
import com.saasresearcher.learning.enrollment.entity.AccessRequest;
import com.saasresearcher.learning.enrollment.entity.CourseEnrollment;
import com.saasresearcher.learning.enrollment.entity.CourseInvitation;
import com.saasresearcher.learning.enrollment.infra.CourseInvitationEmailHandler;
import com.saasresearcher.learning.enrollment.repository.AccessEventRepository;
import com.saasresearcher.learning.enrollment.repository.AccessRequestRepository;
import com.saasresearcher.learning.enrollment.repository.CourseInvitationRepository;
import com.saasresearcher.learning.enrollment.service.AccessGrants.GrantContext;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.identity.api.RateLimits;
import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Author invitations: a pending, tokenized invitation per course and normalized email, delivered asynchronously. The
 * verified account with the matching email accepts explicitly, which enrolls it without another approval. Opening a
 * link never changes anything; only the accept request does.
 */
@Service
public class CourseInvitationService {

    static final Duration TTL = Duration.ofDays(7);
    public static final int MAX_BULK = 100;
    private static final int INVITE_CALLS_PER_WINDOW = 30;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CourseInvitationRepository invitations;
    private final AccessRequestRepository requests;
    private final AccessEventRepository events;
    private final AccessGrants grants;
    private final CourseOwnershipService ownership;
    private final IdentityAccess identity;
    private final RateLimits rateLimits;
    private final EmailSender email;
    private final DurableJobs jobs;
    private final Notifications notifications;
    private final AuditService audit;
    private final Clock clock;
    private final RowLocks locks;

    CourseInvitationService(CourseInvitationRepository invitations, AccessRequestRepository requests, AccessEventRepository events,
                            AccessGrants grants, CourseOwnershipService ownership, IdentityAccess identity, RateLimits rateLimits, EmailSender email, DurableJobs jobs,
                            Notifications notifications, AuditService audit, RowLocks locks, Clock clock) {
        this.invitations = invitations;
        this.requests = requests;
        this.events = events;
        this.grants = grants;
        this.ownership = ownership;
        this.identity = identity;
        this.rateLimits = rateLimits;
        this.email = email;
        this.jobs = jobs;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
        this.locks = locks;
    }

    @Transactional
    public InvitationView invite(UUID courseId, String recipient, Actor actor) {
        return inviteAll(courseId, List.of(normalize("email", recipient)), actor).getFirst();
    }

    /** Up to 100 unique normalized addresses, validated together and created in one transaction. */
    @Transactional
    public BulkInvitationResult inviteBulk(UUID courseId, List<String> recipients, Actor actor) {
        if (recipients == null || recipients.isEmpty()) {
            throw ApiException.invalid("emails", "Add at least one email address");
        }
        Set<String> unique = new LinkedHashSet<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (int i = 0; i < recipients.size(); i++) {
            try {
                unique.add(normalize("emails[" + i + "]", recipients.get(i)));
            } catch (ApiException invalid) {
                errors.putAll(invalid.fieldErrors());
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
        if (unique.size() > MAX_BULK) {
            throw ApiException.invalid("emails", "Invite at most " + MAX_BULK + " different addresses at a time");
        }
        List<InvitationView> created = inviteAll(courseId, List.copyOf(unique), actor);
        return new BulkInvitationResult(created.size(), created);
    }

    private List<InvitationView> inviteAll(UUID courseId, List<String> recipients, Actor actor) {
        ownership.requirePublishAuthor(courseId, actor);
        if (!email.isConfigured()) {
            throw ApiException.conflict("EMAIL_NOT_CONFIGURED", "Email delivery is not configured; invitations cannot be sent");
        }
        rateLimits.acquire("course-invite:" + actor.userId(), INVITE_CALLS_PER_WINDOW);
        AccountSummary author = identity.lockShared(List.of(actor.userId())).get(actor.userId());
        if (author == null || !author.isActiveApprovedTeacher()) {
            throw ApiException.teacherApprovalRequired();
        }
        CourseOwner course = ownership.lock(courseId);
        if (course.isArchived()) {
            throw ApiException.conflict(EnrollmentErrors.COURSE_UNAVAILABLE, "Archived courses accept no new learners");
        }
        if (recipients.contains(author.email())) {
            throw ApiException.conflict(EnrollmentErrors.OWN_COURSE, "Authors cannot invite themselves; use the author preview");
        }
        Instant now = clock.instant();
        List<InvitationView> created = new ArrayList<>();
        for (String recipient : recipients) {
            invitations.lockByCourseAndEmail(courseId, recipient, InvitationStatus.PENDING).forEach(previous -> previous.revoke(now));
            invitations.flush();
            CourseInvitation invitation = invitations.saveAndFlush(new CourseInvitation(courseId, actor.userId(), recipient,
                    InvitationTokens.sha256(InvitationTokens.newToken()), now.plus(TTL), now));
            jobs.enqueue(JobRequest.now(CourseInvitationEmailHandler.TYPE, invitation.getId(), "course-invitation-email:" + invitation.getId(), now));
            created.add(InvitationView.of(invitation));
        }
        audit.record(AuditEntry.success(actor.userId(), "course-invitation.created", "course", courseId, Map.of("recipients", recipients.size())));
        return created;
    }

    @Transactional
    public InvitationView revoke(UUID invitationId, Actor actor) {
        CourseInvitation unlocked = invitations.findById(invitationId).orElseThrow(ApiException::notFound);
        ownership.requireAuthor(unlocked.getCourseId(), actor);
        ownership.lock(unlocked.getCourseId());
        CourseInvitation invitation = locks.forUpdate(unlocked);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.conflict(EnrollmentErrors.INVITATION_NOT_PENDING, "Only pending invitations can be revoked");
        }
        invitation.revoke(clock.instant());
        invitations.flush();
        audit.record(AuditEntry.success(actor.userId(), "course-invitation.revoked", "course-invitation", invitationId));
        return InvitationView.of(invitation);
    }

    /**
     * Explicit acceptance by the verified account whose email matches. Rechecks the author, course and edition under the
     * grant locks; a failed check leaves the token unused. An active enrollment makes acceptance a harmless success,
     * and an invitation older than the learner's suspension or withdrawal cannot quietly undo it.
     */
    @Transactional
    public AcceptedInvitation accept(String rawToken, Actor actor) {
        actor.requireVerified();
        CourseInvitation unlocked = invitations.findByTokenHash(InvitationTokens.sha256(rawToken == null ? "" : rawToken))
                .orElseThrow(CourseInvitationService::invalid);
        GrantContext context = grants.lock(unlocked.getCourseId(), unlocked.getInvitedBy(), actor.userId(), EnrollmentErrors.INVITATION_UNAVAILABLE);
        CourseInvitation invitation = locks.forUpdate(unlocked);
        Instant now = clock.instant();
        if (!invitation.isUsable(now)) {
            throw invalid();
        }
        if (!invitation.getRecipientEmail().equals(context.learner().email())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.INVITATION_EMAIL_MISMATCH,
                    "This invitation was sent to a different email address");
        }
        CourseEnrollment existing = context.existing().orElse(null);
        if (existing != null && !existing.isActive() && !invitation.getCreatedAt().isAfter(existing.getStatusChangedAt())) {
            throw ApiException.conflict(EnrollmentErrors.INVITATION_STALE, "This invitation predates a change to your enrollment; ask for a new one");
        }
        CourseEnrollment enrollment = grants.grant(context, GrantSource.INVITATION);
        invitation.accept(actor.userId(), enrollment.getId(), now);
        requests.lockByCourseAndLearner(invitation.getCourseId(), actor.userId()).filter(AccessRequest::isPending).ifPresent(request -> {
            request.approve(invitation.getInvitedBy(), enrollment.getId(), now);
            events.save(new AccessEvent(request.getId(), invitation.getInvitedBy(), AccessRequestStatus.PENDING, AccessRequestStatus.APPROVED,
                    "Invitation accepted", now));
        });
        invitations.flush();
        notifications.notify(new NotificationRequest(invitation.getInvitedBy(), "course-invitation.accepted",
                context.learner().displayName() + " joined " + context.course().title(), "", "/courses/" + invitation.getCourseId() + "/enrollments",
                "course-invitation-accepted:" + invitation.getId()));
        audit.record(AuditEntry.success(actor.userId(), "course-invitation.accepted", "course-invitation", invitation.getId(),
                Map.of("enrollmentId", enrollment.getId())));
        return new AcceptedInvitation(enrollment.getId(), invitation.getCourseId());
    }

    @Transactional(readOnly = true)
    public PageResponse<InvitationView> list(UUID courseId, InvitationStatus status, PageQuery query, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        Sort order = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        Page<CourseInvitation> page = status == null ? invitations.findByCourseId(courseId, query.pageable(order))
                : invitations.findByCourseIdAndStatus(courseId, status, query.pageable(order));
        return PageResponse.of(page, InvitationView::of);
    }

    private static String normalize(String field, String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw ApiException.invalid(field, "Enter a valid email address");
        }
        return normalized;
    }

    private static ApiException invalid() {
        return ApiException.badRequest(EnrollmentErrors.INVITATION_INVALID, "This invitation is invalid or has expired");
    }
}
