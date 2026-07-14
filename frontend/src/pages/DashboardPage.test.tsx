import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
      transactions: vi.fn(),
      importTransactions: vi.fn(),
      householdShared: vi.fn(),
      setTransactionVisibility: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

const POPULATED: DashboardResponse = {
  currency: 'AUD',
  totals: { income: 3040, expenses: 217.7, net: 2822.3, transactionCount: 6 },
  byCategory: [
    { category: 'Transportation', spent: 132.5, share: 0.6086 },
    { category: 'Food & Drink', spent: 85.2, share: 0.3914 },
  ],
  byMonth: [
    { month: '2026-06', income: 3000, expenses: 0, net: 3000 },
    { month: '2026-07', income: 0, expenses: 12.5, net: -12.5 },
  ],
  topMerchants: [{ description: 'Reddy Express', spent: 120, count: 2 }],
  recent: [
    { id: 't1', date: '2026-07-11', description: 'Transport NSW', category: 'Transportation', amount: -12.5, accountId: 'a1', visibility: 'personal' },
    { id: 't2', date: '2026-06-01', description: 'Salary', category: 'Income', amount: 3000, accountId: 'a2', visibility: 'personal' },
  ],
};

const EMPTY_HOUSEHOLD = {
  currency: null,
  totalShared: 0,
  memberCount: 0,
  fairShare: 0,
  settlement: { yourContribution: 0, fairShare: 0, balance: 0, status: 'settled' as const, amount: 0 },
  contributions: [],
  byCategory: [],
  transactions: [],
};

const EMPTY: DashboardResponse = {
  currency: null,
  totals: { income: 0, expenses: 0, net: 0, transactionCount: 0 },
  byCategory: [],
  byMonth: [],
  topMerchants: [],
  recent: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
  mockedApi.householdShared.mockResolvedValue(EMPTY_HOUSEHOLD);
});

describe('DashboardPage', () => {
  it('shows the upload call-to-action when there are no transactions', async () => {
    mockedApi.dashboard.mockResolvedValue(EMPTY);
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    expect(await screen.findByTestId('csv-dropzone')).toBeInTheDocument();
    expect(screen.getByText(/upload a csv export/i)).toBeInTheDocument();
  });

  it('renders KPIs, charts, merchants and recent activity when populated', async () => {
    mockedApi.dashboard.mockResolvedValue(POPULATED);
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    await waitFor(() => expect(screen.getByTestId('kpi-income')).toBeInTheDocument());
    expect(screen.getByTestId('kpi-income')).toHaveTextContent('3,040');
    expect(screen.getByTestId('kpi-expenses')).toHaveTextContent('217.70');
    expect(screen.getByTestId('kpi-net')).toHaveTextContent('2,822.30');

    // category donut legend (also shown in the recent-table category cell) +
    // monthly chart + merchants + recent row
    expect(screen.getAllByText('Transportation').length).toBeGreaterThan(0);
    expect(screen.getByText('Jun 2026')).toBeInTheDocument();
    expect(screen.getByText('Reddy Express')).toBeInTheDocument();
    expect(screen.getByText('Transport NSW')).toBeInTheDocument();
    expect(screen.getByText('Salary')).toBeInTheDocument();

    // the differentiator card (self-fetches) + per-row share toggles are present
    expect(await screen.findByText('Shared with your household')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /shared with household/i }).length).toBeGreaterThan(0);
  });

  it('surfaces an error when the dashboard cannot load', async () => {
    mockedApi.dashboard.mockRejectedValue(new ApiError(500, { title: 'Server error', status: 500 }));
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    expect(await screen.findByText(/could not load your dashboard/i)).toBeInTheDocument();
  });

  it('uploads a chosen CSV with the selected currency', async () => {
    mockedApi.dashboard.mockResolvedValue(EMPTY);
    mockedApi.importTransactions.mockResolvedValue({
      importId: 'i1', fileName: 'statement.csv', currency: 'AUD',
      rowsParsed: 6, imported: 6, duplicatesSkipped: 0, failedRows: 0,
      accountsCreated: ['Everyday'], dateFrom: '2025-08-17', dateTo: '2026-07-11',
      totalIncome: 3040, totalExpenses: 217.7, net: 2822.3, errors: [],
    });
    renderWithProviders(<DashboardPage />, ['/dashboard']);

    await screen.findByTestId('csv-dropzone');
    const file = new File(['Date,Description,Amount\n2026-07-11,Coffee,-4.50\n'], 'statement.csv', {
      type: 'text/csv',
    });
    await userEvent.upload(screen.getByLabelText('Choose a CSV file'), file);

    await waitFor(() => expect(mockedApi.importTransactions).toHaveBeenCalledTimes(1));
    const [uploaded, currency] = mockedApi.importTransactions.mock.calls[0];
    expect(uploaded).toBeInstanceOf(File);
    expect((uploaded as File).name).toBe('statement.csv');
    expect(currency).toBe('AUD');
  });
});
