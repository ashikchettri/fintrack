import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import JoinHouseholdPage from './JoinHouseholdPage';
import { ApiError, api } from '../api/client';
import { TEST_USER, renderPageWithDestinations } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      login: vi.fn(),
      acceptInvite: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

async function fill() {
  await userEvent.type(screen.getByLabelText('Your name'), 'Ashik');
  await userEvent.type(screen.getByLabelText('Email'), 'partner@example.com');
  await userEvent.type(screen.getByLabelText('Invite code'), '123456');
  await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery staple');
  await userEvent.click(screen.getByRole('button', { name: 'Join household' }));
}

describe('JoinHouseholdPage', () => {
  it('accepts the invite, logs in, and lands on the dashboard', async () => {
    mockedApi.acceptInvite.mockResolvedValue({
      userId: 'u1', email: 'partner@example.com', householdId: 'h1',
      householdName: "jane's household", role: 'ADULT', createdAt: '2026-07-15T00:00:00Z',
    });
    mockedApi.login.mockResolvedValue({ accessToken: 'jwt', tokenType: 'Bearer', expiresInSeconds: 900 });
    mockedApi.me.mockResolvedValue(TEST_USER);
    renderPageWithDestinations(<JoinHouseholdPage />, '/join', { '/dashboard': 'DASHBOARD_DEST' });

    await fill();

    await waitFor(() =>
      expect(mockedApi.acceptInvite).toHaveBeenCalledWith(
        'partner@example.com', '123456', 'correct horse battery staple', 'Ashik'),
    );
    await waitFor(() => expect(screen.getByText('DASHBOARD_DEST')).toBeInTheDocument());
  });

  it('shows the problem detail when the code is wrong', async () => {
    mockedApi.acceptInvite.mockRejectedValue(
      new ApiError(400, { title: 'Invalid invitation', status: 400, detail: 'Invalid or expired invitation code' }),
    );
    renderPageWithDestinations(<JoinHouseholdPage />, '/join', { '/dashboard': 'DASHBOARD_DEST' });

    await fill();

    await waitFor(() =>
      expect(screen.getByText('Invalid or expired invitation code')).toBeInTheDocument(),
    );
  });

  it('renders server field errors under the fields', async () => {
    mockedApi.acceptInvite.mockRejectedValue(
      new ApiError(400, { title: 'Validation error', status: 400, errors: { password: 'password must be between 12 and 128 characters' } }),
    );
    renderPageWithDestinations(<JoinHouseholdPage />, '/join', { '/dashboard': 'DASHBOARD_DEST' });

    await fill();

    await waitFor(() =>
      expect(screen.getByText('password must be between 12 and 128 characters')).toBeInTheDocument(),
    );
  });

  it('toasts on a network error', async () => {
    mockedApi.acceptInvite.mockRejectedValue(new Error('network down'));
    renderPageWithDestinations(<JoinHouseholdPage />, '/join', { '/dashboard': 'DASHBOARD_DEST' });

    await fill();

    await waitFor(() => expect(vi.mocked(toast).error).toHaveBeenCalled());
  });
});
