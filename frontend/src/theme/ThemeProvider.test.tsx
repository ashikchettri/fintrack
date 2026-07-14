import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from './ThemeProvider';
import { ThemeToggle } from '@/components/ThemeToggle';

function ThemeProbe() {
  const { theme, resolved } = useTheme();
  return <span data-testid="probe">{theme}:{resolved}</span>;
}

beforeEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
});

afterEach(() => {
  localStorage.clear();
});

describe('ThemeProvider', () => {
  it('defaults to system (resolved light via stubbed matchMedia) and stamps <html>', () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('probe')).toHaveTextContent('system:light');
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('toggle switches to dark, persists, and updates the applied token set', async () => {
    render(
      <ThemeProvider>
        <ThemeToggle />
        <ThemeProbe />
      </ThemeProvider>,
    );

    await userEvent.setup().click(screen.getByRole('button', { name: /switch to dark theme/i }));

    expect(screen.getByTestId('probe')).toHaveTextContent('dark:dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('fintrack-theme')).toBe('dark');
  });

  it('restores a persisted explicit theme on mount', () => {
    localStorage.setItem('fintrack-theme', 'dark');
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('probe')).toHaveTextContent('dark:dark');
  });

  it('follows an OS theme change while on system', async () => {
    // capture the media-query change listener so the test can fire it
    let capturedListener: (() => void) | null = null;
    let prefersDark = false;
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation((query: string) => ({
        get matches() {
          return prefersDark;
        },
        media: query,
        addEventListener: (_: string, cb: () => void) => {
          capturedListener = cb;
        },
        removeEventListener: vi.fn(),
      })),
    );

    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('probe')).toHaveTextContent('system:light');

    // OS flips to dark → provider follows without changing the stored preference
    prefersDark = true;
    await act(async () => {
      capturedListener?.();
    });
    expect(screen.getByTestId('probe')).toHaveTextContent('system:dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
  });
});
