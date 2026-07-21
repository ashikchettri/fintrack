import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { CashFlowSummaryCard } from './CashFlowSummaryCard';
import { HomeLoanSummaryCard } from './HomeLoanSummaryCard';
import { BudgetSummaryCard } from './BudgetSummaryCard';
import { api } from '../../api/client';
import { renderWithProviders } from '../../test/utils';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      getCashFlow: vi.fn(),
      getHomeLoan: vi.fn(),
      getOverview: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('summary cards — empty states', () => {
  it('cash flow prompts for income when there is none', async () => {
    mockedApi.getCashFlow.mockResolvedValue({
      currency: 'AUD', monthlyIncome: 0, monthlyLoanRepayment: 0,
      monthlyAvgSpending: 0, monthlySurplus: 0, monthsOfSpendingData: 0,
    });
    renderWithProviders(<CashFlowSummaryCard />);

    expect(await screen.findByText(/add your income/i)).toBeInTheDocument();
  });

  it('home loan prompts to add a loan when none is tracked', async () => {
    mockedApi.getHomeLoan.mockResolvedValue({
      hasHomeLoan: false, lender: null, loanAmount: null, interestRate: null,
      repaymentFrequency: null, repaymentAmount: null, hasOffset: false,
      offsetBalance: null, ownership: null, currency: 'AUD', notes: null,
    });
    renderWithProviders(<HomeLoanSummaryCard />);

    expect(await screen.findByText(/track your mortgage/i)).toBeInTheDocument();
  });

  it('budget prompts to create one when none exists', async () => {
    mockedApi.getOverview.mockResolvedValue({ hasBudget: false } as never);
    renderWithProviders(<BudgetSummaryCard />);

    expect(await screen.findByText(/set a household budget/i)).toBeInTheDocument();
  });

  it('a card stays quiet when its data fails to load', async () => {
    mockedApi.getCashFlow.mockRejectedValue(new Error('boom'));
    const { container } = renderWithProviders(<CashFlowSummaryCard />);

    await waitFor(() => expect(mockedApi.getCashFlow).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
