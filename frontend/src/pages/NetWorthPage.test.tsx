import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import NetWorthPage from './NetWorthPage';
import { api } from '../api/client';
import { renderWithProviders } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      getNetWorth: vi.fn(),
      getNetWorthItems: vi.fn(),
      saveNetWorthItems: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
  mockedApi.getNetWorth.mockResolvedValue({
    currency: 'AUD', totalAssets: 800000, totalLiabilities: 500000, netWorth: 300000,
    assets: [{ name: 'Home', category: 'Property', value: 800000, source: 'MANUAL' }],
    liabilities: [{ name: 'Home loan', category: 'Mortgage', value: 500000, source: 'HOME_LOAN' }],
  });
  mockedApi.getNetWorthItems.mockResolvedValue({
    currency: 'AUD',
    items: [{ kind: 'ASSET', category: 'Property', name: 'Home', value: 800000 }],
  });
});

describe('NetWorthPage', () => {
  it('shows the summary (with the home-loan line marked) and the editor', async () => {
    renderWithProviders(<NetWorthPage />, ['/net-worth']);

    expect(await screen.findByTestId('net-worth-total')).toHaveTextContent(/300,000/);
    // the folded home-loan liability is flagged as derived
    expect(screen.getByText(/\(home loan\)/i)).toBeInTheDocument();
    // the saved asset seeds an editable row
    await waitFor(() => expect((screen.getByLabelText('Assets name') as HTMLInputElement).value).toBe('Home'));
  });

  it('adds a liability and saves the balance sheet', async () => {
    mockedApi.saveNetWorthItems.mockResolvedValue({ currency: 'AUD', items: [] });
    renderWithProviders(<NetWorthPage />, ['/net-worth']);

    await screen.findByTestId('net-worth-total');
    await userEvent.click(screen.getByRole('button', { name: /add liability/i }));
    await userEvent.type(screen.getByLabelText('Liabilities name'), 'Visa');
    await userEvent.type(screen.getByLabelText('Liabilities value'), '3000');
    await userEvent.click(screen.getByRole('button', { name: /save net worth/i }));

    await waitFor(() => expect(mockedApi.saveNetWorthItems).toHaveBeenCalled());
    const [items] = mockedApi.saveNetWorthItems.mock.calls[0];
    expect(items).toEqual(expect.arrayContaining([
      { kind: 'ASSET', category: 'Property', name: 'Home', value: 800000 },
      { kind: 'LIABILITY', category: null, name: 'Visa', value: 3000 },
    ]));
    await waitFor(() => expect(vi.mocked(toast).success).toHaveBeenCalled());
  });
});
