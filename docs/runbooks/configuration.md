# Configuration

All settings are environment variables (Docker: `.env`; VPS: `/etc/learning-portal/learning.env`, mode 600).
The application fails to start when a required value is missing.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `LEARNING_DB_URL` | yes | — | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/learning` |
| `LEARNING_DB_USERNAME` / `LEARNING_DB_PASSWORD` | yes | — | Database credentials |
| `LEARNING_PUBLIC_ORIGIN` | yes | — | Canonical origin used in emailed links and certificate QR codes |
| `LEARNING_SECURE_COOKIES` | no | `true` | Keep `true` behind HTTPS; `false` only for local HTTP testing |
| `LEARNING_BIND_ADDRESS` | no | `0.0.0.0` | Application HTTP bind address (`127.0.0.1` on a VPS behind Caddy) |
| `LEARNING_PORT` | no | `8080` | Application HTTP port |
| `LEARNING_MANAGEMENT_ADDRESS` / `LEARNING_MANAGEMENT_PORT` | no | `127.0.0.1` / `8081` | Internal health endpoints (`/actuator/health/liveness`, `/readiness`) |
| `LEARNING_STORAGE_MODE` | no | `local` | `local` or `s3` |
| `LEARNING_MEDIA_ROOT` / `LEARNING_STAGING_DIR` | local mode | `data/media` / `data/staging` | Private media and upload staging directories (same filesystem) |
| `LEARNING_S3_ENDPOINT`, `_REGION`, `_BUCKET`, `_ACCESS_KEY`, `_SECRET_KEY`, `_PATH_STYLE` | s3 mode | — | Private bucket; the bucket must exist and must not be public |
| `LEARNING_MEDIA_ORIGIN` | s3 mode | empty | S3 origin allowed by the proxy CSP for presigned media |
| `LEARNING_FFPROBE` | no | `ffprobe` | Path to ffprobe (FFmpeg) for video validation |
| `SPRING_MAIL_HOST`, `_PORT`, `_USERNAME`, `_PASSWORD` | no | unset | SMTP; without it, use temporary passwords and assisted resets |
| `LEARNING_MAIL_FROM` | no | empty | Sender address |
| `LEARNING_JOBS_ENABLED` | no | `true` | Set `false` to pause background jobs (restore drills, maintenance) |
| `LEARNING_REPORTING_INACTIVITY_DAYS` | no | `7` | Inactivity flag threshold, 1-90 days |

Upload limits (`learning.media.max-video-size` 500MB, `max-document-size` 25MB, `max-image-size` 5MB) and login
limits (`learning.identity.login-attempts-per-account` 5, `login-attempts-per-ip` 50, `rate-limit-window` 15m) can be
overridden with the matching `LEARNING_MEDIA_MAX_VIDEO_SIZE`-style environment variables.

## S3 notes

- Objects are stored without public ACLs; downloads use presigned GET URLs valid for 60 seconds, issued only after
  the application authorizes the reader. A URL already issued keeps working until it expires, even if access is
  withdrawn meanwhile.
- Text documents are read by the browser through the presigned URL, so the bucket needs a CORS rule allowing `GET`
  from `LEARNING_PUBLIC_ORIGIN`.
- External YouTube/Vimeo embeds are hosted by those providers and cannot be made private by the application.
