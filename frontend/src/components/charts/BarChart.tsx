import { EXPENSE_COLOR, INCOME_COLOR } from './palette';
import { formatMonth } from '@/lib/format';

export interface MonthlyBar {
  month: string;
  income: number;
  expenses: number;
}

interface BarChartProps {
  data: MonthlyBar[];
  formatValue?: (value: number) => string;
  /** show at most this many of the most-recent months (default 12) */
  maxMonths?: number;
}

// Fixed plot height in px. Percentage heights would need a definite-height
// parent; a fixed px plot keeps the bars reliable regardless of layout.
const PLOT_HEIGHT = 160;

/**
 * Grouped vertical bars — income vs expenses per month — built with sized divs
 * (no chart library). Bars are sized in pixels against the largest value in the
 * series, so the tallest fills the plot and small non-zero values stay visible.
 */
export function BarChart({ data, formatValue, maxMonths = 12 }: BarChartProps) {
  const fmt = formatValue ?? ((v: number) => v.toFixed(2));

  if (data.length === 0) {
    return <p className="text-sm text-muted-foreground">No monthly activity yet.</p>;
  }

  // data is oldest→newest; keep the most recent window so bars stay legible
  const months = data.slice(-maxMonths);
  const max = Math.max(1, ...months.flatMap((d) => [d.income, d.expenses]));

  // 0 → no bar; any positive value → at least 2px so it doesn't vanish
  const barPx = (value: number) => (value <= 0 ? 0 : Math.max(2, Math.round((value / max) * PLOT_HEIGHT)));

  return (
    <div>
      <div className="mb-3 flex gap-4 text-xs text-muted-foreground">
        <Legend color={INCOME_COLOR} label="Income" />
        <Legend color={EXPENSE_COLOR} label="Expenses" />
      </div>

      {/* columns flex to fit the card width — no forced min-width / scrollbar */}
      <div className="flex items-end gap-2" role="img" aria-label="Monthly income and expenses">
        {months.map((d) => (
          <div key={d.month} className="flex min-w-0 flex-1 flex-col items-center gap-1">
            <div className="flex items-end justify-center gap-1" style={{ height: PLOT_HEIGHT }}>
              <Bar px={barPx(d.income)} color={INCOME_COLOR}
                   title={`${formatMonth(d.month)} · income ${fmt(d.income)}`} />
              <Bar px={barPx(d.expenses)} color={EXPENSE_COLOR}
                   title={`${formatMonth(d.month)} · expenses ${fmt(d.expenses)}`} />
            </div>
            <span className="whitespace-nowrap text-[11px] text-muted-foreground">
              {formatMonth(d.month)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Bar({ px, color, title }: { px: number; color: string; title: string }) {
  return (
    <div
      className="w-3 rounded-t-sm"
      style={{ height: `${px}px`, backgroundColor: color }}
      title={title}
    />
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="inline-block size-2.5 rounded-sm" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
