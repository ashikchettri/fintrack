import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ShareToggle } from './ShareToggle';
import { api } from '../api/client';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), setTransactionVisibility: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('ShareToggle', () => {
  it('marks a personal transaction as shared', async () => {
    mockedApi.setTransactionVisibility.mockResolvedValue({} as never);
    renderWithProviders(<ShareToggle id="t1" visibility="personal" />);

    const button = screen.getByRole('button', { name: /mark as shared/i });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(button);

    await waitFor(() =>
      expect(mockedApi.setTransactionVisibility).toHaveBeenCalledWith('t1', 'shared'),
    );
  });

  it('un-shares a shared transaction', async () => {
    mockedApi.setTransactionVisibility.mockResolvedValue({} as never);
    renderWithProviders(<ShareToggle id="t1" visibility="shared" />);

    const button = screen.getByRole('button', { name: /click to make personal/i });
    expect(button).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(button);

    await waitFor(() =>
      expect(mockedApi.setTransactionVisibility).toHaveBeenCalledWith('t1', 'personal'),
    );
  });
});
