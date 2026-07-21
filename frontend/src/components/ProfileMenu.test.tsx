import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileMenu } from './ProfileMenu';
import { api } from '../api/client';
import { TEST_USER, renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(true), me: vi.fn(), logout: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  // restore a live session so useAuth exposes TEST_USER
  mockedApi.refresh.mockResolvedValue(true);
  mockedApi.me.mockResolvedValue(TEST_USER);
});

describe('ProfileMenu', () => {
  it('opens on click and shows identity + account actions', async () => {
    renderWithProviders(<ProfileMenu />);

    // avatar shows the email initial and the menu is closed
    await waitFor(() => expect(screen.getByRole('button', { name: /account menu/i }))
      .toHaveTextContent('J')); // jane@example.com
    expect(screen.queryByRole('link', { name: 'Profile' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /account menu/i }));

    expect(screen.getByText('jane@example.com')).toBeInTheDocument();
    expect(screen.getByText(/jane's household/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Profile' })).toHaveAttribute('href', '/profile');
    expect(screen.getByRole('link', { name: 'Household' })).toHaveAttribute('href', '/profile');
    expect(screen.getByRole('link', { name: 'Account settings' })).toHaveAttribute('href', '/settings');
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
  });

  it('logs out from the menu', async () => {
    mockedApi.logout.mockResolvedValue(undefined as never);
    renderWithProviders(<ProfileMenu />);

    await waitFor(() => expect(screen.getByRole('button', { name: /account menu/i })).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: /account menu/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));

    await waitFor(() => expect(mockedApi.logout).toHaveBeenCalled());
  });

  it('closes when a menu item is chosen', async () => {
    renderWithProviders(<ProfileMenu />);
    await waitFor(() => expect(screen.getByRole('button', { name: /account menu/i })).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: /account menu/i }));
    await userEvent.click(screen.getByRole('link', { name: 'Profile' }));

    await waitFor(() => expect(screen.queryByRole('link', { name: 'Profile' })).not.toBeInTheDocument());
  });
});
