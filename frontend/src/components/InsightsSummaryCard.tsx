import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { api } from '../api/client';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatMoney, formatMonth } from '@/lib/format';

/**
 * The AI (or template) monthly spending summary (ADR 012) — a headline plus a
 * few insight bullets. Self-fetching; stays quiet on load/error.
 */
export function InsightsSummaryCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['insights-summary'],
    queryFn: () => api.getMonthlySummary(),
    retry: false,
  });

  if (isPending || isError || !data) return null;

  return (
    <Card className="border-primary/30 bg-primary/[0.03]">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <Sparkles className="size-4 text-primary" aria-hidden="true" />
          Spending summary
        </CardTitle>
        {data.month && (
          <CardDescription>{formatMonth(data.month)}</CardDescription>
        )}
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="font-medium">{data.headline}</p>
        {data.insights.length > 0 && (
          <ul className="space-y-1.5">
            {data.insights.map((insight, i) => (
              <li key={i} className="flex gap-2 text-sm text-muted-foreground">
                <span aria-hidden="true" className="text-primary">•</span>
                <span>{insight}</span>
              </li>
            ))}
          </ul>
        )}
        {data.totals.transactionCount > 0 && (
          <p className="border-t pt-2 text-xs text-muted-foreground">
            {data.totals.transactionCount} transactions ·{' '}
            {formatMoney(data.totals.expenses, data.currency)} spent ·{' '}
            {formatMoney(data.totals.income, data.currency)} in
          </p>
        )}
      </CardContent>
    </Card>
  );
}
