# frontend

React 19 + Vite + TypeScript SPA for FinTrack — the full app: auth, dashboard, budgets, home loan, income & cash flow, and household sharing.

## Stack

- **Vite · React 19 · TypeScript**
- **Tailwind CSS v4** + shadcn-style components (owned in `src/components/ui/`)
- **TanStack Query** for server state
- **react-hook-form + zod** for forms (schemas in `src/validators/`)
- **Sonner** toasts · **light/dark theme** (`ThemeProvider`, persisted, follows the OS)
- Access token in JS memory only; refresh token in an httpOnly cookie (ADR 003) — sessions survive reloads via silent refresh
- Dev server proxies `/api` → the services (auth `:8081` / finance `:8082`); the production entry point is the gateway (`:8080`)

## Pages

**Public:** `/signup` · `/verify-email` · `/login` · `/forgot-password` · `/reset-password` · `/join` (accept a household invite).
**Protected:** `/dashboard` (overview: totals + AI summary + cash-flow/home-loan/budget rollups) · `/bank` (imported statement detail: charts, merchants, recent, import, sharing) · `/insights` (AI summary + Q&A) · `/cash-flow` · `/home-loan` (+ payoff calculator) · `/net-worth` (assets/liabilities → net worth) · `/budget` (income & expenses) · `/profile` (household roster + invites) · `/settings`.

The API client (`src/api/client.ts`) attaches the bearer token, does one silent refresh on a 401, and parses RFC 9457 problem bodies into a typed `ApiError`.

## Run

```bash
nvm use                 # Node 22 (.nvmrc)
npm install
npm run dev             # http://localhost:5173 — needs auth-service on :8081
```

Easier: `./dev.sh` from the repo root starts the whole stack.

## Test

| Command | What it runs |
|---|---|
| `npm run test:coverage` | Vitest + React Testing Library, with a coverage gate |
| `npm run test:ui` | Playwright against a **mocked** API (no backend) |
| `npm run test:e2e` | Playwright against the **real** stack — run `./dev.sh mailpit` first (e2e reads codes from the Mailpit inbox) |
