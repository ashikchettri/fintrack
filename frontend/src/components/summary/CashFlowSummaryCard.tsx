import { useQuery } from '@tanstack/react-query';
import { Waves } from 'lucide-react';
import { api } from '../../api/client';
import { formatMoney } from '@/lib/format';
import { SummaryCard, SummaryStat } from './SummaryCard';

/** Dashboard summary of the household's monthly cash flow. Links to the detail. */
export function CashFlowSummaryCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['cash-flow'],
    queryFn: api.getCashFlow,
    retry: false,
  });

  if (isPending || isError || !data) return null;

  const hasIncome = data.monthlyIncome > 0;

  return (
    <SummaryCard
      icon={<Waves className="size-4" aria-hidden="true" />}
      title="Cash flow"
      to="/cash-flow"
      accent="cyan"
      hero={{
        label: 'Monthly surplus',
        value: formatMoney(data.monthlySurplus, data.currency),
        tone: data.monthlySurplus >= 0 ? 'good' : 'bad',
      }}
      empty={!hasIncome ? { prompt: 'Add your income to see your monthly surplus.', to: '/budget' } : undefined}
    >
      <SummaryStat label="Income / mo" value={formatMoney(data.monthlyIncome, data.currency)} />
      <SummaryStat label="Avg spending / mo" value={formatMoney(data.monthlyAvgSpending, data.currency)} />
    </SummaryCard>
  );
}
