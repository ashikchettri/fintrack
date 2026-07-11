/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // Dev-time CORS non-issue: the browser only ever talks to this origin;
    // Vite forwards /api to auth-service. The phase-2 gateway owns this later.
    proxy: {
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
