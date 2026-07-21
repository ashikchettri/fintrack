import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { InsightsSummaryCard } from './InsightsSummaryCard';
import { api } from '../api/client';
import type { MonthlySummary } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), getMonthlySummary: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const summary: MonthlySummary = {
  month: '2026-06',
  currency: 'AUD',
  totals: { income: 5000, expenses: 4120, net: 880, transactionCount: 78 },
  headline: 'In June 2026 you spent AUD 4,120.00 across 78 transactions.',
  insights: ['You came out ahead by AUD 880.00.', 'Groceries & Food was your biggest category.'],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('InsightsSummaryCard', () => {
  it('renders the headline, insights, and month', async () => {
    mockedApi.getMonthlySummary.mockResolvedValue(summary);
    renderWithProviders(<InsightsSummaryCard />);

    expect(await screen.findByText(/across 78 transactions/i)).toBeInTheDocument();
    expect(screen.getByText(/came out ahead/i)).toBeInTheDocument();
    expect(screen.getByText(/Groceries & Food was your biggest/i)).toBeInTheDocument();
    expect(screen.getByText(/Jun 2026/i)).toBeInTheDocument();
  });

  it('stays quiet when the summary fails to load', async () => {
    mockedApi.getMonthlySummary.mockRejectedValue(new Error('boom'));
    const { container } = renderWithProviders(<InsightsSummaryCard />);

    await waitFor(() => expect(mockedApi.getMonthlySummary).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
