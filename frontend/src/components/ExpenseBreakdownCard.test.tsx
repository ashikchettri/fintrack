import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExpenseBreakdownCard } from './ExpenseBreakdownCard';
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
  planned: { income: 15000, expenses: 4000, savings: 0, leftover: 11000 },
  actual: { income: 0, expenses: 0 },
  byCategory: [
    { category: 'Housing', planned: 3000, actual: 0 },
    { category: 'Food & Drink', planned: 1000, actual: 0 },
  ],
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('ExpenseBreakdownCard', () => {
  it('charts budgeted expenses by category, monthly by default', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({}));
    renderWithProviders(<ExpenseBreakdownCard />);

    await waitFor(() => expect(screen.getByText('Where your money goes')).toBeInTheDocument());
    expect(screen.getByText(/per month/i)).toBeInTheDocument();
    expect(screen.getByText('Housing')).toBeInTheDocument();
    expect(screen.getByText('Food & Drink')).toBeInTheDocument();
    expect(screen.getByText(/^\$4,000\.00$/)).toBeInTheDocument();  // total in the donut center
    expect(screen.getByText(/^\$3,000\.00$/)).toBeInTheDocument();  // Housing legend
  });

  it('annualizes every figure when switched to yearly', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({}));
    renderWithProviders(<ExpenseBreakdownCard />);

    await waitFor(() => expect(screen.getByText('Where your money goes')).toBeInTheDocument());
    await userEvent.click(screen.getByRole('button', { name: 'Yearly' }));

    expect(screen.getByText(/per year/i)).toBeInTheDocument();
    expect(screen.getByText(/^\$48,000\.00$/)).toBeInTheDocument();  // 4,000 × 12 total
    expect(screen.getByText(/^\$36,000\.00$/)).toBeInTheDocument();  // Housing 3,000 × 12
  });

  it('renders nothing without a budget', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({ hasBudget: false }));
    renderWithProviders(<ExpenseBreakdownCard />);

    await waitFor(() => expect(mockedApi.getOverview).toHaveBeenCalled());
    expect(screen.queryByText('Where your money goes')).not.toBeInTheDocument();
  });

  it('renders nothing when the budget has no expense categories', async () => {
    mockedApi.getOverview.mockResolvedValue(overview({ byCategory: [] }));
    renderWithProviders(<ExpenseBreakdownCard />);

    await waitFor(() => expect(mockedApi.getOverview).toHaveBeenCalled());
    expect(screen.queryByText('Where your money goes')).not.toBeInTheDocument();
  });
});
