import { useQuery } from '@tanstack/react-query';
import { Users, Lock } from 'lucide-react';
import { api } from '../api/client';
import type { SharedHouseholdView } from '../api/types';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatMoney } from '@/lib/format';
import { colorAt } from '@/components/charts/palette';
import { cn } from '@/lib/utils';

/**
 * The differentiator a bank can't offer (ADR 006): a private household view of
 * shared commitments — who has covered what and a suggested settlement — while
 * every member's personal spending stays invisible. Self-fetching so the
 * mark-as-shared toggle can refresh it by invalidating ['household'].
 */
export function SharedCommitmentsCard({ month = null }: { month?: string | null }) {
  const { data, isPending, isError } = useQuery({
    queryKey: ['household', month],
    queryFn: () => api.householdShared(month ?? undefined),
    retry: false,
    placeholderData: (prev) => prev,
  });
  // the roster gives contributions a real name instead of "Housemate"
  const { data: members } = useQuery({
    queryKey: ['household-members'],
    queryFn: api.householdMembers,
    retry: false,
  });

  if (isPending || isError || !data) return null; // stay quiet on load/error; the dashboard still works

  const nameById = new Map((members ?? []).map((m) => [m.memberId, m.name]));

  return (
    <Card className="border-primary/30 bg-primary/[0.03]">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base">
          <Users className="size-4 text-primary" aria-hidden="true" />
          Shared with your household
        </CardTitle>
        <CardDescription className="flex items-center gap-1.5">
          <Lock className="size-3" aria-hidden="true" />
          Only shared items appear here — personal spending stays private.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {data.memberCount === 0 ? <Empty /> : <Populated data={data} nameById={nameById} />}
      </CardContent>
    </Card>
  );
}

function Empty() {
  return (
    <p className="text-sm text-muted-foreground">
      Mark shared costs — rent, groceries, bills — from your recent transactions below. FinTrack then
      shows who&apos;s covered what and a fair settlement, without exposing anyone&apos;s personal spending.
    </p>
  );
}

function Populated({ data, nameById }: { data: SharedHouseholdView; nameById: Map<string, string> }) {
  const currency = data.currency;
  const { status, amount } = data.settlement;
  const maxCovered = Math.max(1, ...data.contributions.map((c) => c.covered));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <p className="text-sm text-muted-foreground">
            {status === 'settled' ? "You're all settled up" : status === 'owed' ? "You're owed" : 'You owe'}
          </p>
          {status !== 'settled' && (
            <p className={cn(
              'text-2xl font-semibold tabular-nums',
              status === 'owed' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400',
            )} data-testid="settlement-amount">
              {formatMoney(amount, currency)}
            </p>
          )}
        </div>
        <p className="text-right text-xs text-muted-foreground">
          {formatMoney(data.totalShared, currency)} shared<br />
          your share {formatMoney(data.fairShare, currency)}
        </p>
      </div>

      <div className="space-y-2">
        {data.contributions.map((c, i) => {
          const label = c.isYou ? 'You' : nameById.get(c.memberId) ?? 'Housemate';
          return (
            <div key={c.memberId} className="flex items-center gap-2 text-sm">
              <span className="w-24 shrink-0 truncate">{label}</span>
              <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                <span
                  className="block h-full rounded-full"
                  style={{ width: `${(c.covered / maxCovered) * 100}%`, backgroundColor: colorAt(i) }}
                />
              </span>
              <span className="w-20 shrink-0 text-right tabular-nums font-medium">
                {formatMoney(c.covered, currency)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
