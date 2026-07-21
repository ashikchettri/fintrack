import { useQuery } from '@tanstack/react-query';
import { Scale } from 'lucide-react';
import { api } from '../../api/client';
import { formatMoney } from '@/lib/format';
import { SummaryCard, SummaryStat } from './SummaryCard';

/** Dashboard summary of the household budget (income & expenses plan). */
export function BudgetSummaryCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['overview'],
    queryFn: api.getOverview,
    retry: false,
  });

  if (isPending || isError || !data) return null;

  if (!data.hasBudget) {
    return (
      <SummaryCard
        icon={<Scale className="size-4" aria-hidden="true" />}
        title="Income & expenses"
        to="/budget"
        empty={{ prompt: 'Set a household budget to plan income against spending.', to: '/budget' }}
      />
    );
  }

  const { income, expenses, leftover } = data.planned;

  return (
    <SummaryCard
      icon={<Scale className="size-4" aria-hidden="true" />}
      title="Income & expenses"
      to="/budget"
      hero={{
        label: 'Planned left over / mo',
        value: formatMoney(leftover, data.currency),
        tone: leftover >= 0 ? 'good' : 'bad',
      }}
    >
      <SummaryStat label="Planned income" value={formatMoney(income, data.currency)} />
      <SummaryStat label="Planned expenses" value={formatMoney(expenses, data.currency)} />
    </SummaryCard>
  );
}
