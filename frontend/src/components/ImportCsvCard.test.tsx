import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import { ImportCsvCard } from './ImportCsvCard';
import { ApiError, api } from '../api/client';
import type { ImportSummary } from '../api/types';
import { renderWithProviders } from '../test/utils';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), importTransactions: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);
const mockedToast = vi.mocked(toast);

const SUMMARY: ImportSummary = {
  importId: 'i1', fileName: 'statement.csv', currency: 'AUD',
  rowsParsed: 8, imported: 6, duplicatesSkipped: 1, failedRows: 1,
  accountsCreated: ['Everyday'], dateFrom: '2025-08-17', dateTo: '2026-07-11',
  totalIncome: 3040, totalExpenses: 217.7, net: 2822.3, errors: [{ rowNumber: 7, message: 'bad date' }],
};

const csv = () => new File(['Date,Description,Amount\n'], 'statement.csv', { type: 'text/csv' });

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('ImportCsvCard', () => {
  it('uploads a chosen file and toasts a summary of what landed', async () => {
    mockedApi.importTransactions.mockResolvedValue(SUMMARY);
    renderWithProviders(<ImportCsvCard />);

    await userEvent.upload(screen.getByLabelText('Choose a CSV file'), csv());

    await waitFor(() => expect(mockedApi.importTransactions).toHaveBeenCalledWith(expect.any(File), 'AUD'));
    await waitFor(() =>
      expect(mockedToast.success).toHaveBeenCalledWith(
        'Imported 6 transactions',
        expect.objectContaining({
          description: expect.stringContaining('1 duplicate skipped'),
        }),
      ),
    );
  });

  it('sends the selected currency', async () => {
    mockedApi.importTransactions.mockResolvedValue({ ...SUMMARY, imported: 0, duplicatesSkipped: 0, accountsCreated: [], failedRows: 0 });
    renderWithProviders(<ImportCsvCard />);

    await userEvent.selectOptions(screen.getByLabelText('Statement currency'), 'USD');
    await userEvent.upload(screen.getByLabelText('Choose a CSV file'), csv());

    await waitFor(() => expect(mockedApi.importTransactions).toHaveBeenCalledWith(expect.any(File), 'USD'));
    // no imports, no dups → the fallback detail (rows processed)
    await waitFor(() =>
      expect(mockedToast.success).toHaveBeenCalledWith('No new transactions', expect.anything()),
    );
  });

  it('imports on drop', async () => {
    mockedApi.importTransactions.mockResolvedValue(SUMMARY);
    renderWithProviders(<ImportCsvCard />);

    const dropzone = screen.getByTestId('csv-dropzone');
    fireEvent.dragOver(dropzone);
    fireEvent.dragLeave(dropzone);
    fireEvent.drop(dropzone, { dataTransfer: { files: [csv()] } });

    await waitFor(() => expect(mockedApi.importTransactions).toHaveBeenCalledTimes(1));
  });

  it('toasts an error when the import fails', async () => {
    mockedApi.importTransactions.mockRejectedValue(
      new ApiError(400, { title: 'CSV import failed', status: 400, detail: 'The uploaded file is empty.' }),
    );
    renderWithProviders(<ImportCsvCard />);

    await userEvent.upload(screen.getByLabelText('Choose a CSV file'), csv());

    await waitFor(() =>
      expect(mockedToast.error).toHaveBeenCalledWith(
        'Could not import that file',
        expect.objectContaining({ description: 'The uploaded file is empty.' }),
      ),
    );
  });

  it('hides the description in compact mode', () => {
    renderWithProviders(<ImportCsvCard compact />);
    expect(screen.queryByText(/accounts are created automatically/i)).not.toBeInTheDocument();
  });
});
