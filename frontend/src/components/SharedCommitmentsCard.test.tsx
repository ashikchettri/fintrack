import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { SharedCommitmentsCard } from './SharedCommitmentsCard';
import { api } from '../api/client';
import type { SharedHouseholdView } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      householdShared: vi.fn(),
      householdMembers: vi.fn().mockResolvedValue([]),
    },
  };
});

const mockedApi = vi.mocked(api);

const view = (over: Partial<SharedHouseholdView>): SharedHouseholdView => ({
  currency: 'AUD',
  month: null,
  availableMonths: ['2026-07'],
  totalShared: 1600,
  memberCount: 2,
  fairShare: 800,
  settlement: { yourContribution: 1200, fairShare: 800, balance: 400, status: 'owed', amount: 400 },
  contributions: [
    { memberId: 'me', covered: 1200, isYou: true },
    { memberId: 'them', covered: 400, isYou: false },
  ],
  byCategory: [{ category: 'Housing', amount: 1000 }],
  transactions: [],
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
  mockedApi.householdMembers.mockResolvedValue([]);
});

describe('SharedCommitmentsCard', () => {
  it('shows the settlement and contributions when shared items exist', async () => {
    mockedApi.householdShared.mockResolvedValue(view({}));
    renderWithProviders(<SharedCommitmentsCard />);

    await waitFor(() => expect(screen.getByText('Shared with your household')).toBeInTheDocument());
    expect(screen.getByText("You're owed")).toBeInTheDocument();
    expect(screen.getByTestId('settlement-amount')).toHaveTextContent('400');
    expect(screen.getByText('You')).toBeInTheDocument();
    expect(screen.getByText('Housemate')).toBeInTheDocument();
    // the privacy promise is stated on the card
    expect(screen.getByText(/personal spending stays private/i)).toBeInTheDocument();
  });

  it('labels a contribution with the member name from the roster', async () => {
    mockedApi.householdShared.mockResolvedValue(view({}));
    mockedApi.householdMembers.mockResolvedValue([
      { memberId: 'me', name: 'Jane', role: 'OWNER', isYou: true },
      { memberId: 'them', name: 'Ashik', role: 'ADULT', isYou: false },
    ]);
    renderWithProviders(<SharedCommitmentsCard />);

    // "You" for the caller, the real name for the other member (not "Housemate")
    await waitFor(() => expect(screen.getByText('Ashik')).toBeInTheDocument());
    expect(screen.getByText('You')).toBeInTheDocument();
    expect(screen.queryByText('Housemate')).not.toBeInTheDocument();
  });

  it('says you owe when under your fair share', async () => {
    mockedApi.householdShared.mockResolvedValue(
      view({ settlement: { yourContribution: 400, fairShare: 800, balance: -400, status: 'owes', amount: 400 } }),
    );
    renderWithProviders(<SharedCommitmentsCard />);

    await waitFor(() => expect(screen.getByText('You owe')).toBeInTheDocument());
  });

  it('prompts to mark shared costs when nothing is shared yet', async () => {
    mockedApi.householdShared.mockResolvedValue(view({ memberCount: 0, contributions: [], totalShared: 0 }));
    renderWithProviders(<SharedCommitmentsCard />);

    await waitFor(() => expect(screen.getByText(/mark shared costs/i)).toBeInTheDocument());
  });
});
