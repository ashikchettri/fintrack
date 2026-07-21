import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { TrendingUp } from 'lucide-react';
import { api } from '../api/client';
import type { CashFlow } from '../api/types';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatMoney } from '@/lib/format';
import { monthlyRepayment } from '@/lib/finance';
import { cn } from '@/lib/utils';

export default function CashFlowPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['cash-flow'],
    queryFn: api.getCashFlow,
    retry: false,
  });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Working out your cash flow…</p></AppShell>;
  }
  if (isError || !data) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your cash flow.</Alert></AppShell>;
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-lg space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Cash flow</h1>
          <p className="text-sm text-muted-foreground">What&apos;s left each month — and what you can afford.</p>
        </div>
        <Summary data={data} />
        <Affordability data={data} />
      </div>
    </AppShell>
  );
}

function Summary({ data }: { data: CashFlow }) {
  const c = data.currency;
  const positive = data.monthlySurplus >= 0;
  return (
    <Card className="border-primary/30 hero-gradient">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <TrendingUp className="size-4 text-primary" aria-hidden="true" />
          Monthly cash flow
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Row label="Household income" value={`${formatMoney(data.monthlyIncome, c)} / mo`} />
        <Row label="Recent spending" value={`${formatMoney(data.monthlyAvgSpending, c)} / mo`}
             hint={data.monthsOfSpendingData > 0
               ? `avg of ${data.monthsOfSpendingData} month${data.monthsOfSpendingData === 1 ? '' : 's'}`
               : 'no transactions yet'} />
        {data.monthlyLoanRepayment > 0 && (
          <Row label="…of which home loan" value={`${formatMoney(data.monthlyLoanRepayment, c)} / mo`} muted />
        )}
        <div className="border-t pt-3">
          <p className="text-sm text-muted-foreground">
            {positive ? 'You can safely spend about' : 'You are over by about'}
          </p>
          <p className={cn('text-2xl font-semibold tabular-nums',
            positive ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400')}
             data-testid="monthly-surplus">
            {formatMoney(Math.abs(data.monthlySurplus), c)} <span className="text-base font-normal text-muted-foreground">/ month</span>
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

/** The "can we afford a new loan?" what-if — runs live against the surplus. */
function Affordability({ data }: { data: CashFlow }) {
  const c = data.currency;
  const [amount, setAmount] = useState('500000');
  const [rate, setRate] = useState('6.25');
  const [term, setTerm] = useState('30');

  const repayment = monthlyRepayment(Number(amount) || 0, Number(rate) || 0, Number(term) || 0);
  const surplusAfter = data.monthlySurplus - repayment;
  const affordable = surplusAfter >= 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Can we afford it?</CardTitle>
        <CardDescription>Model a new loan — a property, a car — against what&apos;s left each month.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-3 gap-3">
          <Num id="amount" label="Loan amount" value={amount} onChange={setAmount} />
          <Num id="rate" label="Rate % p.a." value={rate} onChange={setRate} />
          <Num id="term" label="Term (yrs)" value={term} onChange={setTerm} />
        </div>

        <div className="rounded-lg border p-4">
          <p className="text-sm text-muted-foreground">
            {formatMoney(Number(amount) || 0, c)} at {rate || 0}% over {term || 0} years is about{' '}
            <span className="font-medium text-foreground" data-testid="new-repayment">
              {formatMoney(repayment, c)} / month
            </span>.
          </p>
          <p className={cn('mt-2 text-sm font-medium',
            affordable ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400')}
             data-testid="affordability-verdict">
            {affordable
              ? `Looks affordable — you'd have about ${formatMoney(surplusAfter, c)} / month left.`
              : `That would leave you about ${formatMoney(Math.abs(surplusAfter), c)} / month short.`}
          </p>
        </div>

        <p className="text-xs text-muted-foreground">
          Based on your household income and recent spending. Adjust those on the Income and Home loan screens.
        </p>
      </CardContent>
    </Card>
  );
}

function Row({ label, value, hint, muted }: { label: string; value: string; hint?: string; muted?: boolean }) {
  return (
    <div className="flex items-baseline justify-between text-sm">
      <span className={muted ? 'text-muted-foreground' : ''}>
        {label}{hint && <span className="ml-1 text-xs text-muted-foreground">({hint})</span>}
      </span>
      <span className={cn('tabular-nums', muted ? 'text-muted-foreground' : 'font-medium')}>{value}</span>
    </div>
  );
}

function Num({ id, label, value, onChange }: {
  id: string; label: string; value: string; onChange: (v: string) => void;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="text-xs">{label}</Label>
      <Input id={id} type="number" inputMode="decimal" step="any" value={value}
             onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}
