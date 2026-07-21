import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Landmark } from 'lucide-react';
import { api } from '../../api/client';
import { Card, CardContent } from '@/components/ui/card';
import { formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * The household's net worth (ADR 014) as the headline "where do we stand" number
 * — total assets minus total liabilities, folding in the home loan. Hidden until
 * there's something on the balance sheet; the /net-worth page fills it in.
 */
export function NetWorthCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['net-worth'],
    queryFn: api.getNetWorth,
    retry: false,
  });

  if (isPending || isError || !data) return null;
  if (data.assets.length === 0 && data.liabilities.length === 0) return null;

  return (
    <Card className="border-primary/30 hero-gradient">
      <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            <Landmark className="size-4 text-primary" aria-hidden="true" />
            Net worth
          </p>
          <p
            className={cn(
              'mt-1 text-3xl font-semibold tabular-nums',
              data.netWorth >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-foreground',
            )}
            data-testid="net-worth"
          >
            {formatMoney(data.netWorth, data.currency)}
          </p>
          <p className="text-xs text-muted-foreground">Everything you own minus what you owe</p>
        </div>

        <div className="flex gap-6">
          <Figure label="Assets" value={`+${formatMoney(data.totalAssets, data.currency)}`} tone="good" />
          <Figure label="Liabilities" value={`−${formatMoney(data.totalLiabilities, data.currency)}`} tone="bad" />
          <Link to="/net-worth" className="self-center text-sm font-medium text-primary hover:underline">
            Manage →
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
