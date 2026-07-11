<!-- Title must follow conventional commits: feat|fix|chore|docs|test|refactor|ci(scope): summary -->

## What & why

<!-- 2-3 sentences: what this PR does and why. Link the roadmap phase / ADR if relevant. -->

## Checklist (project conventions — see CLAUDE.md)

- [ ] **Tests included** — unit + Testcontainers integration; a feature without tests is not done
- [ ] **Schema changes are new Flyway migrations only** (`V<n>__description.sql`) — no edits to applied migrations, no `ddl-auto=update`
- [ ] **DTOs are records at the boundary** — no JPA entities exposed from controllers
- [ ] **Errors are RFC 9457 `ProblemDetail`** — no ad-hoc error bodies
- [ ] **API paths under `/api/v1/...`**
- [ ] **No secrets in the diff** — config via env vars / Spring profiles
- [ ] **Money handled as `NUMERIC(19,4)` + currency code** (if applicable) — never float/double
- [ ] **Tables scoped by `household_id` + `member_id`** (if adding tables); transactions default `visibility = personal` (ADR 001)
- [ ] **Significant decisions recorded** as ADR in `docs/decisions/` (if applicable)
