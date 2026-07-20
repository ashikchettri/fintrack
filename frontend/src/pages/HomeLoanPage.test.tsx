import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import HomeLoanPage from './HomeLoanPage';
import { ApiError, api } from '../api/client';
import type { HomeLoan } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      getHomeLoan: vi.fn(),
      saveHomeLoan: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const EMPTY: HomeLoan = {
  hasHomeLoan: false, lender: null, loanAmount: null, interestRate: null,
  repaymentFrequency: null, repaymentAmount: null, hasOffset: false, offsetBalance: null,
  ownership: null, currency: 'AUD', notes: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('HomeLoanPage', () => {
  it('reveals the detail fields only when you have a loan, and saves them', async () => {
    mockedApi.getHomeLoan.mockResolvedValue(EMPTY);
    mockedApi.saveHomeLoan.mockResolvedValue({ ...EMPTY, hasHomeLoan: true });
    renderWithProviders(<HomeLoanPage />, ['/home-loan']);

    const toggle = await screen.findByLabelText('Do you have a home loan?');
    // hidden until you say yes
    expect(screen.queryByLabelText('Total loan amount')).not.toBeInTheDocument();

    await userEvent.click(toggle);
    // scope to the form: the payoff calculator below has its own rate input
    const form = screen.getByLabelText('Total loan amount').closest('form') as HTMLFormElement;
    await userEvent.type(screen.getByLabelText('Total loan amount'), '650000');
    await userEvent.type(within(form).getByLabelText('Interest rate (% p.a.)'), '6.25');
    await userEvent.selectOptions(screen.getByLabelText('Repayment frequency'), 'MONTHLY');
    await userEvent.selectOptions(screen.getByLabelText('Whose name is the loan in?'), 'JOINT');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(mockedApi.saveHomeLoan).toHaveBeenCalledWith(
        expect.objectContaining({
          hasHomeLoan: true, loanAmount: 650000, interestRate: 6.25,
          repaymentFrequency: 'MONTHLY', ownership: 'JOINT', currency: 'AUD',
        }),
      ),
    );
  });

  it('prefills the form from an existing profile', async () => {
    mockedApi.getHomeLoan.mockResolvedValue({
      ...EMPTY, hasHomeLoan: true, loanAmount: 500000, interestRate: 5.99,
      repaymentFrequency: 'FORTNIGHTLY', hasOffset: true, offsetBalance: 42000, ownership: 'JOINT',
    });
    renderWithProviders(<HomeLoanPage />, ['/home-loan']);

    await waitFor(() => expect(screen.getByLabelText('Total loan amount')).toHaveValue(500000));
    const form = screen.getByLabelText('Total loan amount').closest('form') as HTMLFormElement;
    expect(within(form).getByLabelText('Interest rate (% p.a.)')).toHaveValue(5.99);
    // offset sub-field is shown because hasOffset is true
    expect(screen.getByLabelText('Savings in offset account')).toHaveValue(42000);
  });

  it('toasts when the save fails', async () => {
    mockedApi.getHomeLoan.mockResolvedValue({ ...EMPTY, hasHomeLoan: true });
    mockedApi.saveHomeLoan.mockRejectedValue(
      new ApiError(400, { title: 'Validation error', status: 400, detail: 'interestRate must be between 0 and 100' }),
    );
    renderWithProviders(<HomeLoanPage />, ['/home-loan']);

    await screen.findByLabelText('Total loan amount');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(vi.mocked(toast).error).toHaveBeenCalled());
  });

  it('shows an error if the profile cannot load', async () => {
    mockedApi.getHomeLoan.mockRejectedValue(new ApiError(500, { title: 'Server error', status: 500 }));
    renderWithProviders(<HomeLoanPage />, ['/home-loan']);

    expect(await screen.findByText(/could not load your home-loan details/i)).toBeInTheDocument();
  });
});
