import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DonutChart } from './DonutChart';

describe('DonutChart', () => {
  it('renders a slice per category with values and shares', () => {
    render(
      <DonutChart
        data={[
          { label: 'Transportation', value: 132.5 },
          { label: 'Food & Drink', value: 85.2 },
        ]}
        formatValue={(v) => `$${v.toFixed(2)}`}
      />,
    );

    // legend entries
    expect(screen.getByText('Transportation')).toBeInTheDocument();
    expect(screen.getByText('$132.50')).toBeInTheDocument();
    // shares: 132.5 / 217.7 ≈ 61%, 85.2 / 217.7 ≈ 39%
    expect(screen.getByText('61%')).toBeInTheDocument();
    expect(screen.getByText('39%')).toBeInTheDocument();

    // accessible summary on the svg
    expect(screen.getByRole('img')).toHaveAttribute(
      'aria-label',
      expect.stringContaining('Transportation 61%'),
    );
  });

  it('groups the long tail into a single "Other" slice', () => {
    // 9 categories (values 90,80,…,10) → top 7 kept, last 2 rolled into Other
    const data = Array.from({ length: 9 }, (_, i) => ({ label: `Cat${i}`, value: 90 - i * 10 }));
    render(<DonutChart data={data} formatValue={(v) => `$${v}`} />);

    expect(screen.getByText('Other (2)')).toBeInTheDocument();
    expect(screen.getByText('Cat0')).toBeInTheDocument();        // biggest kept
    expect(screen.queryByText('Cat8')).not.toBeInTheDocument();  // smallest grouped away
  });

  it('shows a message when there is nothing to chart', () => {
    render(<DonutChart data={[]} />);
    expect(screen.getByText(/no spending to chart/i)).toBeInTheDocument();
  });
});
