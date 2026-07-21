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
      <Kpi label="Income" value={formatMoney(income, currency)} tone="income"
           icon={<ArrowUpRight className="size-4" aria-hidden="true" />} />
      <Kpi label="Expenses" value={formatMoney(expenses, currency)} tone="expense"
           icon={<ArrowDownRight className="size-4" aria-hidden="true" />} />
      <Kpi label="Net" value={formatMoney(net, currency)} tone={net >= 0 ? 'income' : 'expense'}
           icon={<Wallet className="size-4" aria-hidden="true" />} />
    </div>
  );
}

/** Shown until the first statement is imported — the totals have nothing to show yet. */
function ImportBanner() {
  return (
    <Card className="border-primary/30 bg-primary/[0.03]">
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

function Kpi({ label, value, tone, icon }: {
  label: string; value: string; tone: 'income' | 'expense'; icon: ReactNode;
}) {
  return (
    <Card>
      <CardContent className="flex items-center gap-3 py-5">
        <span className={cn(
          'flex size-9 items-center justify-center rounded-full',
          tone === 'income' ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                            : 'bg-red-500/10 text-red-600 dark:text-red-400',
        )}>
          {icon}
        </span>
        <div>
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className="text-lg font-semibold tabular-nums" data-testid={`kpi-${label.toLowerCase()}`}>
            {value}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
