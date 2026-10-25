# Course-centric backend: breaking API changes

The v2 backend replaces classes with direct course enrollment. It needs a fresh database
(`docs/runbooks/v2-database-reset.md`). The frontend has not been updated yet and will not work against this
backend until it is.

## Removed

| Old route | Replacement |
| --- | --- |
| `/classes/**`, `/classes/{id}/imports/**` | Course access requests, invitations and `/courses/{id}/enrollments` |
| `/users`, `/users/{id}`, `/users/{id}/assisted-reset` | `/auth/register` (public signup), `/workspace/users/**` (approved teachers) |
| `/auth/invitations/accept` (account invitations) | `/auth/email/verify` for new accounts; `/course-invitations/accept` for course access |
| `/institution` | `/workspace` |
| `/courses/{id}/teachers` | None: every course has one immutable author |
| `/exam-assignments/**` | `/courses/{id}/exams`, `/exams/{id}/schedule`, `/exams/{id}/attempts` |
| `/exams?courseId=`, `/exams/{id}/draft`, `/exams/{id}/publish`, `/exams/{id}/editions/{versionId}` | `/courses/{id}/exams`, `GET/PUT/DELETE /exams/{id}`; exams publish with the course edition |
| `/grading?classId=` | `/courses/{id}/grading` |
| `/certificates?classId=` | `/courses/{id}/certificates` |
| `/reports/classes/{id}/**` | `/reports/courses/{id}/**` (optional `versionId` filter) |
| `GET /me/enrollments/{id}` (player) | `GET /me/enrollments/{id}/content` |

## Changed payloads

- Roles: exactly `TEACHER` and `STUDENT`; account status `ACTIVE | SUSPENDED`. `/auth/me` returns `role`,
  `emailVerified`, `teacherStatus` and `capabilities {canAdministerWorkspace, canPublishCourses, canLearn}`
  instead of `roles` and `passwordChangeRequired`.
- `POST /setup` takes `workspaceName` and answers `202 {userId, nextAction: VERIFY_EMAIL}` without a session.
  `GET /setup` returns the bare JSON string `REQUIRED`, `OWNER_VERIFICATION_PENDING` or `ACTIVE`.
- Unverified accounts can sign in but only use `/auth/me`, `/auth/csrf`, `/auth/email/*`, password change, sign-out,
  `/workspace` and the public catalog; everything else answers `403 EMAIL_UNVERIFIED`.
- Exams have no editions of their own: they belong to a course edition like its lessons, are edited in the course draft,
  freeze when it is published and are copied (with new IDs) into the next draft. Course drafts no longer carry
  `requiredExamVersionIds`; an exam of the draft is required when created or rescheduled with `required: true`.
  `PublishedCourse` adds `authorId` and drops `requiredExamVersionIds`.
- Exams carry `mode` (`SELF_PACED | SCHEDULED`), `opensAt`, `closesAt` (scheduled only), `durationMinutes`
  (1-240, required), `maxAttempts`, `resultPolicy`, `feedbackPolicy`. Attempts, learner exams, required results and
  grading snapshots reference `examId`.
- Learner results add `courseId`; certificates add `courseId` and `authorName`; public verification returns
  `workspace` and `learner` instead of `institution` and `student`.
- Reports use `learner_name` columns and `examId` score columns.

## New error codes

`EMAIL_UNVERIFIED`, `TEACHER_APPROVAL_REQUIRED`, `APPLICATION_ALREADY_DECIDED`, `ACCESS_REQUEST_COOLDOWN`,
`INVITATION_EMAIL_MISMATCH`, `REQUIRED_EXAM_CLOSED`, `RATE_LIMITED`, `OWN_COURSE`, `ALREADY_ENROLLED`,
`ENROLLMENT_INACTIVE`, `INVITATION_INVALID`, `INVITATION_UNAVAILABLE`, `INVITATION_STALE`, `EXAM_FROZEN`,
`SELF_SUSPENSION`, `SELF_REVIEW`, `APPLICATION_COOLDOWN`, `REGISTRATION_CLOSED`; `LEGACY_SCHEMA_UNSUPPORTED` stops
startup on a v1 database.

## Frontend follow-up

Signup and email verification pages; teacher application status and review inbox; public catalog; access request,
invitation acceptance and roster screens; exam authoring and scheduling on the course draft; course-scoped grading, reports
and certificates; removal of class and account-provisioning screens.
