# Course-centric Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution, or superpowers:subagent-driven-development if explicitly selected. Track the checkbox steps and verify each task before proceeding.

**Goal:** Replace the implemented class-based backend with verified public signup, peer-approved teachers, author-approved direct course enrollment and course invitations, preserving learning/exams/grading/certificates.

**Architecture:** Refactor the existing Spring Boot modular monolith, keeping its Java 21/Gradle/PostgreSQL infrastructure. Replace class relationships with course-enrollment ownership. Workspace administration and course ownership are independent permission checks.

**Spec:** [Backend design](../specs/2026-10-23-course-centric-backend-design.md).  
**SQL:** [Complete database blueprint](../../database/2026-10-23-course-centric-schema.sql).  
**Target repository:** `/Users/rizwan/Documents/Projects/LMS`. All paths below are relative to that repository. These documents themselves reside in the separate SaaS planning workspace.

**Status:** Detailed plan for review; no backend/frontend changes, SQL execution, or database reset has occurred.

## Global constraints

- Backend only. Do not implement or adjust React components in this scope.
- Exactly two account roles: TEACHER and STUDENT. Teaching approval is account state, not a third role.
- Every self-registering account verifies email. Verified teachers may learn; only approved teachers administer/publish.
- First active approved peer decision resolves teacher review. No self-review.
- Approved teachers administer users/settings but only a course author manages its access/content/grades/private reports.
- Remove classes and class assignments entirely, including endpoints, DTOs, schema and active tests.
- Requests require author approval; author invitations preauthorize enrollment after matching verified-email acceptance.
- One enrollment per course/learner, pinned to a course edition; preserve learning history on suspension/restoration.
- Per-exam SELF_PACED or SCHEDULED mode, server deadlines, written teacher grading, separate result publication.
- No payment features, framework upgrades or multi-workspace tenancy.
- Follow actual LMS/AGENTS.md layer rules and injected Clock/MutableClock conventions.
- Preserve parameterized SQL, optimistic revisions, session/CSRF, durable jobs and ArchUnit checks.
- Clean v2 database is the deployment target; no automatic destructive startup or implicit old-data migration.

## Review focus

1. A pending teacher cannot gain admin privileges via TEACHER role checks: test every workspace and publication boundary (Tasks 2–5).
2. Teacher B administers accounts but cannot read/change Teacher A's private course or gradebook, including file/export/certificate backdoors (Tasks 5, 10–13).
3. Email verification, teacher decision and invitation retries/races create one transition and one enrollment (Tasks 3–4, 6–7).
4. Removing class_id does not break deadline snapshots, progress edition checks, grading history or certificate eligibility (Tasks 8–12).
5. Destructive reset cannot accidentally target an old production database, and the untouched frontend incompatibility is explicit (Tasks 1, 14–15).

## Repository findings

- Role/authority currently uses `common/Role.java`, `common/Actor.java` and many `isAdministrator()` branches.
- Signup is currently staff-provisioned: `identity/service/SetupService.java`, `AccountService.java`, `CredentialIssuer.java`; setup immediately creates an administrator.
- `courses/service/CourseAccess.java` currently grants administrator bypass and supports course_teachers.
- `classrooms/api/EnrollmentAccess.java` and `TeachingAccess.java` are used by learning, assessments, grading, completion, certificates, reporting and media.
- `assessments/service/ExamAssignmentService.java` reacts to class lifecycle; it must become edition-based scheduling rather than a renamed hidden class.
- Existing token email handlers rotate tokens before sending; preserve and test that behavior for verification and course invitations.
- Existing `notifications/api/Notifications.java` and NotificationRequest support transactional deduplicated notices.
- Existing SQL is V001–V013 under `backend/src/main/resources/db/migration`. Keep those files unchanged/outside v2 location; a new baseline is deliberate.
- Actual build uses `backend/gradlew`, not the Maven commands in the original preliminary SaaS plan.

## Scope/file map

For readability `B` = `backend/src/main/java/com/saasresearcher/learning`, `T` = `backend/src/test/java/com/saasresearcher/learning`, `R` = `backend/src/main/resources`. Every path using these prefixes expands literally. Keep existing module layer packages; do not add `internal` packages.

| Area | Create/change/remove |
| --- | --- |
| Identity | Change B/common/{Role,Actor}.java; B/identity/{entity/UserAccount,service/AccountAuthorization,service/SetupService,service/AccountService,config/ActiveAccountFilter,config/SecurityConfiguration}.java. Add registration, verification and teacher-review services/controllers/repos/DTOs. |
| Workspace | Adapt B/institution services to workspace and new approved-teacher capability; remove admin role terminology. |
| Courses | Change B/courses/{service/CourseAccess,service/CourseAuthoringService,service/CoursePublicationService,repository/CourseRepository,repository/CourseContentRepository,controller/CourseController}.java; add public catalog DTO/query/controller. |
| Enrollment | New B/enrollment/{api,controller,service,repository,dto,entity,infra}; remove B/classrooms after consumers are refactored. |
| Learning | Refactor B/learning/service/ProgressService.java and its controller/repository contracts. |
| Exams | Replace ExamAssignmentService and class callbacks; update all attempt snapshots/read models to courseExamId/enrollmentId. |
| Grading/completion | Refactor GradingService, GradingQueue, CompletionReconciler and their owned schema/read models. |
| Certificates/reports/media | Refactor CertificateIssueService, CertificateRepository, ClassReports/ClassReportQueries and media access policies. |
| Operations | Adapt MaintenanceService, demo accounts, backup manifests, release fixtures and health/config docs. |
| Contracts | Update docs/api/openapi.yaml, AGENTS.md, README.md, HowToSetup.md; add docs/api/course-centric-breaking-changes.md and docs/runbooks/v2-database-reset.md. |

## Milestones

| Milestone | Tasks | Reviewable backend outcome |
| --- | --- | --- |
| A. Foundation and identity | 1–4 | Empty v2 schema, owner setup, verified signup, peer teacher review |
| B. Course access | 5–7 | Public catalog, author ownership, course requests and email invitations |
| C. Learning and assessment | 8–11 | Direct enrollment materials/progress, both exam modes, published grades |
| D. Completion and reporting | 12–13 | Certificates and course-author reports |
| E. Delivery qualification | 14–15 | Operational reset/recovery and backend end-to-end acceptance |

Tasks are dependent and should run sequentially. Establish a branch and baseline verification before editing. Do not commit documents or unrelated work blindly. Each task ends with a focused commit after relevant checks pass. Use meaningful failing tests before implementation, with expected behavior specified below. Existing tests will need legitimate adaptation because the product contract changes; do not simply delete failure coverage.

This is an incompatible schema/domain cutover, not a rolling migration. Keep existing deployed instances on the old release until Task 15; never point an intermediate branch at their database. During Tasks 1–13 use focused PostgreSQL/module contexts rather than claim the complete application is deployable. Task 1's schema test runs Flyway directly against empty PostgreSQL without loading legacy JPA mappings. Keep main code compiling: where shared contract edits affect later tasks, include the minimal caller/type updates and tests in the same commit, record them, and defer behavior only with explicit access-denied/unsupported results. Do not introduce a permissive legacy administrator alias. At Tasks 13 and 15 require the full application context and complete backend suite. Milestone reports distinguish tested module capabilities from full-application startup readiness.

## Contract and code examples

### Account state and capabilities

```java
public enum Role { STUDENT, TEACHER }
public enum TeacherStatus { PENDING, APPROVED, REJECTED }

// Built by IdentityAccess from current database state, never request JSON.
public record Actor(UUID userId, Role role, boolean emailVerified,
                    TeacherStatus teacherStatus) {
    public boolean isApprovedTeacher() {
        return emailVerified && role == Role.TEACHER
            && teacherStatus == TeacherStatus.APPROVED;
    }
}

public record RegisterCommand(String username, String displayName,
                              String email, String password, Role role) {}
public record RegistrationReceipt(UUID userId, String nextAction) {}
// nextAction = VERIFY_EMAIL; receipt carries no privileged session or token.
```

Active status is checked before Actor construction and inside protected mutation transactions. A student requires teacherStatus=null. A verified teacher not yet approved still has learner capability.

### Separate global and resource authorization

```java
// AccountAuthorization.java
public void requireApprovedTeacher(Actor actor) {
    if (!actor.isApprovedTeacher()) {
        throw ApiException.accessDenied("Approved teacher required");
    }
}

// CourseAccess.java -- there is deliberately no global admin bypass.
public void requireAuthor(UUID courseId, Actor actor) {
    identity.requireVerified(actor.userId());
    CourseOwner owner = courses.requireOwner(courseId);
    if (!owner.authorId().equals(actor.userId())) throw ApiException.notFound();
}
```

These are illustrative method bodies; use existing ApiException code conventions and inject repositories/services in owning layers. `requirePublishAuthor` composes current approved-teacher status with requireAuthor. Pending verified authors can edit only their own drafts; material reads and mutation policies distinguish draft ownership from workspace permissions.

### New module API contracts

```java
public interface CourseOwnership {
    CourseOwner requireOwner(UUID courseId); // authorId, courseId, course status
    void requireAuthor(UUID courseId, Actor actor);
    void requirePublishAuthor(UUID courseId, Actor actor);
}
public interface EnrollmentAccess {
    EnrollmentView requireActive(UUID enrollmentId, Actor actor);
    EnrollmentView lockActive(UUID enrollmentId, Actor actor);
    EnrollmentView require(UUID enrollmentId); // system lookup
    EnrollmentView lock(UUID enrollmentId);    // completion/grade lock
    LearnerEnrollment requireOwn(UUID enrollmentId, Actor actor);
    List<LearnerEnrollment> enrollmentsOf(UUID learnerId);
    boolean isLearningLocked(UUID enrollmentId);
    void lockLearning(UUID enrollmentId);
}
public record EnrollmentView(UUID id, UUID courseId, UUID courseVersionId,
                             UUID learnerId, EnrollmentStatus status) {}
public record AccessDecision(long expectedRevision, boolean approve, String reason) {}
public record CourseInvitationCommand(String email) {}
public record AcceptInvitationCommand(String token) {}
public enum ExamMode { SELF_PACED, SCHEDULED }
```

Place CourseOwnership/CourseOwner in courses/api and EnrollmentAccess and views in enrollment/api. Consumers do not call enrollment repositories. Course enrollment restore reuses the original ID/version/progress and attempt history.

### Access grant transaction

```text
Read request/invitation metadata without trusting its current state.
Acquire involved user locks in UUID order; acquire course lock.
Re-read/revalidate active approved author, verified learner, course and current edition.
Lock request/invitation and existing enrollment if present using consistent row order.
Reject expired/revoked token, wrong verified email, own course or closed required exam.
Insert enrollment if absent; otherwise return active enrollment or explicitly restore
only when this is a fresh author-authorized grant (never a stale revoked invitation).
Resolve request/invitation, record audit and enqueue notification in same transaction.
Commit; durable worker sends email later.
```

Use unique `(course_id,learner_id)` plus course lock; no “check then insert” without serialization. A bulk invite normalizes/deduplicates at most 100 recipient emails, validates the batch, and creates invitations transactionally; email delivery remains asynchronous per invitation. Return accepted count and per-recipient invitation IDs/status, not raw tokens.

### Exam deadline calculation

```java
static Instant deadline(Instant startedAt, int durationMinutes,
                        ExamMode mode, Instant closesAt) {
    Instant timed = startedAt.plus(Duration.ofMinutes(durationMinutes));
    return mode == ExamMode.SCHEDULED && closesAt.isBefore(timed)
        ? closesAt : timed;
}
```

Validate scheduled opens/closes before calling; self-paced excludes both. Timer checks use injected Clock, not browser time. An existing attempt's deadline does not change after schedule edits.

## Detailed tasks

### Task 1: Establish v2 schema, baseline and reset boundary

**Files:** Create R/db/coursecentric/V001__workspace_identity.sql through V006__operations.sql by splitting the blueprint in FK dependency order (identity; media/content; access/progress; exams/grades; completion/certificates; operations). Change R/application.yml Flyway location, test setup and B/operations/config/SchemaCompatibilityGuard.java. Add T/operations/CourseCentricSchemaIT.java and docs/runbooks/v2-database-reset.md. Keep old migrations unchanged in their old inactive location.

**Produces:** Reproducible fresh v2 database and legacy-schema refusal. Preserve existing dependency versions/Gradle task names. Blueprint singleton rows are inserted explicitly by their migration with fixed migration provenance timestamps.

- [ ] Write PostgreSQL tests asserting two role values only, no classes/user_roles/course_teachers, all blueprint FKs/checks/indexes and singleton setup row; legacy classes table triggers LEGACY_SCHEMA_UNSUPPORTED.
- [ ] Run `cd backend && ./gradlew integrationTest --tests '*CourseCentricSchemaIT'`; confirm new-contract failures before schema changes.
- [ ] Implement clean baseline and an ordering-safe guard before Flyway, checking configured schema rather than entire server. Give operator a create-fresh-database path. No startup DROP, clean or migration-history rewriting.
- [ ] Re-run schema tests and migration from a completely empty Testcontainers database. Confirm existing SQL is not auto-replayed into v2. Record schema decisions/deviations.
- [ ] Commit `feat: establish course-centric database baseline`.

### Task 2: Two-role identity and owner workspace setup

**Files:** Modify B/common/{Actor,Role}.java, identity/entity/UserAccount.java, identity/service/{SetupService,AccountAuthorization,AccountService}.java, identity/config/{ActiveAccountFilter,SecurityConfiguration}.java, institution service/repository/DTOs; add T/identity/WorkspaceSetupIT.java and T/identity/TwoRoleAuthorizationIT.java.

**Produces:** Current-state Actor, `IdentityAccess.requireVerified(UUID): Actor`, `requireApprovedTeacher(UUID): Actor`, workspace setup states and no administrator role. Existing modules that need later refactoring should receive explicit compiler-guided updates, not a misleading `isAdministrator` alias.

- [ ] Test setup secret, duplicate concurrent setup, pending owner verification, last approved teacher/self-suspension protection, stale-session capability refresh and pending teacher denied global operations.
- [ ] Run targeted setup/authorization tests and observe expected failing behavior.
- [ ] Implement row-locked bootstrap, status model and capability response; approved teachers may manage account metadata/status but not directly reset another user's chosen password or self-approve. Keep CSRF/session rotation.
- [ ] Run `./gradlew integrationTest --tests '*WorkspaceSetupIT' --tests '*TwoRoleAuthorizationIT'` from backend. Require passing cases for current active status, verified owner activation through a controlled verification fixture (real endpoint added in Task 3) and both roles.
- [ ] Commit `feat: replace administrator role with approved teacher capabilities`.

### Task 3: Public signup, email verification and recovery

**Files:** Add B/identity/controller/{RegistrationController,EmailVerificationController}.java, service/{RegistrationService,EmailVerificationService}.java, repository/IdentityTokenRepository.java, entity/IdentityToken.java, infra/VerificationEmailHandler.java; adapt CredentialIssuer, TokenEmailHandler, RecoveryController/LoginService; T/identity/PublicRegistrationIT.java.

**Consumes:** Identity state/workspace setup and DurableJobs. **Produces:** register, verify, resend, limited preverification sessions and verified-email recovery. Role selected at signup cannot be changed through profile updates.

- [ ] Test normalized unique emails, both role registrations, duplicate signup race, token purpose/expiry/reuse, resend invalidating old token, SMTP-not-configured rejection, unverified access denials and owner verification activating workspace exactly once.
- [ ] Run `./gradlew integrationTest --tests '*PublicRegistrationIT'` from backend; verify missing flows fail.
- [ ] Implement hash-only token rotation and atomic consumption, mandatory email, rate limits and generic public responses. No temporary-password creation or email-optional public accounts. Register verified teacher application through an identity-owned event handler in Task 4.
- [ ] Re-run tests plus AccountSecurityIT adapted to new contract; inspect response JSON/log capture for raw token leakage. Test retry after SMTP failure without marking verified.
- [ ] Commit `feat: add verified public registration`.

### Task 4: Teacher application queue and first-review decision

**Files:** Add B/identity/controller/TeacherApplicationController.java, service/TeacherApplicationService.java, repository/TeacherApplicationRepository.java, dto/TeacherApplicationViews.java, infra/TeacherApplicationNotificationHandler.java; T/identity/TeacherApprovalIT.java.

**Interfaces:** `submit(Actor): TeacherApplicationView`; `decide(UUID,AccessDecision,Actor): TeacherApplicationView`; pending teacher self-status and approved-teacher review list.

- [ ] Test verified-only queue entry, one pending application, no self-review, unapproved reviewer denied, approve/reject race, duplicate same decision, 24-hour rejection resubmission cooldown and suspension racing the last approved peer.
- [ ] Run TeacherApprovalIT and observe the new queue/decision assertions fail.
- [ ] Implement workspace → sorted affected-user → application lock order, authoritative teacher status, transactional fan-out job and applicant result notices. Notify active approved teachers; later inbox queries always show current pending applications even if a teacher missed fan-out.
- [ ] Re-run `./gradlew integrationTest --tests '*TeacherApprovalIT'`; two concurrent peer decisions must produce exactly one state transition and one resolution notice.
- [ ] Commit `feat: add peer approval of teacher accounts`.

### Task 5: Single-author courses and safe public catalog

**Files:** Modify CourseAccess, CourseAuthoringService, CoursePublicationService, CourseRepository, CourseContentRepository and CourseController; add B/courses/api/CourseOwnership.java, dto/CatalogViews.java, controller/CatalogController.java; T/courses/CourseOwnershipIT.java, CatalogPrivacyIT.java.

**Produces:** Ownership API from code examples; public catalog search/filter/pagination and immutable course editions. No course_teachers grant or privileged cross-author bypass remains.

- [ ] Test pending verified teacher draft creation vs denied publication; approved author publishing; student denied authoring; Teacher B denied A's edits/previews/private assets; catalog DTO excludes protected fields; archived/unpublished courses absent.
- [ ] Run targeted ownership/catalog tests to establish failures.
- [ ] Implement author_id and publish capability gates. Preserve content sanitization, private assets and immutable editions. Latest catalog metadata comes from a published edition rather than leaking draft edits through courses.title/summary.
- [ ] Re-run course tests and ArchUnit; assert public JSON recursively lacks lesson bodies/video URLs/learner identities/answer keys. Test catalog authorization separately from private course endpoints.
- [ ] Commit `feat: add author-owned courses and public discovery`.

### Task 6: Course requests and direct enrollment module

**Files:** Add B/enrollment/api/{EnrollmentAccess,EnrollmentView,LearnerEnrollment,EnrollmentStatus}.java; service/{CourseAccessRequestService,CourseEnrollmentService}.java; repository/{CourseEnrollmentRepository,AccessRequestRepository}.java; controller/{CourseAccessRequestController,CourseEnrollmentController}.java; T/enrollment/CourseAccessRequestIT.java.

**Interfaces:** request(UUID courseId,Actor), decide(UUID requestId,AccessDecision,Actor), cancel(UUID,Actor), and EnrollmentAccess from examples. All return explicit DTOs.

- [ ] Test verified learner requests, teacher-as-learner, own-course rejection, one pending request, author-only decisions, 24-hour rejected cooldown, no material access before approval, course-edition consistency and preserved state on explicit restored grant.
- [ ] Run CourseAccessRequestIT; new enrollment should be unavailable until implemented.
- [ ] Implement request state/history and grant transaction with closed required exam gate, author status recheck and deduplicated notices. Learner withdrawal and author suspension are distinct endpoints/actions. Historical results remain own-readable.
- [ ] Re-run PostgreSQL races for duplicate approvals and two learners; assert no duplicate enrollment and no teacher-admin override on another author's access request.
- [ ] Commit `feat: replace class enrollment with author-approved course access`.

### Task 7: Course email invitations and bounded bulk invite

**Files:** Add B/enrollment/service/CourseInvitationService.java, repository/CourseInvitationRepository.java, controller/CourseInvitationController.java, infra/CourseInvitationEmailHandler.java; T/enrollment/CourseInvitationIT.java.

**Interfaces:** invite(UUID courseId,CourseInvitationCommand,Actor), inviteBulk(UUID,List<String>,Actor), accept(AcceptInvitationCommand,Actor), revoke(UUID,Actor). Bulk maximum 100 unique normalized emails; no account creation by the inviter.

- [ ] Test existing/new account acceptance after verification, wrong email, seven-day expiry, revoked/replaced invite, author suspended before acceptance, invite/request concurrent grant, duplicate acceptance and withdrawn grant not resurrected by stale invite.
- [ ] Run CourseInvitationIT; expect flow/ownership/token assertions to fail.
- [ ] Implement token purpose boundary, hash-only email delivery, matching verified recipient and author-authorized enrollment. Pending requests resolve approved when invite accepts. Do not consume enrollment tokens from GET/link-preview requests.
- [ ] Re-run tests including bulk normalization/deduplication, 101 recipients rejection, SMTP retry and rollback. One enrollment survives concurrent request approval/invite acceptance.
- [ ] Commit `feat: add verified course invitation acceptance`.

### Task 8: Refactor learning and media to direct course access

**Files:** Modify B/learning/service/ProgressService.java and repositories/controllers; replace B/classrooms/service/ClassAssetPolicy.java with B/enrollment/service/CourseEnrollmentAssetPolicy.java; modify MediaAccessService/UploadService; T/learning/CourseProgressIT.java, T/media/CourseMediaAuthorizationIT.java.

**Consumes:** EnrollmentAccess, CourseOwnership, published edition read API. **Produces:** own-learning enrollment views and unchanged student-safe progress behavior with no class ID.

- [ ] Test teacher learner access, course edition matching, suspended enrollment, workspace teacher denied unrelated assets, draft uploader limits, last activity, monotonic resume and stale completion saves.
- [ ] Run targeted progress/media tests and observe old class-based behavior fail under direct enrollment fixtures.
- [ ] Refactor enrollment ID lookups, remove admin media bypass, retain range/S3 and completion locks. Workspace branding may be globally administered but course files remain author/enrollment-scoped. Keep required/optional lesson calculations.
- [ ] Re-run learning/media tests and verify revoked access on new requests; document already-issued presigned URL expiry behavior. No React changes.
- [ ] Commit `refactor: authorize learning through direct course enrollment`.

### Task 9: Course-edition exam schedules and both modes

**Files:** Replace B/assessments/service/ExamAssignmentService.java with CourseExamService.java; change schedule/assignment DTOs, repositories/controllers; remove ClassLifecycleListener implementation; T/assessments/CourseExamScheduleIT.java.

**Interfaces:** publishExam(UUID examId,Actor), attachToDraft(UUID courseVersionId,CourseExamCommand,Actor), updateSchedule(UUID courseExamId,long expectedRevision,ScheduleCommand,Actor). Required exam references freeze on edition publication; only schedules can subsequently change operationally.

- [ ] Test same-course version FKs, all question types, owner checks, immutable exam snapshots, self-paced no window, scheduled opens/closes and duration limits, mixed exams requiring manual publication, and required past-close enrollment rejection.
- [ ] Run schedule tests and observe missing new modes/direct links fail.
- [ ] Implement course_exams replacing exam_assignments, calendar validation and frozen version references. Preserve exact-set scoring and private keys. Schedule changes never mutate existing attempts.
- [ ] Re-run schedule/publication tests using MutableClock, including exact opening/closing boundaries in Dhaka and UTC.
- [ ] Commit `refactor: schedule assessments directly on course editions`.

### Task 10: Attempt start, saves and deadline recovery

**Files:** Modify B/assessments/service/{AttemptStartService,AnswerService,AttemptFinalizer,ExpiredAttemptSweep}.java, associated repository/entity/API records; T/assessments/CourseAttemptConcurrencyIT.java.

**Interfaces:** start(UUID courseExamId,Actor): AttemptView; save/submit retain attemptId and snapshot-question ID; snapshots replace classId with courseId/courseVersionId/enrollmentId.

- [ ] Test two concurrent starts, denied teacher-admin access to another learner's private attempt, self-paced deadline, scheduled truncated deadline, schedule edits preserving deadline, duplicate submit, stale answer save and expiry race.
- [ ] Run concurrency tests; use barriers/independent transactions rather than sequential mocks or sleep.
- [ ] Adapt enrollment-first attempt rules to documented user/course/enrollment/attempt lock order. Keep private snapshots, acknowledged saves and deadline finalization on requests plus background sweep.
- [ ] Re-run existing scoring tests plus new races and restarted-app deadline processing. No answer keys or unpublished grades may appear in learner DTOs.
- [ ] Commit `refactor: run resilient exams without class dependencies`.

### Task 11: Author-only grading and published results

**Files:** Modify B/grading/service/{GradingService,GradingQueue}.java, grade repositories/DTOs/controllers, assessment published-result APIs; T/grading/CourseAuthorGradingIT.java.

**Produces:** course-scoped queue, draft manual marks, immutable published revisions and transactional completion event. Role TEACHER alone never grants access.

- [ ] Test author vs unrelated approved teacher, teacher-as-learner own published results, written grading pending, explicit zero, bounds, revision conflicts, incomplete finalization and private regrade drafts.
- [ ] Run grading suite and confirm ownership/new-schema failures.
- [ ] Refactor private snapshots and grade queries to courses/enrollments, retain objective auto-publication and mixed manual publication. Recheck author active/approved on publish and account/course locks before enrollment/attempt.
- [ ] Re-run grading/history tests; published projection and immutable revision must commit together with completion notice job. A failed publication cannot leak partial scores.
- [ ] Commit `refactor: scope grades and results to course authors`.

### Task 12: Completion and certificate ownership

**Files:** Modify B/completion/service/CompletionReconciler.java, CompletionSnapshot, B/certificates/service/CertificateIssueService.java, CertificateRow/CertificateRepository and access controllers; T/completion/CourseCompletionIT.java, T/certificates/CourseCertificateIT.java.

**Produces:** required lesson/exam eligibility for pinned course edition; learner/author private downloads and author-only revoke/reinstate. No workspace-teacher bypass.

- [ ] Test no class fields, teacher learner eligibility, pending grades, published pass/fail revision, invalidation/revocation race, rendering retry, author signature snapshots and unrelated teacher download/reinstatement denied.
- [ ] Run targeted completion/certificate tests and observe schema/access failures.
- [ ] Refactor required result queries and completion snapshots; preserve unique completion/certificate per enrollment, locked progress, old identity snapshot and explicit audited reinstatement. Do not grant enrolled course authors self-certificates.
- [ ] Re-run grade correction → completion invalidation → certificate revocation → restored pass → author reinstatement journey. Verify Unicode PDF behavior using existing renderer tests.
- [ ] Commit `refactor: issue certificates for direct course completion`.

### Task 13: Course reports and complete class removal

**Files:** Rename B/reporting/service/ClassReports.java to CourseReports.java and repository/ClassReportQueries.java to CourseReportQueries.java; adapt report APIs/jobs/private assets; remove B/classrooms and obsolete ClassFixtures/ExamClassFixture; add T/reporting/CourseReportingIT.java; update T/ModuleBoundariesTest.java.

**Produces:** author-only course/version filtered reports, roster, progress, published scores and CSV exports. Learners see only their own history.

- [ ] Test author filters across old/new editions, unpublished grades excluded, another approved teacher denied export and storage URL, Unicode/formula-safe CSV, failed teacher status after queued export, and no old class routes.
- [ ] Run reporting and module-boundary tests; confirm obsolete APIs currently fail removal assertions.
- [ ] Remove synthetic class logic, old role grants, class DTOs and unused temporary-account/CSV import endpoints. Replace old tests with equivalent course-owner isolation coverage. Check all modules including media, operations and demo fixtures.
- [ ] Run `rg -n 'classrooms|class_id|classId|ADMINISTRATOR|isAdministrator|course_teachers|user_roles' backend/src/main/java backend/src/main/resources/db/coursecentric docs/api/openapi.yaml`; require zero legacy domain hits (Java `class` keyword and archived migration/docs are unrelated). Run ArchUnit and report suite.
- [ ] Commit `refactor: remove class architecture and finish course reports`.

### Task 14: Contracts, delivery and reset/recovery runbooks

**Files:** docs/api/openapi.yaml, docs/api/course-centric-breaking-changes.md, docs/runbooks/{configuration,v2-database-reset,backup-restore}.md, AGENTS.md, README.md, HowToSetup.md, B/identity/config/DemoAccounts.java, backup/restore scripts and config references; T/operations/CourseCentricRecoveryIT.java.

**Produces:** backend OpenAPI for every route in spec, explicit frontend follow-up map, SMTP/bootstrap settings and separate new-schema restore policy.

- [ ] Write contract tests matching actual controllers, fresh install + setup tests and recovery fixture with verified learner, approved author, request/invite, progress, published grade and certificate. Test legacy backup rejection for v2 target.
- [ ] Run OpenApiContractIT and recovery tests; expect missing routes/new-schema fixtures to fail.
- [ ] Update operational controls to approved teachers; demo-only profiles use the two roles and cannot bypass production verification. Document fresh database creation and optional explicit old-DB deletion only after naming/checking target and preserving wanted backup. Do not embed credentials or execute deletion while updating docs.
- [ ] Restore a new-schema backup into isolated PostgreSQL with outbound jobs disabled, verify private assets/results/certificate. Confirm frontend source unchanged and list broken/replacement routes, role payloads and new UI needs.
- [ ] Commit `docs: define course-centric backend deployment and API changes`.

### Task 15: Backend release acceptance and verification

**Files:** T/CourseCentricReleaseJourneyIT.java, existing ReleaseJourneyIT/support fixtures, docs/verification-results.md and docs/release-checklist.md; backend CI job as needed.

**Produces:** a verified backend release, not a claim that the old frontend is compatible.

- [ ] Add full API journeys: owner setup/verify; student signup/verify; teacher signup/verify/peer approve; author publish; learner request/author grant; invitation/new signup/verify/accept; teacher learns; both exam modes; written grade/publication; completion/certificate; unrelated teacher denied private course operations.
- [ ] Run new journeys, fix owning-module failures and preserve concurrency/authorization coverage. Assert duplicate notifications/jobs are bounded and published grade correction reconciles completion.
- [ ] Review public/private DTOs, session approval refresh, transaction lock order, new SQL baseline and removed domain references. Review dependency/secret scans using existing project tooling without unrequested dependency upgrades.
- [ ] Run from backend: `./gradlew check` then `./gradlew integrationTest --tests '*CourseCentricReleaseJourneyIT'` if a targeted rerun is justified by changes after check. Record exact commands, environment, outputs, skipped/blocked checks and remaining frontend incompatibility. Do not label failing/skipped checks as passing.
- [ ] Commit `test: qualify course-centric learning backend` and present milestone evidence and material deviations.

## Acceptance matrix

| Requirement | Verification tasks |
| --- | --- |
| Two roles, no administrator bypass | 1–2, 5, 8, 11–13 |
| Workspace setup and verified owner | 2–3, 14–15 |
| Public signup/mandatory verification | 3, 15 |
| Peer teacher review and notifications | 4, 15 |
| Teacher learner using same account | 6, 8, 10–12, 15 |
| Public safe catalog | 5 |
| Author-approved course request | 6 |
| Author invite auto-grant on acceptance | 7 |
| No class architecture anywhere active | 1, 8–13 |
| Both exam modes and grading | 9–11 |
| Progress, completion and certificates | 8, 12 |
| Private author reports and files | 8, 13 |
| Fresh DB/reset boundaries/recovery | 1, 14 |
| Backend-only contract and frontend impact | 14–15 |

## Planning review result

The plan preserves the six answered product choices, names current files and actual Gradle commands, and covers all modules affected by removing classes. The SQL blueprint includes state/check/FK/index decisions; service rules cover approval, ownership, verification and concurrent state transitions. Snippets are illustrative future implementation, not code written into LMS. Operational defaults in the spec remain reviewable. No application tests, SQL migrations, deletion, deployment or feature implementation were performed during planning.
