import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { BudgetVsActualCard } from './BudgetVsActualCard';
import { api } from '../api/client';
import type { Overview } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), getOverview: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const overview = (over: Partial<Overview>): Overview => ({
  currency: 'AUD',
  hasBudget: true,
  actualMonth: '2026-06',
  planned: { income: 15249, expenses: 9668, savings: 6000, leftover: -419 },
  actual: { income: 15000, expenses: 8500 },
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('BudgetVsActualCard', () => {
  it('shows under-budget when actual spend is below the plan', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({}));
    renderWithProviders(<BudgetVsActualCard />);

    await waitFor(() => expect(screen.getByText('Budget vs actual')).toBeInTheDocument());
    // spent 8,500 of 9,668 budget → 1,168 under
    expect(screen.getByTestId('compare-expenses')).toHaveTextContent(/under budget/i);
    expect(screen.getByText(/jun 2026/i)).toBeInTheDocument();
  });

  it('shows over-budget when actual spend exceeds the plan', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({ actual: { income: 15000, expenses: 11000 } }));
    renderWithProviders(<BudgetVsActualCard />);

    await waitFor(() =>
      expect(screen.getByTestId('compare-expenses')).toHaveTextContent(/over budget/i),
    );
  });

  it('prompts to create a budget when none exists', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({ hasBudget: false }));
    renderWithProviders(<BudgetVsActualCard />);

    await waitFor(() => expect(screen.getByText(/set a household budget/i)).toBeInTheDocument());
    expect(screen.getByRole('link', { name: /create budget/i })).toHaveAttribute('href', '/budget');
  });
});
