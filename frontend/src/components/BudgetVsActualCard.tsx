import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Scale } from 'lucide-react';
import { api } from '../api/client';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatMoney, formatMonth } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * The dashboard rollup: the household budget (plan) vs the latest month's actual
 * spending (reality) — so the dashboard is a financial position, not just the
 * imported bank feed. Self-fetching.
 */
export function BudgetVsActualCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['overview'],
    queryFn: api.getOverview,
    retry: false,
  });

  if (isPending || isError || !data) return null;

  if (!data.hasBudget) {
    return (
      <Card>
        <CardContent className="flex items-center justify-between gap-3 py-4 text-sm">
          <span className="text-muted-foreground">
            Set a household budget to track your plan against what you actually spend.
          </span>
          <Link to="/budget" className="shrink-0 font-medium text-primary hover:underline">Create budget →</Link>
        </CardContent>
      </Card>
    );
  }

  // only categories that carry a plan or actual spend this month
  const breakdown = data.byCategory.filter((c) => c.planned > 0 || c.actual > 0);

  return (
    <Card className="border-primary/30 bg-primary/[0.03]">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <Scale className="size-4 text-primary" aria-hidden="true" />
          Budget vs actual
        </CardTitle>
        <CardDescription>
          {data.actualMonth ? `Your ${formatMonth(data.actualMonth)} spending against your monthly budget.`
                            : 'Import transactions to compare against your budget.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <CompareRow label="Income" planned={data.planned.income} actual={data.actual.income}
                    currency={data.currency} goodWhenOver />
        <CompareRow label="Expenses" planned={data.planned.expenses} actual={data.actual.expenses}
                    currency={data.currency} />

        {breakdown.length > 0 && (
          <div className="space-y-2.5 border-t pt-3" data-testid="category-breakdown">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">By category</p>
            {breakdown.map((c) => (
              <CategoryRow key={c.category} label={c.category} planned={c.planned}
                           actual={c.actual} currency={data.currency} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/** One canonical expense category: actual vs planned, over-budget in red. */
function CategoryRow({ label, planned, actual, currency }: {
  label: string; planned: number; actual: number; currency: string;
}) {
  const good = actual - planned <= 0;             // under or on budget
  const pct = planned > 0 ? Math.min((actual / planned) * 100, 100) : actual > 0 ? 100 : 0;

  return (
    <div>
      <div className="flex items-baseline justify-between gap-2 text-sm">
        <span className="truncate">{label}</span>
        <span className="shrink-0 tabular-nums text-xs text-muted-foreground">
          {formatMoney(actual, currency)} / {formatMoney(planned, currency)}
        </span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
        <div className={cn('h-full rounded-full', good ? 'bg-emerald-500' : 'bg-red-500')}
             style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

function CompareRow({ label, planned, actual, currency, goodWhenOver }: {
  label: string; planned: number; actual: number; currency: string; goodWhenOver?: boolean;
}) {
  const diff = actual - planned;
  // for expenses, over budget (diff>0) is bad; for income, over (diff>0) is good
  const good = goodWhenOver ? diff >= 0 : diff <= 0;
  const pct = planned > 0 ? Math.min((actual / planned) * 100, 100) : 0;

  return (
    <div>
      <div className="flex items-baseline justify-between text-sm">
        <span className="font-medium">{label}</span>
        <span className="tabular-nums text-muted-foreground">
          {formatMoney(actual, currency)} <span className="text-xs">of</span> {formatMoney(planned, currency)} / mo
        </span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
        <div className={cn('h-full rounded-full', good ? 'bg-emerald-500' : 'bg-red-500')}
             style={{ width: `${pct}%` }} />
      </div>
      {planned > 0 && (
        <p className={cn('mt-1 text-xs', good ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400')}
           data-testid={`compare-${label.toLowerCase()}`}>
          {label === 'Expenses'
            ? (diff <= 0 ? `${formatMoney(-diff, currency)} under budget` : `${formatMoney(diff, currency)} over budget`)
            : (diff >= 0 ? `${formatMoney(diff, currency)} above plan` : `${formatMoney(-diff, currency)} below plan`)}
        </p>
      )}
    </div>
  );
}
