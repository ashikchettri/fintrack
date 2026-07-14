import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BarChart } from './BarChart';

describe('BarChart', () => {
  it('renders a labelled column per month with income and expense bars', () => {
    render(
      <BarChart
        data={[
          { month: '2026-06', income: 3000, expenses: 200 },
          { month: '2026-07', income: 0, expenses: 12.5 },
        ]}
        formatValue={(v) => `$${v.toFixed(2)}`}
      />,
    );

    expect(screen.getByText('Jun 2026')).toBeInTheDocument();
    expect(screen.getByText('Jul 2026')).toBeInTheDocument();
    expect(screen.getByText('Income')).toBeInTheDocument();
    expect(screen.getByText('Expenses')).toBeInTheDocument();
    // a bar carries its value in the title for hover
    expect(screen.getByTitle('Jun 2026 · income $3000.00')).toBeInTheDocument();
  });

  it('shows a message when there is no monthly activity', () => {
    render(<BarChart data={[]} />);
    expect(screen.getByText(/no monthly activity/i)).toBeInTheDocument();
  });
});
