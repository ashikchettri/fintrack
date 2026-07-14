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
}

/**
 * Grouped vertical bars — income vs expenses per month — built with sized divs
 * (no chart library). Heights scale to the largest value across the series so
 * the tallest bar fills the plot.
 */
export function BarChart({ data, formatValue }: BarChartProps) {
  const fmt = formatValue ?? ((v: number) => v.toFixed(2));
  const max = Math.max(1, ...data.flatMap((d) => [d.income, d.expenses]));

  if (data.length === 0) {
    return <p className="text-sm text-muted-foreground">No monthly activity yet.</p>;
  }

  return (
    <div>
      <div className="mb-3 flex gap-4 text-xs text-muted-foreground">
        <Legend color={INCOME_COLOR} label="Income" />
        <Legend color={EXPENSE_COLOR} label="Expenses" />
      </div>

      <div className="flex h-40 items-end gap-3 overflow-x-auto pb-1" role="img"
           aria-label="Monthly income and expenses">
        {data.map((d) => (
          <div key={d.month} className="flex min-w-10 flex-1 flex-col items-center gap-1">
            <div className="flex h-full w-full items-end justify-center gap-1">
              <Bar heightPct={(d.income / max) * 100} color={INCOME_COLOR}
                   title={`${formatMonth(d.month)} · income ${fmt(d.income)}`} />
              <Bar heightPct={(d.expenses / max) * 100} color={EXPENSE_COLOR}
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

function Bar({ heightPct, color, title }: { heightPct: number; color: string; title: string }) {
  return (
    <div
      className="w-3 rounded-t-sm transition-[height]"
      style={{ height: `${Math.max(heightPct, 1.5)}%`, backgroundColor: color }}
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
