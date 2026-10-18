# Learning Portal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a self-hosted learning portal for tutors and small schools with private materials, class progress, mixed exams, teacher grading and certificates.

**Architecture:** One institution per installation, one Spring Boot modular monolith and one same-origin React frontend. PostgreSQL owns transactional state and durable jobs; private local or S3 storage owns files. Published content, attempt questions and released grades preserve historical meaning.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Maven Wrapper, PostgreSQL 17, Spring Security/Session JDBC, Flyway, React 19, TypeScript 5, Vite, PDFBox 3, ZXing, Docker Compose.

**Spec:** [Learning Portal Architecture Specification](../specs/2026-10-18-learning-portal-architecture.md). Read the complete specification before execution. This plan and the written spec are for user review; no application code exists yet.

## Global Constraints

- One institution per installation; no tenant routing or `tenant_id` columns in release 1.
- No payments, subscriptions, billing, or payment-provider dependencies.
- Java 21; Spring Boot 4.1.1; Maven Wrapper 3.9.x; PostgreSQL 17.
- React 19; TypeScript 5; Vite; Node.js 22.12 or newer within major 22; npm lockfile committed.
- One backend application; same-origin React and `/api/v1`; Spring Security sessions stored by Spring Session JDBC.
- Feature packages under `com.saasresearcher.learning`; module internals must not be imported across modules.
- UUID record identifiers; UTC `timestamptz` business timestamps; `Instant` and injected `Clock` in Java.
- Flyway owns schema changes; Hibernate schema mode is `validate`; no destructive automatic schema updates.
- PostgreSQL is authoritative for deadlines, answers, grades, completion and durable jobs; browser storage is never the source of truth.
- Content and exam publication editions are immutable; corrections publish a new edition.
- Existing classes retain their course edition and existing attempts retain their question snapshots.
- No correct answers, grading keys or unpublished grades in student API payloads.
- All protected records require server-side assignment or enrollment authorization.
- Exams include automatic objective scoring and teacher-graded written answers; no file-based exam answers.
- Certificates require all required lessons completed and all required exam assignments passed using published results.
- Docker Compose and manual Ubuntu VPS installation are required delivery paths.

## Review Focus

These cross-cutting cases need explicit tests in the tasks named below:

1. Session expiry or account disable during an exam: acknowledged answers survive; further unauthorized saves fail; deadline finalization still works (Tasks 2, 11).
2. Deadline, autosave and submit arrive together: one ordered final attempt; no late or stale answer overwrites (Tasks 11, 18).
3. Names, timezones and imported text are not simple ASCII: display names survive, date boundaries are correct, CSV cannot execute spreadsheet formulas (Tasks 7, 9, 15).
4. Worker dies between external work and acknowledgement: certificate/job retries reuse identity; SMTP duplicate risk is documented (Tasks 3, 14).
5. A previously passing published grade becomes failing: completion invalidates and issued/pending certificates are revoked consistently (Tasks 12–14).

## Repository findings and execution rules

The repository contains only Git metadata and these planning documents. It has no commits, source code, dependencies, tests or inherited `AGENTS.md` instructions discovered during planning. All application paths below are proposed new files. Do not mistake filenames in this plan for existing code.

The milestones are sequential because they share domain interfaces. Finish and verify a task before proceeding. A task includes backend, frontend and relevant tests where necessary to deliver a usable flow. Do not install dependencies, generate the application, create a worktree or change Git state until implementation is authorized. Choose an execution method at handoff. Use a `codex/` branch for implementation. Commit each completed task after its checks pass; stage that task's files explicitly, never unrelated work.

Each task uses a red/green cycle: write its named tests, run to observe a meaningful failing assertion or missing feature, implement, then rerun. Missing Docker, broken test infrastructure or dependency resolution is a blocker to fix, not a valid red test. No tests have been run for application behavior during planning.

## File map and conventions

To keep paths readable, the following prefixes expand literally:

| Prefix | Exact repository-relative directory |
| --- | --- |
| B | `backend/src/main/java/com/saasresearcher/learning/` |
| T | `backend/src/test/java/com/saasresearcher/learning/` |
| M | `backend/src/main/resources/db/migration/` |
| F | `frontend/src/features/` |
| E | `frontend/e2e/` |

Thus `B/identity/api/IdentityAccess.java` means `backend/src/main/java/com/saasresearcher/learning/identity/api/IdentityAccess.java`. Each file named below has one responsibility. Add focused controllers, repositories and DTO records in the same owning module as needed; do not combine a whole module in one service. Public interfaces live in `api`, implementations in `internal`. Each task updates the affected paths in `docs/api/openapi.yaml`; the API table in the spec is authoritative.

Root files: `README.md`, `.gitignore`, `.env.example`, `.github/workflows/ci.yml`. Backend build: `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/`. Frontend build: `frontend/package.json`, `frontend/package-lock.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`. Infrastructure: `deploy/compose.yml`, `deploy/Caddyfile`, `deploy/backend.Dockerfile`, `deploy/frontend.Dockerfile`.

## Shared interface decisions

Use the public signatures in spec §5. Define shared `Actor`, `Role`, `PageQuery` and `PageResponse` in `B/common/`; module-specific DTOs stay with their owning API. `Actor` is derived exclusively from authentication. Request dates are UTC instants with explicit timezone-aware display in the frontend.

Two dependency details keep the module graph acyclic:

- Media delivery uses `media/api/AssetAccessPolicy.java`, a registered strategy with `boolean canRead(UUID assetId, Actor actor)`. Courses and classrooms supply policy implementations; media does not import their internals. Course publishing checks availability through `MediaCatalog.requireAvailable(UUID assetId): AssetMetadata`.
- Assessments owns a published result projection on attempts. Grading publishes its immutable revision and calls `AttemptResults.recordPublished(UUID attemptId, UUID gradeRevisionId, BigDecimal earned, BigDecimal maximum): void` in the same transaction. `AssessmentResults.requiredResults` reads that projection; assessments never imports grading repositories. Completion and reporting consume published projections only.

## Milestones and dependencies

| Milestone | Tasks | Working result |
| --- | --- | --- |
| A. Foundation | 1–3 | Reproducible app, accounts, audit and durable jobs |
| B. Teaching | 4–8 | Private materials, published course, class roster and lesson progress |
| C. Assessment | 9–12 | Versioned mixed exams, resilient student attempts and published grades |
| D. Completion | 13–15 | Completion, certificates and teacher reports |
| E. Delivery | 16–18 | Deployable package, verified recovery and release evidence |

### Task 1: Reproducible application and test foundation

**Dependencies:** None.  
**Files:** Root/build/infrastructure files in the file map; `B/LearningPortalApplication.java`, `B/common/TimeConfiguration.java`, `B/common/Actor.java`, `B/common/Role.java`, `B/common/PageQuery.java`, `B/common/PageResponse.java`; `T/support/PostgresIntegrationTest.java`, `T/FoundationIT.java`, `T/ModuleBoundariesTest.java`; `frontend/src/app/App.tsx`; `docs/dependencies.md`, `docs/api/openapi.yaml`.

**Interfaces:** Produces injected `Clock`, base problem response and pagination types; reusable PostgreSQL/HTTP integration fixture. Configure Surefire for `*Test` and Failsafe for `*IT`; `./mvnw verify` runs both. Configure `npm test -- --run`, `npm run typecheck`, `npm run lint`, `npm run build`, `npm run test:e2e`.

- [ ] Write `FoundationIT.databaseAndHealthAreReady` and `ModuleBoundariesTest.noCrossModuleInternalImports`; assert healthy DB-backed readiness and module isolation.
- [ ] Run `cd backend && ./mvnw verify`; establish the fixture, then confirm missing application behavior fails for the intended reason.
- [ ] Scaffold backend/frontend and one-command development dependencies. Resolve compatible dependency patches, commit lockfiles, keep schema validation and fail-fast production configuration. Add minimal CI for backend verification and frontend lint/typecheck/test/build.
- [ ] Run the backend command and `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build`; expect successful exits, not skipped suites. Record resolved dependencies and licences.
- [ ] Commit `chore: establish learning portal application and verification`.

### Task 2: Institution setup, accounts and secure sessions

**Dependencies:** 1.  
**Files:** `B/identity/api/IdentityAccess.java`, `B/identity/internal/AccountService.java`, `B/identity/internal/SecurityConfiguration.java`, `B/identity/internal/AccountRecoveryService.java`, `B/institution/api/InstitutionSettings.java`, `B/institution/internal/InstitutionService.java`; `M/V001__identity_and_institution.sql`; `T/identity/AccountSecurityIT.java`; `F/auth/LoginPage.tsx`, `F/auth/ChangePasswordPage.tsx`, `F/settings/InstitutionSettingsPage.tsx`, `F/accounts/AccountListPage.tsx`; `E/accounts.spec.ts`.

**Interfaces:** Produces `IdentityAccess.requireActive(UUID): Actor`, `InstitutionSettings.get(): InstitutionProfile`, authenticated `/auth/me`, CSRF flow and role switching. `AccountRecoveryService.assistedReset(UUID studentId, Actor actor): TemporaryCredential` checks teaching scope once class access exists in Task 6; until then administrator-only.

- [ ] Write tests: one-use setup, last-admin protection, normalized username duplicates, optional student email, required staff email, hashed expiring reset tokens, session invalidation, login caps and CSRF. Example assertions: `assertThat(resetAgain.statusCode()).isEqualTo(400); assertThat(disabledSession.statusCode()).isEqualTo(401);`.
- [ ] Run `cd backend && ./mvnw -Dit.test=AccountSecurityIT verify`; expect failures for missing account/security behavior.
- [ ] Implement spec §6 and §16, migrations, account/settings screens and password-change gate. Do not introduce public signup or grant course access from invitations alone.
- [ ] Rerun the integration suite and `cd frontend && npm run test:e2e -- accounts.spec.ts`; expect create/login/reset/disable journeys to pass and session cookies to have production attributes.
- [ ] Commit `feat: add institution accounts and session security`.

### Task 3: Audit trail and transactional durable work

**Dependencies:** 2.  
**Files:** `B/audit/api/AuditLog.java`, `B/audit/internal/AuditService.java`, `B/notifications/api/DurableJobs.java`, `B/notifications/api/JobRequest.java`, `B/notifications/internal/JobRunner.java`, `B/notifications/internal/EmailJobHandler.java`, `B/notifications/internal/NotificationService.java`; `M/V002__audit_jobs_notifications.sql`; `T/notifications/JobRecoveryIT.java`; `F/notifications/NotificationList.tsx`.

**Interfaces:** Produces `AuditLog.record(AuditEntry): void`, `DurableJobs.enqueue(JobRequest): UUID` within caller transaction, and `JobHandler.handle(JobRequest): void` registration. Audit entries contain no credentials or raw answers.

- [ ] Write `JobRecoveryIT` for rollback removing jobs, duplicate deduplication keys, competing workers, crashed lease reclaim, five-failure terminal state and duplicate in-app notification prevention. Assert one job claim and unchanged aggregate identity after retry.
- [ ] Run `cd backend && ./mvnw -Dit.test=JobRecoveryIT verify`; expect durable behavior failures.
- [ ] Implement spec retry intervals/leases, protected failed-job visibility, SMTP adapter and own-notification endpoints/UI. Connect invitation/reset delivery; record SMTP acknowledgement uncertainty in the runbook.
- [ ] Rerun `JobRecoveryIT` and component tests for unread/read/error states; verify job failures do not roll back already committed user actions.
- [ ] Commit `feat: add audit and recoverable notifications`.

### Task 4: Safe private local media

**Dependencies:** 2–3.  
**Files:** `B/media/api/AssetStore.java`, `B/media/api/MediaCatalog.java`, `B/media/api/MediaService.java`, `B/media/api/AssetAccessPolicy.java`, `B/media/internal/LocalAssetStore.java`, `B/media/internal/UploadService.java`, `B/media/internal/MediaController.java`; `M/V003__media_assets.sql`; `T/media/LocalMediaIT.java`; `F/materials/UploadField.tsx`.

**Interfaces:** Produces `AssetStore.put(UploadSource): StoredAsset`, `stat(String): AssetStat`, `openRange(String, ByteRange): AssetStream`, `delete(String): void`; `MediaCatalog.requireAvailable(UUID): AssetMetadata`; `MediaService.authorizeRead(UUID,Actor): AuthorizedAsset`.

- [ ] Write range tests (206/416), unavailable/rejected upload tests, path traversal, content spoofing, unauthorized read, 500-MiB video/25-MiB document/5-MiB image boundaries and interrupted-upload cleanup. Use generated streaming input instead of allocating full videos in memory.
- [ ] Run `cd backend && ./mvnw -Dit.test=LocalMediaIT verify`; expect missing upload/range/access behavior.
- [ ] Implement staged streaming, signatures/type checks, ffprobe metadata, re-encoded branding images and spec §9 lifecycle. Default policy denies access; uploader preview is allowed until linked policies exist. Add cancellation/progress/error UI.
- [ ] Rerun media tests and `cd frontend && npm test -- --run`; confirm private content cannot be fetched by guessing asset UUIDs or storage keys.
- [ ] Commit `feat: add private streamed media storage`.

### Task 5: Course authoring and immutable publishing

**Dependencies:** 4.  
**Files:** `B/courses/api/CourseCatalog.java`, `B/courses/internal/CourseAuthoringService.java`, `B/courses/internal/CoursePublicationService.java`, `B/courses/internal/CourseAssetPolicy.java`; `M/V004__courses_and_editions.sql`; `T/courses/CoursePublicationIT.java`; `F/courses/CourseListPage.tsx`, `F/courses/CourseEditorPage.tsx`, `F/courses/CoursePreviewPage.tsx`; `E/course-authoring.spec.ts`.

**Interfaces:** Produces `CourseCatalog.requirePublished(UUID): PublishedCourse`, `publish(UUID courseId, long expectedRevision, Actor): PublishedCourse`, `copyToDraft(UUID versionId, Actor): UUID`. PublishedCourse exposes edition ID, ordered student-safe lesson descriptors and required lesson IDs, not persistence entities.

- [ ] Write tests for assigned authors only, sanitized text, provider-ID embed validation, missing/unavailable assets, required lesson rules, publish-once concurrency and copying editions. Assert old lesson text stays unchanged after publishing a correction.
- [ ] Run `cd backend && ./mvnw -Dit.test=CoursePublicationIT verify`; expect publication/authorization failures.
- [ ] Implement authoring, bounded lesson ordering, preview and archiving; register course media policy. Published editions reject every mutation, including spelling changes; corrections use new drafts.
- [ ] Rerun backend tests and `cd frontend && npm run test:e2e -- course-authoring.spec.ts`; verify teacher can author and preview video, PDF and text lessons.
- [ ] Commit `feat: add versioned course authoring`.

### Task 6: Classes, teaching assignments and roster access

**Dependencies:** 5.  
**Files:** `B/classrooms/api/EnrollmentAccess.java`, `B/classrooms/api/TeachingAccess.java`, `B/classrooms/internal/ClassService.java`, `B/classrooms/internal/EnrollmentService.java`, `B/classrooms/internal/ClassAssetPolicy.java`; `M/V005__classes_and_enrollments.sql`; `T/classrooms/EnrollmentAccessIT.java`; `F/classrooms/ClassListPage.tsx`, `F/classrooms/ClassDetailPage.tsx`; `E/classrooms.spec.ts`.

**Interfaces:** Produces `EnrollmentAccess.requireActive(UUID,Actor): EnrollmentView`, `TeachingAccess.requireClassTeacher(UUID classId,Actor): void`, `TeachingAccess.canManageStudent(UUID studentId,Actor): boolean`. Produces class activation/frozen policy for later tasks.

- [ ] Write tests for teacher reassignment, unrelated-course author denied class access, inactive windows, unique roster membership and archived historical read access. Assert active learning access fails after suspension but historical owned results remain visible.
- [ ] Run `cd backend && ./mvnw -Dit.test=EnrollmentAccessIT verify`; expect scoped-access failures.
- [ ] Implement class lifecycle and enrollment states, tutor default-class shortcut, class teacher assignment and media policy. Extend assisted account resets to authorized class teachers without enabling staff-account resets.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- classrooms.spec.ts`; verify one course can serve two classes with isolated rosters.
- [ ] Commit `feat: add class rosters and scoped teaching access`.

### Task 7: Student import and invitation delivery

**Dependencies:** 6.  
**Files:** `B/classrooms/internal/RosterImportService.java`, `B/classrooms/api/RosterImportPreview.java`; `T/classrooms/RosterImportIT.java`; `F/classrooms/RosterImportDialog.tsx`; `E/roster-import.spec.ts`.

**Interfaces:** `preview(UUID classId, InputStream csv, Actor): RosterImportPreview`; `commit(UUID classId, RosterImportCommand, Actor): ImportResult`. Commit takes validated rows plus a digest, then revalidates all identities under transaction; preview is never an authorization token.

- [ ] Test UTF-8/BOM, Bengali display names, 1,000/1,001 rows, malformed header, duplicates after normalization, existing staff identities and state changes between preview/commit. Assert one invalid row leaves zero new enrollments and existing identity fields unchanged.
- [ ] Run `cd backend && ./mvnw -Dit.test=RosterImportIT verify`; expect import-validation failures.
- [ ] Implement all-or-nothing import, invitation jobs or one-time temporary-credential download, preview and row errors. Mark credential responses `no-store`; ordinary exports never include credentials.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- roster-import.spec.ts`; verify both emailed and email-less student setup.
- [ ] Commit `feat: add validated student roster import`.

### Task 8: Student course player and progress

**Dependencies:** 6.  
**Files:** `B/learning/api/LearningProgress.java`, `B/learning/internal/ProgressService.java`; `M/V006__lesson_progress.sql`; `T/learning/ProgressIT.java`; `F/learning/StudentDashboardPage.tsx`, `F/learning/CoursePlayerPage.tsx`, `F/learning/DocumentViewer.tsx`, `F/learning/VideoPlayer.tsx`; `E/learning.spec.ts`.

**Interfaces:** Produces `LearningProgress.summary(UUID): LessonSummary` and `save(UUID enrollmentId, UUID lessonId, ProgressCommand, Actor): ProgressView`; `ProgressCommand(long expectedRevision, boolean completed, long resumeSeconds)`.

- [ ] Test explicit completion, optional/zero-required lessons, monotonic resume, revision conflicts and another edition's lesson rejected. Assert retries create one progress record and old saves cannot reduce acknowledged resume position.
- [ ] Run `cd backend && ./mvnw -Dit.test=ProgressIT verify`; expect progress/access failures.
- [ ] Implement own-learning API, secure player, completion toggle, resume, accessible loading/error/empty states and last activity. Keep a tested progress-change integration seam; Task 13 connects it to durable completion jobs. Do not enqueue jobs with no production handler or claim certificates work at this milestone.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- learning.spec.ts`; verify refresh persistence, mobile layout and forbidden file access.
- [ ] Commit `feat: add student learning and durable progress`.

### Task 9: Versioned mixed-exam authoring and scheduling

**Dependencies:** 6.  
**Files:** `B/assessments/api/ExamCatalog.java`, `B/assessments/internal/ExamAuthoringService.java`, `B/assessments/internal/ExamAssignmentService.java`; `M/V007__exams_and_assignments.sql`; `T/assessments/ExamPublicationIT.java`; `F/assessments/ExamEditorPage.tsx`, `F/assessments/ExamScheduleDialog.tsx`; `E/exam-authoring.spec.ts`.

**Interfaces:** `publish(UUID examId,long expectedRevision,Actor): PublishedExam`; `assign(UUID classId,ExamAssignmentCommand,Actor): UUID`. Extend CoursePublicationService via a course-owned requirement validator using ExamCatalog; no reverse import from assessments to courses internals.

- [ ] Test all four question types, exact-set keys, point/length/question limits, incomplete keys, immutable editions, finite deadlines and mixed-exam teacher-publication requirement. Test UTC/Dhaka date conversion and equal open/close rejection.
- [ ] Run `cd backend && ./mvnw -Dit.test=ExamPublicationIT verify`; expect validation/publication failures.
- [ ] Implement authoring/schedules and frozen required exam links. Class activation materializes requirements; reject changing required assignments after enrollment. Existing attempts later retain original timing even if an unopened schedule changes.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- exam-authoring.spec.ts`; verify course publishing with required exam editions and teacher preview.
- [ ] Commit `feat: add versioned exams and class scheduling`.

### Task 10: Attempt start, snapshots and student-safe payloads

**Dependencies:** 9.  
**Files:** `B/assessments/api/AttemptService.java`, `B/assessments/api/AttemptView.java`, `B/assessments/internal/AttemptStartService.java`; `M/V008__attempts_and_answers.sql`; `T/assessments/AttemptStartIT.java`; `F/assessments/ExamInstructionsPage.tsx`, `F/assessments/ExamAttemptPage.tsx`.

**Interfaces:** `AttemptService.start(UUID assignmentId,Actor): AttemptView`; `get(UUID attemptId,Actor): AttemptView`. AttemptView includes serverNow, expiresAt, saved answers and stable shuffled order; no keys, rubric or unreleased grades.

- [ ] Test schedule boundaries, same-student concurrent start, maximum attempts, copied question/option order and hidden keys. Assert simultaneous starts return the same active attempt ID; mutation of a later exam edition cannot change it.
- [ ] Run `cd backend && ./mvnw -Dit.test=AttemptStartIT verify`; expect snapshot/start failures.
- [ ] Implement enrollment lock, attempt constraints, deadlines and minimal student exam interface. Show server-based timer; do not store answers in localStorage.
- [ ] Rerun integration and frontend tests; inspect serialized attempt JSON for prohibited fields and verify written input length enforcement.
- [ ] Commit `feat: add stable student exam attempts`.

### Task 11: Autosave, deadlines, scoring and finalization

**Dependencies:** 10 and 3.  
**Files:** `B/assessments/api/AnswerSaveCommand.java`, `B/assessments/internal/AnswerService.java`, `B/assessments/internal/AttemptFinalizer.java`, `B/assessments/internal/ObjectiveScorer.java`, `B/assessments/internal/ExpiredAttemptSweep.java`; `T/assessments/AttemptConcurrencyIT.java`, `T/assessments/ObjectiveScorerTest.java`; `F/assessments/useAnswerAutosave.ts`, `F/assessments/SaveStatus.tsx`; `E/exam-submission.spec.ts`.

**Interfaces:** `save(UUID attemptId,UUID questionId,AnswerSaveCommand,Actor): AnswerSaved`; `submit(UUID attemptId,Actor): AttemptOutcome`; `finalizeExpired(UUID attemptId): AttemptOutcome`. Produces private provisional objective scores for grading.

- [ ] Write barrier-based races for save/submit/expiry, stale revision 409, duplicate submission and exact deadline. Score fixtures: exact multiple-select earns full points; extra selection earns zero; unanswered earns zero. Test disabled/expired session, reconnect and unsaved-warning states.
- [ ] Run `cd backend && ./mvnw -Dtest=ObjectiveScorerTest -Dit.test=AttemptConcurrencyIT verify`; expect scoring and concurrency failures.
- [ ] Implement attempt-first locking for all writes; server Clock boundaries; serialized per-question client saves; explicit conflict reconciliation; durable expired sweep every 15 seconds. Finalize on request paths too. Retry submit without rescoring/reopening.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- exam-submission.spec.ts`; assert a refreshed/disconnected browser does not extend deadlines and acknowledged answers survive application restart.
- [ ] Commit `feat: make exam saves and submission race safe`.

### Task 12: Written grading and immutable result publication

**Dependencies:** 11.  
**Files:** `B/grading/api/GradePublication.java`, `B/grading/internal/GradingService.java`, `B/assessments/api/AttemptResults.java`, `B/assessments/api/AssessmentResults.java`; `M/V009__grade_revisions.sql`; `T/grading/GradePublicationIT.java`; `F/grading/GradingQueuePage.tsx`, `F/grading/GradeAttemptPage.tsx`, `F/results/StudentResultsPage.tsx`; `E/grading.spec.ts`.

**Interfaces:** `saveGrade(UUID attemptId,UUID questionId,GradeCommand,Actor): GradeView`; `finalizeGrade(UUID,long expectedRevision,Actor): GradedResult`; `GradePublication.publish(UUID,long expectedRevision,Actor): PublishedResult`; `regrade(UUID,String reason,Actor): GradeDraft`; assessments projection contract in Shared interface decisions.

- [ ] Test incomplete grading, intentional zero, bounded decimals, unrelated teacher access, concurrent revision, objective auto-publication, mixed teacher-publication, hidden drafts and feedback release. Assert regrade drafts do not replace the student's previous published score.
- [ ] Run `cd backend && ./mvnw -Dit.test=GradePublicationIT verify`; expect grade/publication failures.
- [ ] Implement grading queue, explicit finalization/publication, immutable per-question result revisions and transactional published projection update plus reconciliation job. Preserve original scores until replacement commits.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- grading.spec.ts`; verify students see pending written grading and subsequently the published feedback only.
- [ ] Commit `feat: add teacher grading and result publication`.

### Task 13: Completion reconciliation

**Dependencies:** 8 and 12.  
**Files:** `B/completion/api/CompletionService.java`, `B/completion/internal/CompletionReconciler.java`; `M/V010__course_completions.sql`; `T/completion/CompletionIT.java`; `F/learning/CompletionRequirements.tsx`.

**Interfaces:** `CompletionService.reconcile(UUID enrollmentId): CompletionState`; `get(UUID completionId): CompletionSnapshot`. Snapshot includes enrollment/student/course edition IDs, achievement time and eligibility state, with no mutable branding.

- [ ] Test required/optional lessons, zero requirements, highest published eligible attempt, pending grades, duplicate jobs and grade correction. Assert one completion record, invalidation on loss of required pass and restoration of eligibility without creating a second record.
- [ ] Run `cd backend && ./mvnw -Dit.test=CompletionIT verify`; expect eligibility failures.
- [ ] Implement enrollment-locked reconciliation, connect learning/grading jobs, and enqueue issue/revoke work transactionally. Render explicit unmet requirements, including pending grading. Backfill reconciliation for existing enrollments when this capability is introduced; no pending progress event may be lost.
- [ ] Rerun completion tests and frontend tests; verify grade publication after a delayed job eventually updates eligibility without relying on a student page visit.
- [ ] Commit `feat: add deterministic course completion`.

### Task 14: Certificates and verification

**Dependencies:** 13 and 4.  
**Files:** `B/certificates/api/CertificateService.java`, `B/certificates/internal/CertificateIssueService.java`, `B/certificates/internal/CertificateRenderer.java`, `B/certificates/internal/CertificateVerificationController.java`; `M/V011__certificates.sql`; `T/certificates/CertificateLifecycleIT.java`; `F/certificates/StudentCertificatesPage.tsx`, `F/certificates/VerifyCertificatePage.tsx`; `E/certificates.spec.ts`; `docs/third-party-notices.md`.

**Interfaces:** `issue(UUID completionId): UUID`; `revoke(UUID certificateId,String reason,Actor): void`; `reinstate(UUID certificateId,String reason,Actor): void`; `verify(String token): PublicCertificateStatus`. Reinstatement is administrator-only and rechecks eligibility.

- [ ] Test duplicate render jobs, crash-after-upload retry, revoke-before-render race, grade-failure revocation, explicit reinstatement and snapshot stability. Assert verification disabled returns 404, unrelated downloads fail, tokens are unguessable and no email/name leaks by default. Test Bengali text or an explicit unsupported-glyph error.
- [ ] Run `cd backend && ./mvnw -Dit.test=CertificateLifecycleIT verify`; expect lifecycle/privacy failures.
- [ ] Implement unique issuance, PDFBox rendering, embedded licensed font, hashed lookup tokens, optional QR and audit-backed revocation/reinstatement. Publish rendered asset under a deterministic certificate key after a locked status recheck.
- [ ] Rerun lifecycle tests and `cd frontend && npm run test:e2e -- certificates.spec.ts`; inspect a generated PDF visually during implementation and verify download after class withdrawal.
- [ ] Commit `feat: add verifiable course certificates`.

### Task 15: Teacher reports and safe exports

**Dependencies:** 12–14.  
**Files:** `B/reporting/api/ReportingService.java`, `B/reporting/internal/ClassReportQueries.java`, `B/reporting/internal/ReportExportHandler.java`; `T/reporting/ClassReportingIT.java`; `F/reports/ClassProgressPage.tsx`, `F/reports/ClassScoresPage.tsx`; `E/reporting.spec.ts`.

**Interfaces:** `classProgress(UUID,Actor,PageQuery): PageResponse<StudentProgressRow>`; `classScores(UUID,Actor,PageQuery): PageResponse<StudentScoreRow>`; `requestExport(UUID classId,ReportType,Actor): UUID`. Export handler uses durable jobs/private files.

- [ ] Test required lesson denominator, never-active student inactivity, seven-day default, unpublished grades absent, class assignment revoked after export queued and CSV formula prefixes. Assert export owner access is rechecked at download and columns never include reset credentials or answer keys.
- [ ] Run `cd backend && ./mvnw -Dit.test=ClassReportingIT verify`; expect reporting/filtering failures.
- [ ] Implement indexed paginated projections, class filters, status explanations and private CSV jobs with published revision/timezone context. Escape CSV syntax and neutralize spreadsheet formula cells.
- [ ] Rerun tests and `cd frontend && npm run test:e2e -- reporting.spec.ts`; verify Unicode names and keyboard navigation in the progress matrix.
- [ ] Commit `feat: add scoped learning reports and exports`.

### Task 16: S3 storage and production deployment

**Dependencies:** 14–15.  
**Files:** `B/media/internal/S3AssetStore.java`, `T/media/S3MediaIT.java`; deployment files from file map; `.env.example`; `docs/runbooks/install-docker.md`, `docs/runbooks/install-vps.md`, `docs/runbooks/configuration.md`; `E/installation-smoke.spec.ts`.

**Interfaces:** Same AssetStore and MediaService contracts from Task 4; storage selection comes from configuration, never caller input. Produce working clean-install Docker and manual VPS paths with private storage and same-origin routing.

- [ ] Write S3-compatible contract tests for multipart abort, missing object, checksum, 60-second URL expiry, withdrawn access and no public ACL. Write installation smoke test covering setup/login/upload/student playback.
- [ ] Run `cd backend && ./mvnw -Dit.test=S3MediaIT verify`; expect missing adapter behavior, not missing emulator credentials.
- [ ] Implement private S3 adapter with a pinned S3-compatible test container, streaming uploads and bounded retry; production images include ffprobe, fonts and non-root execution. Configure HTTPS, range proxying, health and secrets. Document external media privacy and SMTP limitations.
- [ ] Run clean Docker installation and the manual VPS procedure in an isolated Ubuntu environment; run `cd frontend && npm run test:e2e -- installation-smoke.spec.ts` against both storage modes. Save actual evidence, not a claim based on reading manifests.
- [ ] Commit `feat: package private storage and production installation`.

### Task 17: Backup, restoration and upgrades

**Dependencies:** 16.  
**Files:** `scripts/backup.sh`, `scripts/restore.sh`, `scripts/verify-backup.sh`, `backend/src/test/resources/fixtures/recovery-manifest.json`; `docs/runbooks/backup-restore.md`, `docs/runbooks/upgrades.md`; `T/operations/RecoverySmokeIT.java`.

**Interfaces:** `backup.sh --output PATH`, `verify-backup.sh ARCHIVE`, `restore.sh ARCHIVE --target isolated`; scripts reject missing keys, unsafe live targets and invalid checksums. Maintenance mode allows health/admin recovery actions while denying ordinary mutations and pausing job claims.

- [ ] Define a recovery fixture with one published course, uploaded video/PDF, mixed exam, published grade and issued certificate. Test missing media, corrupt archive and wrong decryption key abort before replacement.
- [ ] Run recovery smoke checks against an empty destination; expect failure until a complete consistent restore exists.
- [ ] Implement mutation quiescence, job draining, DB/media manifest, encrypted archive, verification and retention preserving the last good copy. Include S3 object export; default restore disables outbound jobs/SMTP until administrator enables them.
- [ ] Back up fixture, restore into a different clean instance, and run `cd backend && ./mvnw -Dit.test=RecoverySmokeIT verify`; verify login, ownership checks, playable media, grade and certificate. Record measured RPO/RTO procedure and rehearse pre-upgrade backup/restore rollback.
- [ ] Commit `feat: add verified backup and recovery workflows`.

### Task 18: End-to-end release qualification

**Dependencies:** All previous tasks.  
**Files:** `E/complete-learning-journey.spec.ts`, `E/exam-recovery.spec.ts`, `frontend/src/shared/accessibility.test.tsx`, `backend/src/test/java/com/saasresearcher/learning/ReleaseJourneyIT.java`, `scripts/load/learning-portal.js`; `README.md`, `HowToSetup.md`, `AGENTS.md`, `docs/release-checklist.md`, `docs/verification-results.md`.

**Interfaces:** No new domain features. Deliver a versioned source package, demo seed profile, setup/architecture notes and reproducible verification evidence. Demo credentials exist only in explicit demo mode and are rejected in production configuration.

- [ ] Write cross-role journeys covering publish/enroll/study/mixed exam/grade/result/certificate; include teacher reassignment, restart mid-attempt, browser reconnect, grade correction and withdrawal. Assert histories persist and no inaccessible records leak.
- [ ] Run new journeys against the assembled app and fix integration failures within the owning module; do not weaken assertions to match accidental behavior.
- [ ] Complete keyboard/mobile states, restrictive CSP, security/dependency/secret checks, third-party notices and clean-machine documentation. Profile at the specification workload with synthetic data; report actual latency and throughput rather than assuming capacity.
- [ ] Run `cd backend && ./mvnw verify`; `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build && npm run test:e2e`. Re-run clean installation/recovery only if packaging or storage changed since Tasks 16–17. Record commands, outcomes, environment and limitations in verification-results. Fix any failing required gate before release.
- [ ] Commit `test: qualify learning portal release and delivery documentation`.

## Requirement coverage

| Requirement | Owning tasks |
| --- | --- |
| R01 institution | 2, 16 |
| R02 scoped roles | 2, 6, 12, 15 |
| R03 account lifecycle | 2, 6–7 |
| R04 authoring/editions | 5, 9 |
| R05 classes | 6, 9 |
| R06 enrollment/import | 6–7 |
| R07 private materials | 4–5, 16 |
| R08 progress | 8, 15 |
| R09 question types | 9, 11–12 |
| R10 deadlines/attempt limits | 9–11 |
| R11 autosave/submission | 10–11, 18 |
| R12 grading/publication | 11–12 |
| R13 completion | 13 |
| R14 certificates | 14 |
| R15 durable work | 3, 11, 13–15 |
| R16 reporting | 15 |
| R17 security/audit | 2–7, 10–12, 14–18 |
| R18 operations | 1, 16–18 |
| R19 responsive/accessibility | 2, 5–12, 14–15, 18 |
| R20 verification | Every task, final integration in 18 |

## Plan self-review and handoff

- All R01–R20 requirements have owning tasks; no excluded payment or tenancy tasks were introduced.
- Module contracts are named, file paths have explicit expansions, and each task has a verification cycle and independent deliverable.
- Race conditions, Unicode/timezone/export input, job crash recovery and grade correction have designated tests.
- Grade projection and registered media policies avoid synchronous module dependency cycles.
- This document is an implementation plan, not implemented software. No build, test, deployment, capacity or recovery success is claimed.

Review the written specification and this plan before implementation. Recommended execution is **native, milestone by milestone**, because the class/course/assessment interfaces are tightly coupled and the user has asked to establish the architecture before coding. The alternative is subagent-driven execution with separate task/review contexts. Do not begin either method until the user has reviewed these artifacts and selected the approach. No external publishing, marketplace submission or deployment is authorized by this planning handoff.
