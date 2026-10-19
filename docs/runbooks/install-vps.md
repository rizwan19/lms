# Manual installation on an Ubuntu 24.04 VPS

Uses systemd, PostgreSQL 17 from the PostgreSQL apt repository, and Caddy for HTTPS. Commands run as root.

## 1. Packages

```bash
apt-get update
apt-get install -y openjdk-21-jre-headless ffmpeg curl gnupg ca-certificates debian-keyring debian-archive-keyring apt-transport-https
install -d /usr/share/postgresql-common/pgdg
curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc
echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt noble-pgdg main" \
  > /etc/apt/sources.list.d/pgdg.list
curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
apt-get update && apt-get install -y postgresql-17 caddy
```

## 2. Database

```bash
DB_PASSWORD=$(openssl rand -base64 32)
sudo -u postgres psql -c "create role learning login password '$DB_PASSWORD'"
sudo -u postgres createdb -O learning learning
```

## 3. Application

Build on a workstation (`cd backend && ./mvnw -DskipTests package`, `cd frontend && npm ci && npm run build`) and copy
`backend/target/learning-portal-*.jar` and `frontend/dist/` to the server.

```bash
useradd --system --home-dir /opt/learning-portal --shell /usr/sbin/nologin learning
install -d -o learning -g learning /opt/learning-portal /var/lib/learning-portal/media /var/lib/learning-portal/staging
install -o learning -g learning -m 644 learning-portal-*.jar /opt/learning-portal/app.jar
install -d /etc/learning-portal
cat > /etc/learning-portal/learning.env <<ENV
LEARNING_DB_URL=jdbc:postgresql://localhost:5432/learning
LEARNING_DB_USERNAME=learning
LEARNING_DB_PASSWORD=$DB_PASSWORD
LEARNING_PUBLIC_ORIGIN=https://learn.example.org
LEARNING_SETUP_TOKEN=$(openssl rand -base64 32)
LEARNING_BIND_ADDRESS=127.0.0.1
LEARNING_MEDIA_ROOT=/var/lib/learning-portal/media
LEARNING_STAGING_DIR=/var/lib/learning-portal/staging
ENV
chmod 600 /etc/learning-portal/learning.env
cp deploy/learning-portal.service /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now learning-portal
curl -fsS http://127.0.0.1:8081/actuator/health/readiness
```

## 4. Front door

```bash
install -d /var/www/learning-portal && cp -r dist/* /var/www/learning-portal/
cp deploy/Caddyfile.vps /etc/caddy/Caddyfile   # replace learn.example.org
systemctl reload caddy
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable
```

Ports 8080 and 8081 listen on 127.0.0.1 only. Open the site, use the setup token from `learning.env`, then delete
the `LEARNING_SETUP_TOKEN` line and `systemctl restart learning-portal`.

## Verification status

Steps 1-3 were rehearsed in an isolated `ubuntu:24.04` container (no systemd, so the service was started with the
unit's `ExecStart` and environment); steps 4 (Caddy/TLS/firewall) were not exercised there. See
[../verification-results.md](../verification-results.md).
