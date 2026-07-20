import { useState } from 'react';
import { LineChart } from 'lucide-react';
import { PayoffChart } from '@/components/charts/PayoffChart';
import type { PayoffSeries } from '@/components/charts/PayoffChart';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { amortize, monthlyRepayment } from '@/lib/finance';
import { formatMoney } from '@/lib/format';

const MIN_COLOR = '#6366f1'; // indigo — the minimum-repayment baseline
const EXTRA_COLOR = '#10b981'; // emerald — paying extra (the "good" scenario)

interface LoanCalculatorProps {
  /** prefill from the saved loan; the user can then explore scenarios */
  loanAmount: number | null;
  interestRate: number | null;
  currency: string | null;
}

/** "27 yr 4 mo" from a month count. */
function formatTerm(months: number): string {
  const years = Math.floor(months / 12);
  const mo = months % 12;
  if (years === 0) return `${mo} mo`;
  if (mo === 0) return `${years} yr`;
  return `${years} yr ${mo} mo`;
}

/**
 * An interactive mortgage payoff calculator: minimum repayments vs paying a bit
 * extra each month. Shows total interest, payoff time, and what the extra saves,
 * with a balance-over-time chart. Inputs are local — nothing is saved.
 */
export function LoanCalculator({ loanAmount, interestRate, currency }: LoanCalculatorProps) {
  const [amount, setAmount] = useState(loanAmount != null ? String(loanAmount) : '');
  const [rate, setRate] = useState(interestRate != null ? String(interestRate) : '');
  const [term, setTerm] = useState('30');
  const [extra, setExtra] = useState('0');

  const principal = Number(amount) || 0;
  const ratePct = Number(rate) || 0;
  const years = Number(term) || 0;
  const extraMonthly = Math.max(0, Number(extra) || 0);

  const base = monthlyRepayment(principal, ratePct, years);
  const ready = principal > 0 && years > 0 && base > 0;

  const fmt = (v: number) => formatMoney(v, currency);
  const compact = (v: number) => (v >= 1000 ? `${fmt(v / 1000).replace(/\.\d+/, '')}k` : fmt(v));

  const minPlan = ready ? amortize(principal, ratePct, base) : null;
  const extraPlan = ready && extraMonthly > 0 ? amortize(principal, ratePct, base + extraMonthly) : null;

  const series: PayoffSeries[] = [];
  if (minPlan) {
    series.push({ points: minPlan.schedule, color: MIN_COLOR, label: 'Minimum repayments' });
  }
  if (extraPlan) {
    series.push({
      points: extraPlan.schedule,
      color: EXTRA_COLOR,
      label: `With ${fmt(extraMonthly)} extra / month`,
    });
  }

  const interestSaved = minPlan && extraPlan ? minPlan.totalInterest - extraPlan.totalInterest : 0;
  const monthsSaved = minPlan && extraPlan ? minPlan.months - extraPlan.months : 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <LineChart className="size-4" aria-hidden="true" />
          Payoff calculator
        </CardTitle>
        <CardDescription>
          See how long your loan takes to clear, the interest you&apos;ll pay, and how much a little extra each
          month saves. Explore freely — nothing here is saved.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <NumberField id="calc-amount" label="Loan amount" prefix="$" value={amount} onChange={setAmount} />
          <NumberField id="calc-rate" label="Interest rate (% p.a.)" value={rate} onChange={setRate} />
          <NumberField id="calc-term" label="Loan term (years)" value={term} onChange={setTerm} />
          <NumberField
            id="calc-extra"
            label="Extra repayment / month"
            prefix="$"
            value={extra}
            onChange={setExtra}
          />
        </div>

        {!ready ? (
          <p className="text-sm text-muted-foreground">
            Enter a loan amount, interest rate and term to see your payoff.
          </p>
        ) : (
          <>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              <Stat label="Minimum repayment" value={`${fmt(base)}/mo`} />
              <Stat label="Total interest" value={fmt(minPlan!.totalInterest)} />
              <Stat label="Paid off in" value={formatTerm(minPlan!.months)} />
            </div>

            {extraPlan && (
              <div
                className="grid grid-cols-2 gap-3 rounded-lg border border-success/30 bg-success/5 p-3"
                data-testid="extra-savings"
              >
                <Stat label="Interest saved" value={fmt(interestSaved)} accent />
                <Stat label="Paid off sooner" value={formatTerm(monthsSaved)} accent />
              </div>
            )}

            <PayoffChart
              series={series}
              xMaxMonths={minPlan!.months}
              yMax={principal}
              formatValue={compact}
            />
          </>
        )}
      </CardContent>
    </Card>
  );
}

function NumberField({
  id,
  label,
  value,
  onChange,
  prefix,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  prefix?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex items-center gap-2">
        {prefix && <span className="text-sm text-muted-foreground">{prefix}</span>}
        <Input
          id={id}
          type="number"
          inputMode="decimal"
          step="any"
          min="0"
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
      </div>
    </div>
  );
}

function Stat({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={`text-lg font-semibold ${accent ? 'text-success' : ''}`}>{value}</dd>
    </div>
  );
}
