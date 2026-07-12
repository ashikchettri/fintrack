#!/usr/bin/env bash
# FinTrack local dev stack — one command to check & start everything.
#
#   ./dev.sh          start whatever isn't running (idempotent)
#   ./dev.sh status   report what's up without starting anything
#   ./dev.sh stop     stop app processes and pause the containers
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
    warn "starting auth-service (first compile can take a minute)…"
    (cd "$ROOT/services/auth-service" \
      && DB_PORT="$PG_PORT" nohup ./gradlew bootRun --console=plain -q \
         > "$LOG_DIR/auth-service.log" 2>&1 & echo $! > "$LOG_DIR/auth-service.pid")
    for _ in $(seq 1 60); do backend_up && break; sleep 3; done
    backend_up && ok "auth-service up on :8081" \
      || { fail "auth-service failed — see .dev-logs/auth-service.log"; exit 1; }
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
  for app in auth-service frontend; do
    if [ -f "$LOG_DIR/$app.pid" ]; then
      pkill -P "$(cat "$LOG_DIR/$app.pid")" 2>/dev/null || true
      kill "$(cat "$LOG_DIR/$app.pid")" 2>/dev/null || true
      rm -f "$LOG_DIR/$app.pid"
    fi
  done
  # catch processes not started by this script (e.g. manual bootRun / npm run dev)
  port_in_use 8081 && lsof -nP -iTCP:8081 -sTCP:LISTEN -t | xargs kill 2>/dev/null || true
  port_in_use 5173 && lsof -nP -iTCP:5173 -sTCP:LISTEN -t | xargs kill 2>/dev/null || true
  (cd "$ROOT" && docker compose stop postgres mailpit >/dev/null 2>&1) || true
  ok "stopped (containers paused, not removed — data kept)"
}

case "${1:-start}" in
  start)  start ;;
  status) status ;;
  stop)   stop ;;
  *) echo "usage: ./dev.sh [start|status|stop]"; exit 1 ;;
esac
