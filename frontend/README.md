# frontend

React 19 + Vite + TypeScript SPA. Auth flows only for now (signup, login,
profile, logout) — finance pages come with phase 2's finance-service.

## Stack

- **Vite + React 19 + TypeScript**, TanStack Query for server state
- Access token in JS memory only; refresh token in an **httpOnly cookie**
  (ADR 003) — sessions survive reloads via silent refresh
- Dev server proxies `/api` → `http://localhost:8081` (no CORS config needed)

## Run

```bash
nvm use                 # Node 22 (.nvmrc)
npm install
npm run dev             # http://localhost:5173 — needs auth-service on :8081
```

## Test

| Command | What it runs |
|---|---|
| `npm test` | Vitest + React Testing Library unit/component tests |
| `npm run test:coverage` | same, with v8 coverage gate (90% lines/statements) |
| `npm run test:ui` | Playwright against a **mocked** API (no backend needed) |
| `npm run test:e2e` | Playwright against the **real** stack — start it first: `docker compose up -d postgres` and `./gradlew bootRun` in `services/auth-service` |

Playwright starts the Vite dev server itself (`webServer` in `playwright.config.ts`).
