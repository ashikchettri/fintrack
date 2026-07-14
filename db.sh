#!/usr/bin/env bash
# FinTrack local database helper — talks to the Docker Postgres via `docker exec`,
# so it works regardless of the host port mapping (no 5432/5433 juggling).
#
#   ./db.sh                    open an interactive psql shell
#   ./db.sh tables             list tables in the auth + finance schemas
#   ./db.sh describe <table>   show a table's columns (e.g. auth.users)
#   ./db.sh users              recent users (email, verified?, created)
#   ./db.sh user <email>       full detail for one user (household, role, sessions)
#   ./db.sh query "<SQL>"      run any SQL (SELECT or UPDATE)
#   ./db.sh help               show the examples below
#
# Requires Postgres running (./dev.sh). See docs/DATABASE.md for more examples.

set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
[ -f "$ROOT/.env" ] && { set -a; . "$ROOT/.env"; set +a; }

CONTAINER=fintrack-postgres
DB_NAME="${POSTGRES_DB:-fintrack}"
DB_USER="${POSTGRES_USER:-fintrack}"
# resolve the schema search path so the auth (and finance) tables are visible
# without a prefix — no more "connected to template1, see no tables" confusion
export_search_path='PGOPTIONS=-c search_path=auth,finance,public'

if ! docker exec "$CONTAINER" pg_isready -U "$DB_USER" -q 2>/dev/null; then
  echo "Postgres isn't running. Start it with ./dev.sh (or: docker compose up -d postgres)."
  exit 1
fi

# non-interactive psql; always the fintrack DB, auth schema on the path
psql_run() {
  docker exec -i -e "$export_search_path" "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

examples() {
  cat <<'EOF'
FinTrack database helper — examples

  Inspect schema:
    ./db.sh tables
    ./db.sh describe auth.users
    ./db.sh describe auth.refresh_tokens

  Read data:
    ./db.sh users
    ./db.sh user jane@example.com
    ./db.sh query "select email, email_verified_at from auth.users order by created_at desc limit 10"
    ./db.sh query "select role, count(*) from auth.household_members group by role"

  Update data (dev only — be careful):
    # manually verify an account (skip the emailed code)
    ./db.sh query "update auth.users set email_verified_at = now() where email = 'jane@example.com'"

    # revoke a user's sessions (force re-login)
    ./db.sh query "update auth.refresh_tokens set revoked_at = now()
                   where user_id = (select id from auth.users where email = 'jane@example.com')
                   and revoked_at is null"

    # clear a pending verification/reset code (let the user request a fresh one)
    ./db.sh query "delete from auth.email_verification_codes
                   where user_id = (select id from auth.users where email = 'jane@example.com')"

  Interactive shell (full psql — \dt, \d, \q to quit):
    ./db.sh
EOF
}

case "${1:-shell}" in
  shell|psql)
    echo "Connected to '$DB_NAME' with search_path=auth,finance — plain \\dt shows the auth tables."
    docker exec -it -e "$export_search_path" "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME"
    ;;

  tables)
    # information_schema handles empty schemas cleanly (finance is empty until phase 2)
    psql_run -c "select table_schema, table_name
                 from information_schema.tables
                 where table_schema in ('auth', 'finance')
                   and table_name <> 'flyway_schema_history'
                 order by table_schema, table_name;"
    ;;

  describe)
    [ -n "${2:-}" ] || { echo "usage: ./db.sh describe <schema.table>  (e.g. auth.users)"; exit 1; }
    psql_run -c "\\d ${2}"
    ;;

  users)
    psql_run -c "select email,
                        (email_verified_at is not null) as verified,
                        created_at
                 from auth.users
                 order by created_at desc
                 limit 50;"
    ;;

  user)
    [ -n "${2:-}" ] || { echo "usage: ./db.sh user <email>"; exit 1; }
    # email passed as a psql variable so quoting is safe
    psql_run -v email="$2" <<'SQL'
\echo '--- account ---'
select u.id, u.email, u.email_verified_at, u.created_at,
       h.name as household, m.role
from auth.users u
left join auth.household_members m on m.user_id = u.id
left join auth.households h on h.id = m.household_id
where u.email = lower(:'email');
\echo '--- active sessions ---'
select count(*) as active_refresh_tokens
from auth.refresh_tokens rt
join auth.users u on u.id = rt.user_id
where u.email = lower(:'email')
  and rt.revoked_at is null and rt.expires_at > now();
SQL
    ;;

  query)
    [ -n "${2:-}" ] || { echo "usage: ./db.sh query \"<SQL>\""; exit 1; }
    psql_run -c "$2"
    ;;

  help|--help|-h)
    examples
    ;;

  *)
    echo "unknown command '$1'"; echo; examples; exit 1
    ;;
esac
