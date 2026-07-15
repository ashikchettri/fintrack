/// <reference types="vitest/config" />
import path from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // Dev-time CORS non-issue: the browser only ever talks to this origin;
    // Vite forwards /api to the services. The phase-2 gateway owns this split later.
    // Finance-service (:8082) owns the finance endpoints; everything else is auth (:8081).
    // More specific keys must come first — Vite matches in insertion order.
    proxy: {
      '/api/v1/dashboard': 'http://localhost:8082',
      '/api/v1/transactions': 'http://localhost:8082',
      // finance's shared view is /household/shared (singular); the trailing
      // path keeps it from swallowing auth's /households/** (plural, :8081)
      '/api/v1/household/shared': 'http://localhost:8082',
      '/api/v1/household/home-loan': 'http://localhost:8082',
      '/api/v1/household/income': 'http://localhost:8082',
      '/api/v1/imports': 'http://localhost:8082',
      '/api/v1/accounts': 'http://localhost:8082',
      '/api': 'http://localhost:8081',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],   // tests/ belongs to Playwright
    setupFiles: './src/test/setup.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      // main.tsx is bootstrap-only; test plumbing excluded from its own metric
      exclude: ['src/main.tsx', 'src/test/**', 'src/vite-env.d.ts'],
      thresholds: {
        lines: 90,
        functions: 90,
        branches: 85,
        statements: 90,
      },
    },
  },
});
