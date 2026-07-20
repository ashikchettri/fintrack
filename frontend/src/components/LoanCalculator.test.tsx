import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoanCalculator } from './LoanCalculator';

describe('LoanCalculator', () => {
  it('prefills from the saved loan and shows the payoff summary + chart', () => {
    render(<LoanCalculator loanAmount={500000} interestRate={6.25} currency="AUD" />);

    // prefilled inputs
    expect(screen.getByLabelText('Loan amount')).toHaveValue(500000);
    expect(screen.getByLabelText('Interest rate (% p.a.)')).toHaveValue(6.25);
    expect(screen.getByLabelText('Loan term (years)')).toHaveValue(30);

    // summary stats
    expect(screen.getByText('Minimum repayment')).toBeInTheDocument();
    expect(screen.getByText('Total interest')).toBeInTheDocument();
    expect(screen.getByText('Paid off in')).toBeInTheDocument();

    // the balance chart is drawn (one line for the minimum-repayment scenario)
    expect(screen.getByRole('img', { name: /loan balance over time/i })).toBeInTheDocument();

    // no savings block until the user adds an extra repayment
    expect(screen.queryByTestId('extra-savings')).not.toBeInTheDocument();
  });

  it('shows interest and time saved once an extra repayment is entered', async () => {
    render(<LoanCalculator loanAmount={500000} interestRate={6.25} currency="AUD" />);

    const extra = screen.getByLabelText('Extra repayment / month');
    await userEvent.clear(extra);
    await userEvent.type(extra, '500');

    const savings = await screen.findByTestId('extra-savings');
    expect(savings).toBeInTheDocument();
    expect(screen.getByText('Interest saved')).toBeInTheDocument();
    expect(screen.getByText('Paid off sooner')).toBeInTheDocument();
  });

  it('prompts for inputs when nothing is prefilled', () => {
    render(<LoanCalculator loanAmount={null} interestRate={null} currency="AUD" />);
    expect(screen.getByText(/enter a loan amount, interest rate and term/i)).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /loan balance/i })).not.toBeInTheDocument();
  });
});
