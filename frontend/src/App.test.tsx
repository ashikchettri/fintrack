import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import App from './App';
import { api } from './api/client';
import { TEST_USER, renderWithProviders } from './test/utils';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    api: {
      signup: vi.fn(),
      login: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
      me: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('App session bootstrap and route guarding', () => {
  it('redirects /profile to /login when no session can be restored', async () => {
    mockedApi.refresh.mockResolvedValue(false);
    renderWithProviders(<App />, ['/profile']);

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument(),
    );
  });

  it('restores the session from the refresh cookie and shows the profile', async () => {
    // simulates a page reload with a live httpOnly cookie (ADR 003 flow)
    mockedApi.refresh.mockResolvedValue(true);
    mockedApi.me.mockResolvedValue(TEST_USER);
    renderWithProviders(<App />, ['/profile']);

    await waitFor(() =>
      expect(screen.getByTestId('profile-email')).toHaveTextContent('jane@example.com'),
    );
  });

  it('sends unknown routes to the root redirect chain', async () => {
    mockedApi.refresh.mockResolvedValue(false);
    renderWithProviders(<App />, ['/no-such-page']);

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument(),
    );
  });
});
