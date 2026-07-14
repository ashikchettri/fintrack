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
    const tallest = screen.getByTitle('Jun 2026 · income $3000.00');
    expect(tallest).toBeInTheDocument();
    // regression guard: the largest value must fill the plot (not collapse to 0)
    expect(tallest).toHaveStyle({ height: '160px' });
    // a zero value renders no bar (July has no income)
    expect(screen.getByTitle('Jul 2026 · income $0.00')).toHaveStyle({ height: '0px' });
  });

  it('shows only the most recent months when there are many', () => {
    // 14 months of history (Jan 2025 → Feb 2026), cap at 12 → the two oldest drop off
    const data = Array.from({ length: 14 }, (_, i) => {
      const year = 2025 + Math.floor(i / 12);
      const month = (i % 12) + 1;
      return { month: `${year}-${String(month).padStart(2, '0')}`, income: 100, expenses: 50 };
    });
    render(<BarChart data={data} maxMonths={12} />);

    expect(screen.queryByText('Jan 2025')).not.toBeInTheDocument(); // oldest, dropped
    expect(screen.queryByText('Feb 2025')).not.toBeInTheDocument();
    expect(screen.getByText('Mar 2025')).toBeInTheDocument();       // 12 kept: Mar–Dec + …
  });

  it('shows a message when there is no monthly activity', () => {
    render(<BarChart data={[]} />);
    expect(screen.getByText(/no monthly activity/i)).toBeInTheDocument();
  });
});
