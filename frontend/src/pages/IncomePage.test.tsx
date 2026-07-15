import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import IncomePage from './IncomePage';
import { ApiError, api } from '../api/client';
import type { HouseholdIncome, Income } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      getIncome: vi.fn(),
      saveIncome: vi.fn(),
      householdIncome: vi.fn(),
      householdMembers: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const EMPTY: Income = {
  salaryAmount: null, salaryFrequency: null, superRate: null, bonusAnnual: null,
  otherIncomeAnnual: null, otherIncomeNote: null, annualIncome: 0, currency: 'AUD', notes: null,
};

const SUMMARY: HouseholdIncome = {
  currency: 'AUD',
  annualTotal: 154000,
  members: [
    { memberId: 'm2', isYou: false, annualIncome: 90000 },
    { memberId: 'm1', isYou: true, annualIncome: 64000 },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
  mockedApi.householdIncome.mockResolvedValue({ currency: 'AUD', annualTotal: 0, members: [] });
  mockedApi.householdMembers.mockResolvedValue([]);
});

describe('IncomePage', () => {
  it('fills in and saves your income', async () => {
    mockedApi.getIncome.mockResolvedValue(EMPTY);
    mockedApi.saveIncome.mockResolvedValue({ ...EMPTY, annualIncome: 64000 });
    renderWithProviders(<IncomePage />, ['/income']);

    await userEvent.type(await screen.findByLabelText('Salary (per pay)'), '2000');
    await userEvent.selectOptions(screen.getByLabelText('Pay frequency'), 'FORTNIGHTLY');
    await userEvent.type(screen.getByLabelText('Bonus (per year)'), '12000');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(mockedApi.saveIncome).toHaveBeenCalledWith(
        expect.objectContaining({ salaryAmount: 2000, salaryFrequency: 'FORTNIGHTLY', bonusAnnual: 12000, currency: 'AUD' }),
      ),
    );
  });

  it('shows the household income total with member names', async () => {
    mockedApi.getIncome.mockResolvedValue(EMPTY);
    mockedApi.householdIncome.mockResolvedValue(SUMMARY);
    mockedApi.householdMembers.mockResolvedValue([
      { memberId: 'm1', name: 'Jane', role: 'OWNER', isYou: true },
      { memberId: 'm2', name: 'Ashik', role: 'ADULT', isYou: false },
    ]);
    renderWithProviders(<IncomePage />, ['/income']);

    await waitFor(() => expect(screen.getByText('Household income')).toBeInTheDocument());
    // the other member is named; the caller is "You"; total is shown
    expect(screen.getByText('Ashik')).toBeInTheDocument();
    expect(screen.getByText('You')).toBeInTheDocument();
    expect(screen.getByTestId('household-income-total')).toHaveTextContent('154,000');
  });

  it('prefills from an existing income', async () => {
    mockedApi.getIncome.mockResolvedValue({ ...EMPTY, salaryAmount: 90000, salaryFrequency: 'ANNUALLY' });
    renderWithProviders(<IncomePage />, ['/income']);

    await waitFor(() => expect(screen.getByLabelText('Salary (per pay)')).toHaveValue(90000));
    expect(screen.getByLabelText('Pay frequency')).toHaveValue('ANNUALLY');
  });

  it('toasts when the save fails', async () => {
    mockedApi.getIncome.mockResolvedValue(EMPTY);
    mockedApi.saveIncome.mockRejectedValue(
      new ApiError(400, { title: 'Validation error', status: 400, detail: 'superRate must be between 0 and 100' }),
    );
    renderWithProviders(<IncomePage />, ['/income']);

    await userEvent.click(await screen.findByRole('button', { name: 'Save' }));
    await waitFor(() => expect(vi.mocked(toast).error).toHaveBeenCalled());
  });

  it('shows an error if income cannot load', async () => {
    mockedApi.getIncome.mockRejectedValue(new ApiError(500, { title: 'Server error', status: 500 }));
    renderWithProviders(<IncomePage />, ['/income']);

    expect(await screen.findByText(/could not load your income/i)).toBeInTheDocument();
  });
});
