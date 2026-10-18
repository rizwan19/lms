# Email delivery and background jobs

## How delivery works

Invitations and password-reset emails are durable jobs (`background_jobs`) inserted in the same
transaction as the account change. The application polls for due jobs every 2 seconds
(`learning.jobs.poll-interval`), claims them with `FOR UPDATE SKIP LOCKED` and a 60-second lease that is
renewed every 20 seconds while a handler runs. A crashed worker's lease expires and another worker
reclaims the job.

Failed executions retry after 1 minute, 5 minutes, 30 minutes and 2 hours. The fifth failed execution is
terminal: the job becomes `FAILED` and appears under **Settings → System status** for administrators
(`GET /api/v1/admin/failed-jobs`). An SMTP failure never undoes the account change that queued it.

## Known limitation: at-least-once email

SMTP has no transactional acknowledgement with the database. If the application crashes after the SMTP
server accepts a message but before the job is marked `SUCCEEDED`, the job is retried and the recipient may
receive a second email. Each delivery attempt rotates the one-time token before sending, so only the most
recent link works; the earlier link reports "invalid or expired". Tokens are never stored in plain text,
including in job payloads.

In-app notifications are deduplicated by key, so retries never show a duplicate notice.

## Without SMTP

SMTP is optional. When `spring.mail.host` is not set:

- administrators see an "Email is not configured" warning on the settings page;
- creating an account with an emailed invitation is rejected; use **Show a temporary password** instead;
- password-reset requests are accepted but send nothing; staff use **Reset password** (assisted reset).

## Configuration

| Setting | Purpose |
| --- | --- |
| `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password` | SMTP server |
| `LEARNING_MAIL_FROM` | Sender address |
| `LEARNING_PUBLIC_ORIGIN` | Origin used in emailed links, e.g. `https://learn.example.org` (required) |
| `learning.jobs.enabled` | Set `false` to pause job claiming (restore drills, maintenance) |
