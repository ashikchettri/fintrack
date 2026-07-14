import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ProfilePage from './ProfilePage';
import { ApiError, api } from '../api/client';
import { TEST_USER, renderPageWithDestinations } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      signup: vi.fn(),
      login: vi.fn(),
      refresh: vi.fn().mockResolvedValue(false),
      logout: vi.fn(),
      me: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('ProfilePage', () => {
  it('renders the profile from /users/me', async () => {
    mockedApi.me.mockResolvedValue(TEST_USER);
    renderPageWithDestinations(<ProfilePage />, '/profile', { '/login': 'LOGIN_DEST' });

    await waitFor(() =>
      expect(screen.getByTestId('profile-email')).toHaveTextContent('jane@example.com'),
    );
    expect(screen.getByTestId('profile-household')).toHaveTextContent("jane's household");
    expect(screen.getByTestId('profile-role')).toHaveTextContent('OWNER');
  });

  it('logs out and navigates to login', async () => {
    mockedApi.me.mockResolvedValue(TEST_USER);
    mockedApi.logout.mockResolvedValue(undefined);
    renderPageWithDestinations(<ProfilePage />, '/profile', { '/login': 'LOGIN_DEST' });
    await waitFor(() => expect(screen.getByTestId('profile-email')).toBeInTheDocument());

    await userEvent.setup().click(screen.getByRole('button', { name: 'Log out' }));

    await waitFor(() => expect(screen.getByText('LOGIN_DEST')).toBeInTheDocument());
    expect(mockedApi.logout).toHaveBeenCalled();
  });

  it('navigates to account settings', async () => {
    mockedApi.me.mockResolvedValue(TEST_USER);
    renderPageWithDestinations(<ProfilePage />, '/profile', { '/settings': 'SETTINGS_DEST' });
    await waitFor(() => expect(screen.getByTestId('profile-email')).toBeInTheDocument());

    await userEvent.setup().click(screen.getByRole('button', { name: 'Account settings' }));

    await waitFor(() => expect(screen.getByText('SETTINGS_DEST')).toBeInTheDocument());
  });

  it('shows an error state when the profile cannot load', async () => {
    mockedApi.me.mockRejectedValue(new ApiError(401, { status: 401 }));
    renderPageWithDestinations(<ProfilePage />, '/profile', { '/login': 'LOGIN_DEST' });

    await waitFor(() =>
      expect(screen.getByText('Could not load your profile.')).toBeInTheDocument(),
    );
  });
});
