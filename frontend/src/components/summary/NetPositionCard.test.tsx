import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { NetPositionCard } from './NetPositionCard';
import { api } from '../../api/client';
import type { HomeLoan } from '../../api/types';
import { renderWithProviders } from '../../test/utils';

vi.mock('../../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), getHomeLoan: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

const loan: HomeLoan = {
  hasHomeLoan: true, lender: null, loanAmount: 500000, interestRate: 6.25,
  repaymentFrequency: 'MONTHLY', repaymentAmount: 3079, hasOffset: true,
  offsetBalance: 120000, ownership: 'JOINT', currency: 'AUD', notes: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('NetPositionCard', () => {
  it('shows offset savings minus the loan balance', async () => {
    mockedApi.getHomeLoan.mockResolvedValue(loan);
    renderWithProviders(<NetPositionCard />);

    // 120,000 − 500,000 = −380,000
    expect(await screen.findByTestId('net-position')).toHaveTextContent(/380,000/);
    expect(screen.getByText(/\+.*120,000/)).toBeInTheDocument();  // offset
    expect(screen.getByText(/−.*500,000/)).toBeInTheDocument();   // loan
  });

  it('renders nothing without a home loan', async () => {
    mockedApi.getHomeLoan.mockResolvedValue({ ...loan, hasHomeLoan: false, loanAmount: null });
    const { container } = renderWithProviders(<NetPositionCard />);

    await waitFor(() => expect(mockedApi.getHomeLoan).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
