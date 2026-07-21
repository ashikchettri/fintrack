import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { NetWorthCard } from './NetWorthCard';
import { api } from '../../api/client';
import type { NetWorth } from '../../api/types';
import { renderWithProviders } from '../../test/utils';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), getNetWorth: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const summary: NetWorth = {
  currency: 'AUD', totalAssets: 920000, totalLiabilities: 503000, netWorth: 417000,
  assets: [{ name: 'Home', category: 'Property', value: 800000, source: 'MANUAL' }],
  liabilities: [{ name: 'Home loan', category: 'Mortgage', value: 500000, source: 'HOME_LOAN' }],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('NetWorthCard', () => {
  it('shows net worth with assets and liabilities', async () => {
    mockedApi.getNetWorth.mockResolvedValue(summary);
    renderWithProviders(<NetWorthCard />);

    expect(await screen.findByTestId('net-worth')).toHaveTextContent(/417,000/);
    expect(screen.getByText(/\+.*920,000/)).toBeInTheDocument();
    expect(screen.getByText(/−.*503,000/)).toBeInTheDocument();
  });

  it('renders nothing when there is no balance sheet', async () => {
    mockedApi.getNetWorth.mockResolvedValue({
      currency: 'AUD', totalAssets: 0, totalLiabilities: 0, netWorth: 0, assets: [], liabilities: [],
    });
    const { container } = renderWithProviders(<NetWorthCard />);

    await waitFor(() => expect(mockedApi.getNetWorth).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
