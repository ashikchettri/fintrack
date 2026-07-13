import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

// jsdom has no matchMedia; ThemeProvider needs it (defaults to light)
vi.stubGlobal(
  'matchMedia',
  vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
);
