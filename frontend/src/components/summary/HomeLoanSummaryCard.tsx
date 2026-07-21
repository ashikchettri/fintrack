import { useQuery } from '@tanstack/react-query';
import { Landmark } from 'lucide-react';
import { api } from '../../api/client';
import { amortize, monthlyAmount } from '@/lib/finance';
import { formatMoney } from '@/lib/format';
import { SummaryCard, SummaryStat } from './SummaryCard';

/** "X yr Y mo" from a month count. */
function formatTerm(months: number): string {
  const years = Math.floor(months / 12);
  const mo = months % 12;
  if (years === 0) return `${mo} mo`;
  if (mo === 0) return `${years} yr`;
  return `${years} yr ${mo} mo`;
}

/** Dashboard summary of the home loan: balance left + a payoff estimate. */
export function HomeLoanSummaryCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['home-loan'],
    queryFn: api.getHomeLoan,
    retry: false,
  });

  if (isPending || isError || !data) return null;

  if (!data.hasHomeLoan || !data.loanAmount) {
    return (
      <SummaryCard
        icon={<Landmark className="size-4" aria-hidden="true" />}
        title="Home loan"
        to="/home-loan"
      accent="violet"
        empty={{ prompt: 'Track your mortgage to see how fast you can pay it off.', to: '/home-loan' }}
      />
    );
  }

  const monthlyPayment = monthlyAmount(data.repaymentAmount, data.repaymentFrequency);
  const plan = data.interestRate != null && monthlyPayment > 0
    ? amortize(data.loanAmount, data.interestRate, monthlyPayment)
    : null;

  return (
    <SummaryCard
      icon={<Landmark className="size-4" aria-hidden="true" />}
      title="Home loan"
      to="/home-loan"
      accent="violet"
      hero={{ label: 'Balance remaining', value: formatMoney(data.loanAmount, data.currency), tone: 'neutral' }}
    >
      <SummaryStat label="Interest rate" value={data.interestRate != null ? `${data.interestRate}% p.a.` : '—'} />
      <SummaryStat label="Paid off in" value={plan && plan.paidOff ? formatTerm(plan.months) : '—'} />
    </SummaryCard>
  );
}
