import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Scale } from 'lucide-react';
import { api } from '../../api/client';
import { Card, CardContent } from '@/components/ui/card';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * The household's tracked net position: offset savings against the home-loan
 * balance — the "where do we stand" number. Hidden until there's a home loan to
 * position against; grows into a fuller net worth as more accounts are tracked.
 */
export function NetPositionCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['home-loan'],
    queryFn: api.getHomeLoan,
    retry: false,
  });

  if (isPending || isError || !data) return null;
  if (!data.hasHomeLoan || !data.loanAmount) return null;

  const liabilities = data.loanAmount;
  const assets = data.offsetBalance ?? 0;
  const net = assets - liabilities;

  return (
    <Card className="border-primary/30 bg-primary/[0.03]">
      <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            <Scale className="size-4 text-primary" aria-hidden="true" />
            Net position
          </p>
          <p
            className={cn(
              'mt-1 text-3xl font-semibold tabular-nums',
              net >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-foreground',
            )}
            data-testid="net-position"
          >
            {formatMoney(net, data.currency)}
          </p>
          <p className="text-xs text-muted-foreground">Offset savings against your home loan</p>
        </div>

        <div className="flex gap-6">
          <Figure label="Offset savings" value={`+${formatMoney(assets, data.currency)}`} tone="good" />
          <Figure label="Home loan" value={`−${formatMoney(liabilities, data.currency)}`} tone="bad" />
          <Link to="/home-loan" className="self-center text-sm font-medium text-primary hover:underline">
            Details →
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

function Figure({ label, value, tone }: { label: string; value: string; tone: 'good' | 'bad' }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={cn(
        'text-sm font-medium tabular-nums',
        tone === 'good' ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400',
      )}>
        {value}
      </p>
    </div>
  );
}
