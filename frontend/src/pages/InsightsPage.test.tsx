import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import InsightsPage from './InsightsPage';
import { api } from '../api/client';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      getMonthlySummary: vi.fn(),
      askInsight: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('InsightsPage', () => {
  it('shows the summary and the ask box', async () => {
    mockedApi.getMonthlySummary.mockResolvedValue({
      month: '2026-06',
      currency: 'AUD',
      totals: { income: 5000, expenses: 4120, net: 880, transactionCount: 78 },
      headline: 'A steady month.',
      insights: [],
    });
    renderWithProviders(<InsightsPage />);

    expect(screen.getByRole('heading', { name: 'Insights' })).toBeInTheDocument();
    expect(screen.getByLabelText('Question')).toBeInTheDocument();
    expect(await screen.findByText('A steady month.')).toBeInTheDocument();
  });
});
