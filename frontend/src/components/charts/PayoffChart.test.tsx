import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PayoffChart } from './PayoffChart';
import type { AmortPoint } from '@/lib/finance';

function ramp(months: number): AmortPoint[] {
  // a simple descending balance from 1000 → 0 over `months`
  return Array.from({ length: months }, (_, i) => ({
    month: i + 1,
    balance: 1000 - ((i + 1) / months) * 1000,
    interestPaid: (i + 1) * 5,
  }));
}

describe('PayoffChart', () => {
  it('draws one line per scenario with legends', () => {
    const { container } = render(
      <PayoffChart
        series={[
          { points: ramp(360), color: '#6366f1', label: 'Minimum repayments' },
          { points: ramp(300), color: '#10b981', label: 'With extra' },
        ]}
        xMaxMonths={360}
        yMax={1000}
        formatValue={(v) => `$${Math.round(v)}`}
      />,
    );

    expect(screen.getByText('Minimum repayments')).toBeInTheDocument();
    expect(screen.getByText('With extra')).toBeInTheDocument();
    // two polylines, one per series
    expect(container.querySelectorAll('polyline')).toHaveLength(2);
    // year axis labels (30y term)
    expect(screen.getByText('30y')).toBeInTheDocument();
    // a y-axis label rendered via the formatter
    expect(screen.getByText('$1000')).toBeInTheDocument();
  });

  it('shows a hint when there is nothing to plot', () => {
    render(<PayoffChart series={[]} xMaxMonths={0} yMax={0} formatValue={(v) => `$${v}`} />);
    expect(screen.getByText(/enter a loan amount and rate/i)).toBeInTheDocument();
  });
});
