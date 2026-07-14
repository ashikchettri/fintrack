# Working with the local database

The dev Postgres runs in a Docker container (`fintrack-postgres`). `./db.sh` wraps `psql` via `docker exec`, so it works no matter which host port the container is mapped to. Postgres must be running — `./dev.sh` starts it.

Schema-per-service: `auth` (identity) and `finance` (empty until Phase 2). `flyway_schema_history` in each tracks applied migrations.

## The `./db.sh` helper

| Command | Does |
|---|---|
| `./db.sh` | open an interactive `psql` shell (`\dt`, `\d table`, `\q` to quit) |
| `./db.sh tables` | list tables in `auth` + `finance` |
| `./db.sh describe <table>` | show a table's columns/indexes, e.g. `./db.sh describe auth.users` |
| `./db.sh users` | recent users — email, verified?, created |
| `./db.sh user <email>` | one user's detail: household, role, active session count |
| `./db.sh query "<SQL>"` | run any SQL (SELECT or UPDATE) |
| `./db.sh help` | print the examples |

## Checking data — examples

```bash
# who signed up recently, and are they verified?
./db.sh users

# everything about one account
./db.sh user jane@example.com

# raw queries
./db.sh query "select email, email_verified_at from auth.users order by created_at desc limit 10"
./db.sh query "select role, count(*) from auth.household_members group by role"

# a user's live sessions
./db.sh query "select created_at, expires_at, revoked_at from auth.refresh_tokens
               where user_id = (select id from auth.users where email = 'jane@example.com')
               order by created_at desc"
```

## Inspecting a table

```bash
./db.sh describe auth.users
./db.sh describe auth.refresh_tokens
./db.sh describe auth.email_verification_codes
```

## Updating data (dev only — mind the WHERE clause)

```bash
# manually verify an account without the emailed code
./db.sh query "update auth.users set email_verified_at = now() where email = 'jane@example.com'"

# force a user to re-login everywhere (revoke their refresh tokens)
./db.sh query "update auth.refresh_tokens set revoked_at = now()
               where user_id = (select id from auth.users where email = 'jane@example.com')
               and revoked_at is null"

# clear a pending verification/reset code so the user can request a fresh one
./db.sh query "delete from auth.email_verification_codes
               where user_id = (select id from auth.users where email = 'jane@example.com')"
```

> ⚠️ These write to the database directly, bypassing the app. Fine for local dev; never a substitute for a real feature. **Schema changes still go only through Flyway migrations** (`V<n>__description.sql`) — never hand-edit tables to change structure.

## Without `./db.sh` — raw `docker exec` + psql

The helper is just a wrapper. If you'd rather run psql directly (or `db.sh` isn't checked out), these do the same thing. All target the local container `fintrack-postgres`, database `fintrack`.

**Open an interactive shell** (tables live in the `auth` schema, so put it on the search path):

```bash
docker exec -it -e "PGOPTIONS=-c search_path=auth,finance,public" \
  fintrack-postgres psql -U fintrack -d fintrack
```

Then inside psql, no prefix needed:

```sql
\dt                         -- list the auth tables
\d users                    -- describe the users table
select email, email_verified_at, created_at from users order by created_at desc limit 10;
select u.email, h.name as household, m.role
  from users u
  join household_members m on m.user_id = u.id
  join households h on h.id = m.household_id;
\q
```

**One-off queries** (no shell — `-c` runs and exits):

```bash
# list users
docker exec fintrack-postgres psql -U fintrack -d fintrack \
  -c "select email, email_verified_at from auth.users order by created_at desc limit 10;"

# one user's details
docker exec fintrack-postgres psql -U fintrack -d fintrack \
  -c "select email, email_verified_at, created_at from auth.users where email = 'jane@example.com';"

# list tables
docker exec fintrack-postgres psql -U fintrack -d fintrack \
  -c "select table_schema, table_name from information_schema.tables
      where table_schema in ('auth','finance') order by 1,2;"

# manually verify an account (update)
docker exec fintrack-postgres psql -U fintrack -d fintrack \
  -c "update auth.users set email_verified_at = now() where email = 'jane@example.com';"
```

> Without the `PGOPTIONS` search-path trick, always prefix tables with `auth.` (e.g. `auth.users`) in `-c` queries — `-c` doesn't inherit an interactive `SET search_path`.

## Troubleshooting

**"I'm in psql but `\dt` shows no tables."** Two causes:

- **Wrong database.** `\conninfo` — if it says `template1` or `postgres`, you connected without a database. Run `\c fintrack`.
- **Wrong schema.** The tables live in `auth` (and `finance`), not `public`, so a bare `\dt` (which only looks at `public`) shows nothing. Use `\dt auth.*`, or `SET search_path TO auth, finance, public;` then `\dt` works.

`./db.sh` avoids both: it always connects to `fintrack` and sets the search path, so plain `\dt` and unqualified table names (`select * from users`) just work.

## Passwords

`password_hash` is Argon2id (`$argon2id$…`) and can't be reversed. To "set" a password for testing, use the app's signup/reset flow, or the change-password endpoint — not a SQL update.
