#!/usr/bin/env bash
# FinTrack local dev stack — one command to check & start everything.
#
#   ./dev.sh          start whatever isn't running (idempotent)
#   ./dev.sh status   report what's up without starting anything
#   ./dev.sh stop     stop app processes and pause the containers
#   ./dev.sh resend   restart the backend sending REAL email via Resend
#   ./dev.sh gmail    restart the backend sending REAL email via Gmail SMTP
#   ./dev.sh mailpit  restart the backend sending to the local Mailpit inbox
#
# Plain start always uses Mailpit (safe default); real-email modes are explicit.
#
# Services: Postgres + Mailpit (docker compose), auth-service (:8081),
# frontend dev server (:5173). Logs land in .dev-logs/.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/.dev-logs"
mkdir -p "$LOG_DIR"

# ---------- environment ----------------------------------------------------
# .env drives ports/credentials (git-ignored; see .env.example)
if [ -f "$ROOT/.env" ]; then
  set -a; . "$ROOT/.env"; set +a
fi
PG_PORT="${POSTGRES_HOST_PORT:-5432}"

# Apple Silicon: force native images even if the shell exports amd64
export DOCKER_DEFAULT_PLATFORM=linux/arm64

# nvm (frontend needs Node 22 per .nvmrc)
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"

GREEN='\033[0;32m'; YELLOW='\033[0;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { printf "${GREEN}  ✓ %s${NC}\n" "$1"; }
warn() { printf "${YELLOW}  … %s${NC}\n" "$1"; }
fail() { printf "${RED}  ✗ %s${NC}\n" "$1"; }

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t >/dev/null 2>&1; }

# ---------- checks ----------------------------------------------------------
docker_up()   { docker info >/dev/null 2>&1; }
postgres_up() { docker exec fintrack-postgres pg_isready -U "${POSTGRES_USER:-fintrack}" -q 2>/dev/null; }
mailpit_up()  { curl -sf http://localhost:8025/api/v1/info >/dev/null 2>&1; }
backend_up()  { curl -sf http://localhost:8081/actuator/health 2>/dev/null | grep -q UP; }
frontend_up() { port_in_use 5173; }

status() {
  echo "FinTrack dev stack status:"
  docker_up   && ok "Docker daemon"              || fail "Docker daemon (start Docker Desktop)"
  postgres_up && ok "Postgres      :$PG_PORT"    || fail "Postgres"
  mailpit_up  && ok "Mailpit       :1025 / UI :8025" || fail "Mailpit"
  backend_up  && ok "auth-service  :8081 (Swagger: /swagger-ui.html)" || fail "auth-service"
  frontend_up && ok "frontend      :5173"        || fail "frontend"
  if backend_up && [ -f "$LOG_DIR/auth-service.log" ]; then
    transport=$(grep "Email transport" "$LOG_DIR/auth-service.log" | tail -1 | sed 's/.*Email transport: //')
    [ -n "$transport" ] && echo "  ↳ email transport: $transport"
  fi
}

start_backend() {
  # $1: optional MAIL_PROVIDER override (empty = auto chain → Mailpit locally)
  local provider="${1:-${MAIL_PROVIDER:-}}"
  warn "starting auth-service${provider:+ (MAIL_PROVIDER=$provider)} — first compile can take a minute…"
  (cd "$ROOT/services/auth-service" \
    && DB_PORT="$PG_PORT" MAIL_PROVIDER="$provider" nohup ./gradlew bootRun --console=plain -q \
       > "$LOG_DIR/auth-service.log" 2>&1 & echo $! > "$LOG_DIR/auth-service.pid")
  for _ in $(seq 1 60); do backend_up && break; sleep 3; done
  backend_up && ok "auth-service up on :8081" \
    || { fail "auth-service failed — see .dev-logs/auth-service.log"; exit 1; }
}

stop_backend() {
  if [ -f "$LOG_DIR/auth-service.pid" ]; then
    pkill -P "$(cat "$LOG_DIR/auth-service.pid")" 2>/dev/null || true
    kill "$(cat "$LOG_DIR/auth-service.pid")" 2>/dev/null || true
    rm -f "$LOG_DIR/auth-service.pid"
  fi
  port_in_use 8081 && lsof -nP -iTCP:8081 -sTCP:LISTEN -t | xargs kill 2>/dev/null || true
  for _ in $(seq 1 10); do port_in_use 8081 || break; sleep 1; done
}

# restart just the backend with an explicit email transport
switch_mail() {
  local provider="$1"
  if [ "$provider" = "resend" ] && ! grep -qE "^RESEND_API_KEY=.+" "$ROOT/.env" 2>/dev/null; then
    fail "RESEND_API_KEY missing in .env — required for the resend transport"
    exit 1
  fi
  if [ "$provider" = "gmail" ]; then
    if ! grep -qE "^MAIL_PASSWORD=.+" "$ROOT/.env" 2>/dev/null; then
      fail "MAIL_PASSWORD missing in .env — Gmail needs a Google App Password:"
      echo "     1. Google Account → Security → enable 2-Step Verification"
      echo "     2. Security → App passwords → create one for 'Mail'"
      echo "     3. Put the 16-char value in .env as MAIL_PASSWORD=..."
      exit 1
    fi
    # Gmail SMTP settings (username/password come from .env)
    export MAIL_HOST=smtp.gmail.com MAIL_PORT=587 MAIL_SMTP_AUTH=true MAIL_SMTP_STARTTLS=true
    provider=smtp
  fi
  backend_up && { warn "restarting auth-service with MAIL_PROVIDER=${provider}..."; stop_backend; }
  start_backend "$provider"
  case "$1" in
    resend)
      echo ""
      echo "  Real email via Resend: until a domain is verified, delivery only"
      echo "  works to the Resend account owner's address. Everything else 500s." ;;
    gmail)
      echo ""
      echo "  Real email via Gmail: delivers to ANY address (≈500/day limit)." ;;
    *)
      echo ""
      echo "  Local inbox mode: all email lands at http://localhost:8025" ;;
  esac
}

# ---------- start -----------------------------------------------------------
start() {
  echo "FinTrack dev stack — checking services…"

  if ! docker_up; then
    fail "Docker daemon is not running — start Docker Desktop first"
    exit 1
  fi
  ok "Docker daemon"

  if postgres_up; then
    ok "Postgres already running on :$PG_PORT"
  else
    warn "starting Postgres…"
    (cd "$ROOT" && docker compose up -d postgres >/dev/null 2>&1)
    for _ in $(seq 1 30); do postgres_up && break; sleep 2; done
    postgres_up && ok "Postgres up on :$PG_PORT" || { fail "Postgres failed to start"; exit 1; }
  fi

  if mailpit_up; then
    ok "Mailpit already running (inbox: http://localhost:8025)"
  else
    warn "starting Mailpit…"
    (cd "$ROOT" && docker compose up -d mailpit >/dev/null 2>&1)
    for _ in $(seq 1 15); do mailpit_up && break; sleep 1; done
    mailpit_up && ok "Mailpit up (inbox: http://localhost:8025)" || { fail "Mailpit failed to start"; exit 1; }
  fi

  if backend_up; then
    ok "auth-service already running on :8081"
  else
    # explicit mailpit: even with Gmail/Resend credentials in .env, a plain
    # start must never send real email
    start_backend mailpit
  fi

  if frontend_up; then
    ok "frontend already running on :5173"
  else
    if ! command -v node >/dev/null; then
      fail "node not found — install nvm / Node 22"; exit 1
    fi
    if [ ! -d "$ROOT/frontend/node_modules" ]; then
      warn "installing frontend dependencies…"
      (cd "$ROOT/frontend" && npm install --silent >/dev/null)
    fi
    warn "starting frontend…"
    (cd "$ROOT/frontend" \
      && nohup npm run dev -- --port 5173 --strictPort \
         > "$LOG_DIR/frontend.log" 2>&1 & echo $! > "$LOG_DIR/frontend.pid")
    for _ in $(seq 1 20); do frontend_up && break; sleep 1; done
    frontend_up && ok "frontend up on :5173" \
      || { fail "frontend failed — see .dev-logs/frontend.log"; exit 1; }
  fi

  echo ""
  echo "All green. Open:"
  echo "  App        http://localhost:5173"
  echo "  Swagger    http://localhost:8081/swagger-ui.html"
  echo "  Mail inbox http://localhost:8025"
}

# ---------- stop ------------------------------------------------------------
stop() {
  echo "Stopping FinTrack dev stack…"
  stop_backend
  if [ -f "$LOG_DIR/frontend.pid" ]; then
    pkill -P "$(cat "$LOG_DIR/frontend.pid")" 2>/dev/null || true
    kill "$(cat "$LOG_DIR/frontend.pid")" 2>/dev/null || true
    rm -f "$LOG_DIR/frontend.pid"
  fi
  # catch processes not started by this script (e.g. manual npm run dev)
  port_in_use 5173 && lsof -nP -iTCP:5173 -sTCP:LISTEN -t | xargs kill 2>/dev/null || true
  (cd "$ROOT" && docker compose stop postgres mailpit >/dev/null 2>&1) || true
  ok "stopped (containers paused, not removed — data kept)"
}

case "${1:-start}" in
  start)   start ;;
  status)  status ;;
  stop)    stop ;;
  resend)  switch_mail resend ;;
  gmail)   switch_mail gmail ;;
  mailpit) switch_mail mailpit ;;
  *) echo "usage: ./dev.sh [start|status|stop|resend|gmail|mailpit]"; exit 1 ;;
esac
