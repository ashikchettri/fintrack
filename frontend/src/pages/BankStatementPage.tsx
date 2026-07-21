import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { DashboardResponse } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ImportCsvCard } from '@/components/ImportCsvCard';
import { SharedCommitmentsCard } from '@/components/SharedCommitmentsCard';
import { BudgetVsActualCard } from '@/components/BudgetVsActualCard';
import { ShareToggle } from '@/components/ShareToggle';
import { DonutChart } from '@/components/charts/DonutChart';
import { BarChart } from '@/components/charts/BarChart';
import { formatDate, formatMonth, formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

/** The imported bank data in detail: charts, merchants, transactions, sharing. */
export default function BankStatementPage() {
  const [month, setMonth] = useState<string | null>(null);
  const { data, isPending, isError } = useQuery({
    queryKey: ['dashboard', month],
    queryFn: () => api.dashboard(month ?? undefined),
    retry: false,
    placeholderData: (prev) => prev,
  });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your statement…</p></AppShell>;
  }
  if (isError || !data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your statement.</Alert></AppShell>;
  }

  const firstRun = data.totals.transactionCount === 0 && month === null && data.availableMonths.length === 0;

  return (
    <AppShell>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Bank &amp; statement</h1>
          <p className="text-sm text-muted-foreground">
            {firstRun ? 'Import a bank statement to get started.' : 'Your imported transactions in detail.'}
          </p>
        </div>
        {!firstRun && <MonthSelect months={data.availableMonths} value={month} onChange={setMonth} />}
      </div>

      {firstRun ? <EmptyState /> : <Populated data={data} month={month} />}
    </AppShell>
  );
}

function MonthSelect({ months, value, onChange }: {
  months: string[]; value: string | null; onChange: (v: string | null) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <span className="text-muted-foreground">Period</span>
      <select
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        aria-label="Period"
        className="rounded-md border bg-background px-2 py-1.5 text-sm"
      >
        <option value="">All time</option>
        {months.map((m) => <option key={m} value={m}>{formatMonth(m)}</option>)}
      </select>
    </label>
  );
}

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

function Populated({ data, month }: { data: DashboardResponse; month: string | null }) {
  const currency = data.currency;
  const { expenses } = data.totals;

  return (
    <div className="space-y-6">
      <BudgetVsActualCard />
      <SharedCommitmentsCard month={month} />

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-2">
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
