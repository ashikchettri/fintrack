import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import DashboardPage from './DashboardPage';
import { ApiError, api } from '../api/client';
import type { DashboardResponse } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      dashboard: vi.fn(),
      getMonthlySummary: vi.fn(),
      getCashFlow: vi.fn(),
      getHomeLoan: vi.fn(),
      getOverview: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const POPULATED: DashboardResponse = {
  currency: 'AUD',
  month: null,
  availableMonths: ['2026-07'],
  totals: { income: 3040, expenses: 217.7, net: 2822.3, transactionCount: 6 },
  byCategory: [], byMonth: [], topMerchants: [], recent: [],
};

const EMPTY: DashboardResponse = {
  currency: null, month: null, availableMonths: [],
  totals: { income: 0, expenses: 0, net: 0, transactionCount: 0 },
  byCategory: [], byMonth: [], topMerchants: [], recent: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
  mockedApi.getMonthlySummary.mockResolvedValue({
    month: '2026-07', currency: 'AUD',
    totals: { income: 3040, expenses: 217.7, net: 2822.3, transactionCount: 6 },
    headline: 'A steady month.', insights: [],
  });
  mockedApi.getCashFlow.mockResolvedValue({
    currency: 'AUD', monthlyIncome: 7500, monthlyLoanRepayment: 2000,
    monthlyAvgSpending: 3000, monthlySurplus: 2500, monthsOfSpendingData: 3,
  });
  mockedApi.getHomeLoan.mockResolvedValue({
    hasHomeLoan: true, lender: null, loanAmount: 500000, interestRate: 6.25,
    repaymentFrequency: 'MONTHLY', repaymentAmount: 3079, hasOffset: false,
    offsetBalance: null, ownership: 'JOINT', currency: 'AUD', notes: null,
  });
  mockedApi.getOverview.mockResolvedValue({
    currency: 'AUD', hasBudget: true, actualMonth: null,
    planned: { income: 10000, expenses: 6000, savings: 2000, leftover: 2000 },
    actual: { income: 0, expenses: 0 }, byCategory: [],
  } as never);
});

describe('DashboardPage (overview)', () => {
  it('shows the headline totals and the summary rollups', async () => {
    mockedApi.dashboard.mockResolvedValue(POPULATED);
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    await waitFor(() => expect(screen.getByTestId('kpi-income')).toBeInTheDocument());
    expect(screen.getByTestId('kpi-income')).toHaveTextContent('3,040');
    expect(screen.getByTestId('kpi-net')).toHaveTextContent('2,822.30');

    // the AI summary + a way to ask a question
    expect(await screen.findByText('A steady month.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ask about your spending/i })).toBeInTheDocument();

    // the three rollup cards (heading role avoids the nav links of the same name)
    expect(await screen.findByRole('heading', { name: 'Cash flow' })).toBeInTheDocument();
    expect(screen.getByText(/2,500/)).toBeInTheDocument();          // surplus
    expect(screen.getByRole('heading', { name: 'Home loan' })).toBeInTheDocument();
    expect(screen.getByText(/500,000/)).toBeInTheDocument();        // balance
    expect(screen.getByRole('heading', { name: 'Income & expenses' })).toBeInTheDocument();
  });

  it('shows an import banner instead of totals before any statement is imported', async () => {
    mockedApi.dashboard.mockResolvedValue(EMPTY);
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    expect(await screen.findByText(/import a bank statement to see your income/i)).toBeInTheDocument();
    expect(screen.queryByTestId('kpi-income')).not.toBeInTheDocument();
  });

  it('surfaces an error when the dashboard cannot load', async () => {
    mockedApi.dashboard.mockRejectedValue(new ApiError(500, { title: 'Server error', status: 500 }));
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    expect(await screen.findByText(/could not load your dashboard/i)).toBeInTheDocument();
  });
});
