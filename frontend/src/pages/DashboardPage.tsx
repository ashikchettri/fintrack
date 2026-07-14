import { useQuery } from '@tanstack/react-query';
import { ArrowDownRight, ArrowUpRight, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '../api/client';
import type { DashboardResponse } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ImportCsvCard } from '@/components/ImportCsvCard';
import { SharedCommitmentsCard } from '@/components/SharedCommitmentsCard';
import { ShareToggle } from '@/components/ShareToggle';
import { DonutChart } from '@/components/charts/DonutChart';
import { BarChart } from '@/components/charts/BarChart';
import { formatDate, formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

export default function DashboardPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: api.dashboard,
    retry: false,
  });

  if (isPending) {
    return (
      <AppShell>
        <p className="text-muted-foreground">Loading your dashboard…</p>
      </AppShell>
    );
  }
  if (isError || !data) {
    return (
      <AppShell>
        <Alert role="alert" className="max-w-md">Could not load your dashboard.</Alert>
      </AppShell>
    );
  }

  const empty = data.totals.transactionCount === 0;

  return (
    <AppShell>
      <div className="mb-6 flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
          <p className="text-sm text-muted-foreground">
            {empty ? 'Start by importing a bank statement.' : 'Your money at a glance.'}
          </p>
        </div>
      </div>

      {empty ? <EmptyState /> : <Populated data={data} />}
    </AppShell>
  );
}

/** First-run: one clear call to action — the whole point of the hero feature. */
function EmptyState() {
  return (
    <div className="mx-auto max-w-lg">
      <div className="mb-6 text-center">
        <p className="text-muted-foreground">
          Upload a CSV export from your bank and we&apos;ll build your dashboard instantly —
          categories, trends and top merchants, with no manual entry.
        </p>
      </div>
      <ImportCsvCard />
    </div>
  );
}

function Populated({ data }: { data: DashboardResponse }) {
  const currency = data.currency;
  const { income, expenses, net } = data.totals;

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Kpi label="Income" value={formatMoney(income, currency)} tone="income"
             icon={<ArrowUpRight className="size-4" aria-hidden="true" />} />
        <Kpi label="Expenses" value={formatMoney(expenses, currency)} tone="expense"
             icon={<ArrowDownRight className="size-4" aria-hidden="true" />} />
        <Kpi label="Net" value={formatMoney(net, currency)} tone={net >= 0 ? 'income' : 'expense'}
             icon={<Wallet className="size-4" aria-hidden="true" />} />
      </div>

      {/* the differentiator: household coordination, not just a statement view */}
      <SharedCommitmentsCard />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Spending by category</CardTitle>
            <CardDescription>Where your money went</CardDescription>
          </CardHeader>
          <CardContent>
            <DonutChart
              data={data.byCategory.map((c) => ({ label: c.category, value: c.spent }))}
              centerValue={formatMoney(expenses, currency)}
              centerLabel={`Total spend ${formatMoney(expenses, currency)}`}
              formatValue={(v) => formatMoney(v, currency)}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Monthly trend</CardTitle>
            <CardDescription>Income vs expenses</CardDescription>
          </CardHeader>
          <CardContent>
            <BarChart data={data.byMonth} formatValue={(v) => formatMoney(v, currency)} />
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Recent transactions</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <RecentTable data={data} />
          </CardContent>
        </Card>

        <div className="space-y-6">
          <TopMerchants data={data} />
          <ImportCsvCard compact />
        </div>
      </div>
    </div>
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

function TopMerchants({ data }: { data: DashboardResponse }) {
  if (data.topMerchants.length === 0) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Top merchants</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {data.topMerchants.map((m) => (
          <div key={m.description} className="flex items-center justify-between gap-2 text-sm">
            <span className="truncate">{m.description}</span>
            <span className="tabular-nums font-medium">{formatMoney(m.spent, data.currency)}</span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function RecentTable({ data }: { data: DashboardResponse }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b text-left text-xs text-muted-foreground">
            <th className="px-6 py-2 font-medium">Date</th>
            <th className="px-6 py-2 font-medium">Description</th>
            <th className="px-6 py-2 font-medium">Category</th>
            <th className="px-6 py-2 text-right font-medium">Amount</th>
            <th className="px-6 py-2 text-right font-medium">Share</th>
          </tr>
        </thead>
        <tbody>
          {data.recent.map((t) => (
            <tr key={t.id} className="border-b last:border-0">
              <td className="whitespace-nowrap px-6 py-2.5 text-muted-foreground">{formatDate(t.date)}</td>
              <td className="px-6 py-2.5">{t.description}</td>
              <td className="px-6 py-2.5 text-muted-foreground">{t.category ?? '—'}</td>
              <td className={cn(
                'whitespace-nowrap px-6 py-2.5 text-right tabular-nums font-medium',
                t.amount < 0 ? 'text-red-600 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400',
              )}>
                {formatMoney(t.amount, data.currency)}
              </td>
              <td className="px-6 py-2.5 text-right">
                <ShareToggle id={t.id} visibility={t.visibility} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
