import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HouseholdSection } from './HouseholdSection';
import { ApiError, api } from '../api/client';
import type { MemberResponse } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), householdMembers: vi.fn(), inviteMember: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const OWNER: MemberResponse = { memberId: 'm1', name: 'Ashik', role: 'OWNER', isYou: true };
const MEMBER: MemberResponse = { memberId: 'm2', name: 'Dixya', role: 'MEMBER', isYou: false };

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('HouseholdSection', () => {
  it('shows the roster and the invite form for an owner', async () => {
    mockedApi.householdMembers.mockResolvedValue([OWNER, MEMBER]);
    renderWithProviders(<HouseholdSection />);

    expect(await screen.findByText('Ashik')).toBeInTheDocument();
    expect(screen.getByText('Dixya')).toBeInTheDocument();
    expect(screen.getByText('(you)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send invite/i })).toBeInTheDocument();
  });

  it('hides the invite form from a non-owner', async () => {
    mockedApi.householdMembers.mockResolvedValue([
      { ...MEMBER, isYou: true },
      { ...OWNER, isYou: false },
    ]);
    renderWithProviders(<HouseholdSection />);

    expect(await screen.findByText('Dixya')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /send invite/i })).not.toBeInTheDocument();
  });

  it('renders nothing when the roster fails to load', async () => {
    mockedApi.householdMembers.mockRejectedValue(new Error('boom'));
    const { container } = renderWithProviders(<HouseholdSection />);

    await waitFor(() => expect(mockedApi.householdMembers).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('sends an invite and clears the field on success', async () => {
    mockedApi.householdMembers.mockResolvedValue([OWNER]);
    mockedApi.inviteMember.mockResolvedValue();
    renderWithProviders(<HouseholdSection />);

    const input = await screen.findByPlaceholderText(/partner@example.com/i);
    await userEvent.type(input, 'partner@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send invite/i }));

    await waitFor(() =>
      expect(mockedApi.inviteMember).toHaveBeenCalledWith('partner@example.com'),
    );
    await waitFor(() => expect(input).toHaveValue(''));
  });

  it('surfaces an invite error inline', async () => {
    mockedApi.householdMembers.mockResolvedValue([OWNER]);
    mockedApi.inviteMember.mockRejectedValue(
      new ApiError(409, { title: 'Conflict', status: 409, detail: 'Already a member' }),
    );
    renderWithProviders(<HouseholdSection />);

    const input = await screen.findByPlaceholderText(/partner@example.com/i);
    await userEvent.type(input, 'partner@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send invite/i }));

    expect(await screen.findByText('Already a member')).toBeInTheDocument();
  });
});
