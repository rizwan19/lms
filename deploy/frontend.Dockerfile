# Build context: repository root.  docker build -f deploy/frontend.Dockerfile .
FROM node:22-bookworm-slim AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2.10-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist /srv
