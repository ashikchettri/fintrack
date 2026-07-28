# syntax=docker/dockerfile:1

# ── Build the SPA ─────────────────────────────────────────────────────────────
# Context is the repo root (matches the service images); only frontend/ is used.
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ── Serve static files ────────────────────────────────────────────────────────
# nginx-unprivileged runs as non-root (uid 101) and listens on 8080 — no root,
# no cap changes, plays well with K8s runAsNonRoot.
FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
