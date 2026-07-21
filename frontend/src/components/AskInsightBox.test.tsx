import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AskInsightBox } from './AskInsightBox';
import { ApiError, api } from '../api/client';
import { renderWithProviders } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: { refresh: vi.fn().mockResolvedValue(false), me: vi.fn(), askInsight: vi.fn() },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('AskInsightBox', () => {
  it('sends the question and shows the grounded answer', async () => {
    mockedApi.askInsight.mockResolvedValue({
      question: 'How much on food in June?',
      answer: 'You spent AUD 1,240 on Groceries & Food in June.',
    });
    renderWithProviders(<AskInsightBox />);

    await userEvent.type(screen.getByLabelText('Question'), 'How much on food in June?');
    await userEvent.click(screen.getByRole('button'));

    expect(await screen.findByTestId('insight-answer'))
      .toHaveTextContent('You spent AUD 1,240 on Groceries & Food in June.');
    expect(mockedApi.askInsight).toHaveBeenCalledWith('How much on food in June?');
  });

  it('shows a gentle note when AI answers are not enabled (503)', async () => {
    mockedApi.askInsight.mockRejectedValue(
      new ApiError(503, { title: 'AI not configured', status: 503, detail: 'off' }),
    );
    renderWithProviders(<AskInsightBox />);

    await userEvent.type(screen.getByLabelText('Question'), 'anything');
    await userEvent.click(screen.getByRole('button'));

    expect(await screen.findByText(/aren.t enabled here yet/i)).toBeInTheDocument();
    expect(screen.queryByTestId('insight-answer')).not.toBeInTheDocument();
  });

  it('shows a retry message on other errors', async () => {
    mockedApi.askInsight.mockRejectedValue(
      new ApiError(502, { title: 'Bad gateway', status: 502 }),
    );
    renderWithProviders(<AskInsightBox />);

    await userEvent.type(screen.getByLabelText('Question'), 'anything');
    await userEvent.click(screen.getByRole('button'));

    expect(await screen.findByText(/couldn.t get an answer/i)).toBeInTheDocument();
  });

  it('does not submit an empty question', async () => {
    renderWithProviders(<AskInsightBox />);
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
