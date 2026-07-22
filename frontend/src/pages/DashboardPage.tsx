import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowDownRight, ArrowUpRight, Import, MessageCircleQuestion, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '../api/client';
import type { DashboardResponse } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Card, CardContent } from '@/components/ui/card';
import { AlertsStrip } from '@/components/AlertsStrip';
import { ExpenseBreakdownCard } from '@/components/ExpenseBreakdownCard';
import { InsightsSummaryCard } from '@/components/InsightsSummaryCard';
import { NetWorthCard } from '@/components/summary/NetWorthCard';
import { CashFlowSummaryCard } from '@/components/summary/CashFlowSummaryCard';
import { HomeLoanSummaryCard } from '@/components/summary/HomeLoanSummaryCard';
import { BudgetSummaryCard } from '@/components/summary/BudgetSummaryCard';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * The overview: your whole financial picture at a glance — headline bank totals,
 * an AI summary, and one-line rollups of cash flow, home loan, and budget. Each
 * card links to its detail page; the raw statement lives on Bank & statement.
 */
export default function DashboardPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['dashboard', null],
    queryFn: () => api.dashboard(),
    retry: false,
  });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your dashboard…</p></AppShell>;
  }
  if (isError || !data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your dashboard.</Alert></AppShell>;
  }

  const hasBankData = data.totals.transactionCount > 0;

  return (
    <AppShell>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">Your whole financial picture at a glance.</p>
      </div>

      <div className="space-y-6">
        <AlertsStrip />

        {hasBankData ? <KpiRow data={data} /> : <ImportBanner />}

        <NetWorthCard />

        <section aria-label="Spending insights" className="space-y-2">
          <InsightsSummaryCard />
          <div className="flex justify-end">
            <Link
              to="/insights"
              className="flex items-center gap-1 text-sm font-medium text-primary hover:underline"
            >
              <MessageCircleQuestion className="size-4" aria-hidden="true" />
              Ask about your spending
            </Link>
          </div>
        </section>

        <ExpenseBreakdownCard />

        <div className="grid grid-cols-1 items-stretch gap-4 md:grid-cols-2 lg:grid-cols-3">
          <CashFlowSummaryCard />
          <HomeLoanSummaryCard />
          <BudgetSummaryCard />
        </div>
      </div>
    </AppShell>
  );
}

function KpiRow({ data }: { data: DashboardResponse }) {
  const { income, expenses, net } = data.totals;
  const currency = data.currency;
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
      <Kpi label="Income" value={formatMoney(income, currency)} color="emerald"
           icon={<ArrowUpRight className="size-5" aria-hidden="true" />} />
      <Kpi label="Expenses" value={formatMoney(expenses, currency)} color="rose"
           icon={<ArrowDownRight className="size-5" aria-hidden="true" />} />
      <Kpi label="Net" value={formatMoney(net, currency)} color="indigo"
           icon={<Wallet className="size-5" aria-hidden="true" />} />
    </div>
  );
}

const KPI_COLOR = {
  emerald: { bubble: 'bg-gradient-to-br from-emerald-400 to-emerald-600', card: 'border-emerald-500/20 bg-emerald-500/[0.04]' },
  rose: { bubble: 'bg-gradient-to-br from-rose-400 to-rose-600', card: 'border-rose-500/20 bg-rose-500/[0.04]' },
  indigo: { bubble: 'bg-gradient-to-br from-indigo-400 to-violet-600', card: 'border-indigo-500/20 bg-indigo-500/[0.04]' },
} as const;

/** Shown until the first statement is imported — the totals have nothing to show yet. */
function ImportBanner() {
  return (
    <Card className="border-primary/30 hero-gradient">
      <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
        <div className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Import className="size-4" aria-hidden="true" />
          </span>
          <p className="text-sm text-muted-foreground">
            Import a bank statement to see your income, expenses and net at a glance.
          </p>
        </div>
        <Link to="/bank" className="shrink-0 text-sm font-medium text-primary hover:underline">
          Import statement →
        </Link>
      </CardContent>
    </Card>
  );
}

function Kpi({ label, value, color, icon }: {
  label: string; value: string; color: keyof typeof KPI_COLOR; icon: ReactNode;
}) {
  const c = KPI_COLOR[color];
  return (
    <Card className={cn('transition-shadow hover:shadow-pop', c.card)}>
      <CardContent className="flex items-center gap-3 py-5">
        <span className={cn(
          'flex size-10 items-center justify-center rounded-xl text-white shadow-sm',
          c.bubble,
        )}>
          {icon}
        </span>
        <div>
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className="text-xl font-bold tabular-nums" data-testid={`kpi-${label.toLowerCase()}`}>
            {value}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
