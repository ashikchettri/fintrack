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

## Passwords

`password_hash` is Argon2id (`$argon2id$…`) and can't be reversed. To "set" a password for testing, use the app's signup/reset flow, or the change-password endpoint — not a SQL update.
