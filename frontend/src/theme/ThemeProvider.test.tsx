import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
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
});
