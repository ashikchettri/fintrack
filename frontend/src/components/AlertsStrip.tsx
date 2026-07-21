import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, HandCoins } from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '../api/client';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

interface Alert {
  id: string;
  tone: 'warn' | 'info';
  icon: ReactNode;
  message: string;
  to: string;
}

/**
 * The "what needs attention" strip at the top of the dashboard: over-budget
 * spending, negative cash flow, and unsettled shared costs, derived from data
 * the other cards already load. Renders nothing when all is well.
 */
export function AlertsStrip() {
  const overview = useQuery({ queryKey: ['overview'], queryFn: api.getOverview, retry: false });
  const cashFlow = useQuery({ queryKey: ['cash-flow'], queryFn: api.getCashFlow, retry: false });
  const shared = useQuery({
    queryKey: ['household', null],
    queryFn: () => api.householdShared(),
    retry: false,
  });

  const alerts: Alert[] = [];

  const ov = overview.data;
  if (ov?.hasBudget && ov.planned.expenses > 0 && ov.actual.expenses > ov.planned.expenses) {
    alerts.push({
      id: 'over-budget',
      tone: 'warn',
      icon: <AlertTriangle className="size-4" aria-hidden="true" />,
      message: `You're ${formatMoney(ov.actual.expenses - ov.planned.expenses, ov.currency)} over budget this month.`,
      to: '/budget',
    });
  }

  const cf = cashFlow.data;
  if (cf && cf.monthlySurplus < 0) {
    alerts.push({
      id: 'negative-cashflow',
      tone: 'warn',
      icon: <AlertTriangle className="size-4" aria-hidden="true" />,
      message: `You're spending ${formatMoney(-cf.monthlySurplus, cf.currency)} more than you earn each month.`,
      to: '/cash-flow',
    });
  }

  const settlement = shared.data?.settlement;
  if (settlement && settlement.status === 'owes' && settlement.amount > 0) {
    alerts.push({
      id: 'settle-up',
      tone: 'info',
      icon: <HandCoins className="size-4" aria-hidden="true" />,
      message: `You owe ${formatMoney(settlement.amount, shared.data?.currency)} toward shared costs.`,
      to: '/bank',
    });
  }

  if (alerts.length === 0) return null;

  return (
    <div className="space-y-2" role="region" aria-label="Alerts" data-testid="alerts-strip">
      {alerts.map((a) => (
        <Link
          key={a.id}
          to={a.to}
          className={cn(
            'flex items-center justify-between gap-3 rounded-lg border px-4 py-2.5 text-sm transition-colors',
            a.tone === 'warn'
              ? 'border-amber-500/30 bg-amber-500/10 text-amber-700 hover:bg-amber-500/15 dark:text-amber-300'
              : 'border-primary/30 bg-primary/[0.06] text-foreground hover:bg-primary/10',
          )}
        >
          <span className="flex items-center gap-2">
            {a.icon}
            {a.message}
          </span>
          <ArrowRight className="size-4 shrink-0 opacity-60" aria-hidden="true" />
        </Link>
      ))}
    </div>
  );
}
