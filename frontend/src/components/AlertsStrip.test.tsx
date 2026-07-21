import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { AlertsStrip } from './AlertsStrip';
import { api } from '../api/client';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      getOverview: vi.fn(),
      getCashFlow: vi.fn(),
      householdShared: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const settledShared = {
  currency: 'AUD', month: null, availableMonths: [], totalShared: 0, memberCount: 1, fairShare: 0,
  settlement: { yourContribution: 0, fairShare: 0, balance: 0, status: 'settled' as const, amount: 0 },
  contributions: [], byCategory: [], transactions: [],
};

const healthyOverview = {
  currency: 'AUD', hasBudget: true, actualMonth: '2026-07',
  planned: { income: 10000, expenses: 6000, savings: 2000, leftover: 2000 },
  actual: { income: 9000, expenses: 4000 }, byCategory: [],
};

const healthyCashFlow = {
  currency: 'AUD', monthlyIncome: 7500, monthlyLoanRepayment: 2000,
  monthlyAvgSpending: 3000, monthlySurplus: 2500, monthsOfSpendingData: 3,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
  mockedApi.getOverview.mockResolvedValue(healthyOverview as never);
  mockedApi.getCashFlow.mockResolvedValue(healthyCashFlow);
  mockedApi.householdShared.mockResolvedValue(settledShared);
});

describe('AlertsStrip', () => {
  it('renders nothing when everything is on track', async () => {
    const { container } = renderWithProviders(<AlertsStrip />);
    await waitFor(() => expect(mockedApi.getOverview).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it('flags over-budget spending', async () => {
    mockedApi.getOverview.mockResolvedValue({
      ...healthyOverview, actual: { income: 9000, expenses: 8000 }, // $2,000 over the $6,000 plan
    } as never);
    renderWithProviders(<AlertsStrip />);

    expect(await screen.findByText(/over budget this month/i)).toHaveTextContent(/2,000/);
  });

  it('flags negative cash flow', async () => {
    mockedApi.getCashFlow.mockResolvedValue({ ...healthyCashFlow, monthlySurplus: -500 });
    renderWithProviders(<AlertsStrip />);

    expect(await screen.findByText(/more than you earn/i)).toHaveTextContent(/500/);
  });

  it('flags an outstanding settle-up', async () => {
    mockedApi.householdShared.mockResolvedValue({
      ...settledShared,
      settlement: { yourContribution: 0, fairShare: 120, balance: -120, status: 'owes', amount: 120 },
    });
    renderWithProviders(<AlertsStrip />);

    expect(await screen.findByText(/you owe/i)).toHaveTextContent(/120/);
  });
});
