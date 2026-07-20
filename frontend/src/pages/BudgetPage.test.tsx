import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import BudgetPage from './BudgetPage';
import { ApiError, api } from '../api/client';
import type { Budget } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), getBudget: vi.fn(), saveBudget: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const BUDGET: Budget = {
  currency: 'AUD',
  lines: [
    { section: 'INCOME', category: null, name: 'Salary', frequency: 'MONTHLY', amount: 10000 },
    { section: 'EXPENSE', category: 'Housing', name: 'Rent', frequency: 'MONTHLY', amount: 2600 },
    { section: 'SAVING', category: null, name: 'Savings', frequency: 'MONTHLY', amount: 2000 },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('BudgetPage', () => {
  it('computes the leftover from income − expenses − savings', async () => {
    mockedApi.getBudget.mockResolvedValue(BUDGET);
    renderWithProviders(<BudgetPage />, ['/budget']);

    // 10,000 − 2,600 − 2,000 = 5,400
    await waitFor(() => expect(screen.getByTestId('budget-leftover')).toHaveTextContent('5,400'));
  });

  it('recomputes live as you edit an amount', async () => {
    mockedApi.getBudget.mockResolvedValue(BUDGET);
    renderWithProviders(<BudgetPage />, ['/budget']);

    const amounts = await screen.findAllByLabelText('Amount');
    // second row is Rent (2600); bump it to 3600 → leftover 4,400
    await userEvent.clear(amounts[1]);
    await userEvent.type(amounts[1], '3600');

    await waitFor(() => expect(screen.getByTestId('budget-leftover')).toHaveTextContent('4,400'));
  });

  it('saves the whole budget', async () => {
    mockedApi.getBudget.mockResolvedValue(BUDGET);
    mockedApi.saveBudget.mockResolvedValue(BUDGET);
    renderWithProviders(<BudgetPage />, ['/budget']);

    await screen.findByTestId('budget-leftover');
    await userEvent.click(screen.getByRole('button', { name: 'Save budget' }));

    await waitFor(() => expect(mockedApi.saveBudget).toHaveBeenCalledTimes(1));
    const sent = mockedApi.saveBudget.mock.calls[0][0];
    expect(sent.lines).toHaveLength(3);
    expect(sent.lines.find((l) => l.name === 'Salary')?.amount).toBe(10000);
  });

  it('adds a new line to a section', async () => {
    mockedApi.getBudget.mockResolvedValue(BUDGET);
    renderWithProviders(<BudgetPage />, ['/budget']);

    const before = (await screen.findAllByLabelText('Line name')).length;
    // the first "Add line" is under Income
    await userEvent.click(screen.getAllByRole('button', { name: /add line/i })[0]);

    await waitFor(() =>
      expect(screen.getAllByLabelText('Line name').length).toBe(before + 1),
    );
  });

  it('removes a line', async () => {
    mockedApi.getBudget.mockResolvedValue(BUDGET);
    renderWithProviders(<BudgetPage />, ['/budget']);

    const before = (await screen.findAllByLabelText('Line name')).length;
    await userEvent.click(screen.getAllByRole('button', { name: 'Remove line' })[0]);

    await waitFor(() =>
      expect(screen.getAllByLabelText('Line name').length).toBe(before - 1),
    );
  });

  it('toasts when the save fails', async () => {
    mockedApi.getBudget.mockResolvedValue(BUDGET);
    mockedApi.saveBudget.mockRejectedValue(new ApiError(400, { title: 'Validation error', status: 400, detail: 'bad' }));
    renderWithProviders(<BudgetPage />, ['/budget']);

    await screen.findByTestId('budget-leftover');
    await userEvent.click(screen.getByRole('button', { name: 'Save budget' }));

    await waitFor(() => expect(vi.mocked(toast).error).toHaveBeenCalled());
  });

  it('shows an error if the budget cannot load', async () => {
    mockedApi.getBudget.mockRejectedValue(new ApiError(500, { title: 'Server error', status: 500 }));
    renderWithProviders(<BudgetPage />, ['/budget']);

    expect(await screen.findByText(/could not load your budget/i)).toBeInTheDocument();
  });
});
