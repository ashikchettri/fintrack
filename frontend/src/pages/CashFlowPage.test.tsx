import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CashFlowPage from './CashFlowPage';
import { ApiError, api } from '../api/client';
import type { CashFlow } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), getCashFlow: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const CF: CashFlow = {
  currency: 'AUD',
  monthlyIncome: 7500,
  monthlyLoanRepayment: 3000,
  monthlyAvgSpending: 3500,
  monthlySurplus: 4000,
  monthsOfSpendingData: 2,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('CashFlowPage', () => {
  it('shows the monthly surplus and a default affordability verdict', async () => {
    mockedApi.getCashFlow.mockResolvedValue(CF);
    renderWithProviders(<CashFlowPage />, ['/cash-flow']);

    await waitFor(() => expect(screen.getByTestId('monthly-surplus')).toHaveTextContent('4,000'));
    expect(screen.getByText(/you can safely spend about/i)).toBeInTheDocument();

    // default: $500k @ 6.25% / 30y ≈ $3,079/mo → surplus 4000 leaves ~921 → affordable
    expect(screen.getByTestId('new-repayment')).toHaveTextContent(/3,07[89]/);
    expect(screen.getByTestId('affordability-verdict')).toHaveTextContent(/looks affordable/i);
  });

  it('flips to "short" when the loan is too big', async () => {
    mockedApi.getCashFlow.mockResolvedValue(CF);
    renderWithProviders(<CashFlowPage />, ['/cash-flow']);

    const amount = await screen.findByLabelText('Loan amount');
    await userEvent.clear(amount);
    await userEvent.type(amount, '1000000'); // ~$6,158/mo > $4,000 surplus

    await waitFor(() =>
      expect(screen.getByTestId('affordability-verdict')).toHaveTextContent(/short/i),
    );
  });

  it('says you are over budget when spending exceeds income', async () => {
    mockedApi.getCashFlow.mockResolvedValue({ ...CF, monthlyAvgSpending: 9000, monthlySurplus: -1500 });
    renderWithProviders(<CashFlowPage />, ['/cash-flow']);

    await waitFor(() => expect(screen.getByText(/you are over by about/i)).toBeInTheDocument());
    expect(screen.getByTestId('monthly-surplus')).toHaveTextContent('1,500');
  });

  it('shows an error if cash flow cannot load', async () => {
    mockedApi.getCashFlow.mockRejectedValue(new ApiError(500, { title: 'Server error', status: 500 }));
    renderWithProviders(<CashFlowPage />, ['/cash-flow']);

    expect(await screen.findByText(/could not load your cash flow/i)).toBeInTheDocument();
  });
});
