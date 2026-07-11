import { defineConfig, devices } from '@playwright/test';

/**
 * Two projects:
 *  - mocked: page.route() intercepts /api — fast, deterministic, no backend
 *  - e2e:    real auth-service on :8081 (Vite dev proxy forwards /api)
 *            → npm run backend must be up first (see README)
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  fullyParallel: true,
  reporter: process.env.CI ? 'github' : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
  projects: [
    { name: 'mocked', testDir: './tests/mocked', use: { ...devices['Desktop Chrome'] } },
    { name: 'e2e', testDir: './tests/e2e', use: { ...devices['Desktop Chrome'] } },
  ],
});
