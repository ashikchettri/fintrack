import { useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { PieChart } from 'lucide-react';
import { api } from '../api/client';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DonutChart } from '@/components/charts/DonutChart';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

type View = 'monthly' | 'yearly';

/**
 * A donut of the household's budgeted expenses by category (from Income &
 * expenses) — a quick "where does the money go", monthly or annualized. The
 * budget is stored monthly, so yearly is simply ×12. Self-fetching; hidden until
 * a budget with expenses exists.
 */
export function ExpenseBreakdownCard() {
  const [view, setView] = useState<View>('monthly');
  const { data, isPending, isError } = useQuery({
    queryKey: ['overview'],
    queryFn: api.getOverview,
    retry: false,
  });

  if (isPending || isError || !data || !data.hasBudget) return null;

  const factor = view === 'yearly' ? 12 : 1;
  const slices = data.byCategory
    .filter((c) => c.planned > 0)
    .map((c) => ({ label: c.category, value: c.planned * factor }));

  if (slices.length === 0) return null;

  const total = data.planned.expenses * factor;
  const fmt = (v: number) => formatMoney(v, data.currency);

  return (
    <Card className="border-primary/30 hero-gradient">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2 text-base">
              <PieChart className="size-4 text-primary" aria-hidden="true" />
              Where your money goes
            </CardTitle>
            <CardDescription>
              Your budgeted expenses by category — {view === 'yearly' ? 'per year' : 'per month'}.
            </CardDescription>
          </div>
          <div className="flex shrink-0 rounded-lg border p-0.5 text-xs" role="group" aria-label="Time range">
            <ToggleBtn active={view === 'monthly'} onClick={() => setView('monthly')}>Monthly</ToggleBtn>
            <ToggleBtn active={view === 'yearly'} onClick={() => setView('yearly')}>Yearly</ToggleBtn>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <DonutChart
          data={slices}
          formatValue={fmt}
          centerValue={fmt(total)}
          centerLabel={`Total budgeted expenses: ${fmt(total)}`}
        />
        <div className="mt-4 flex justify-end">
          <Link to="/budget" className="text-sm font-medium text-primary hover:underline">
            Edit budget →
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

function ToggleBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'rounded-md px-2.5 py-1 font-medium transition-colors',
        active ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground',
      )}
    >
      {children}
    </button>
  );
}
