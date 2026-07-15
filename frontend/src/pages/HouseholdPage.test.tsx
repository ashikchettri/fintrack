import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import HouseholdPage from './HouseholdPage';
import { api } from '../api/client';
import { renderWithProviders } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      householdMembers: vi.fn(),
      inviteMember: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);
const mockedToast = vi.mocked(toast);

const OWNER = { memberId: 'm1', name: 'Jane', role: 'OWNER', isYou: true };
const PARTNER = { memberId: 'm2', name: 'Ashik', role: 'ADULT', isYou: false };

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('HouseholdPage', () => {
  it('shows the roster and lets an owner invite a member', async () => {
    mockedApi.householdMembers.mockResolvedValue([OWNER, PARTNER]);
    mockedApi.inviteMember.mockResolvedValue(undefined);
    renderWithProviders(<HouseholdPage />, ['/household']);

    await waitFor(() => expect(screen.getByText('Jane')).toBeInTheDocument());
    expect(screen.getByText('Ashik')).toBeInTheDocument();
    expect(screen.getByText('(you)')).toBeInTheDocument();

    // owner sees the invite form
    await userEvent.type(screen.getByLabelText('Email'), 'new@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send invite' }));

    await waitFor(() => expect(mockedApi.inviteMember).toHaveBeenCalledWith('new@example.com'));
    expect(mockedToast.success).toHaveBeenCalledWith('Invitation sent', expect.anything());
  });

  it('hides the invite form from a non-owner', async () => {
    mockedApi.householdMembers.mockResolvedValue([
      { memberId: 'm1', name: 'Jane', role: 'OWNER', isYou: false },
      { memberId: 'm2', name: 'Ashik', role: 'ADULT', isYou: true },
    ]);
    renderWithProviders(<HouseholdPage />, ['/household']);

    await waitFor(() => expect(screen.getByText('Ashik')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Send invite' })).not.toBeInTheDocument();
  });

  it('surfaces an invite error inline (e.g. already a member)', async () => {
    const { ApiError } = await import('../api/client');
    mockedApi.householdMembers.mockResolvedValue([OWNER]);
    mockedApi.inviteMember.mockRejectedValue(
      new ApiError(409, { title: 'Email already in use', status: 409, detail: 'An account with this email already exists' }),
    );
    renderWithProviders(<HouseholdPage />, ['/household']);

    await waitFor(() => expect(screen.getByText('Jane')).toBeInTheDocument());
    await userEvent.type(screen.getByLabelText('Email'), 'taken@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send invite' }));

    await waitFor(() =>
      expect(screen.getByText('An account with this email already exists')).toBeInTheDocument(),
    );
  });
});
