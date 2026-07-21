import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';

interface Hero {
  label: string;
  value: string;
  tone?: 'good' | 'bad' | 'neutral';
}

export type Accent = 'teal' | 'indigo' | 'violet' | 'amber' | 'emerald' | 'rose' | 'cyan';

// Full static class strings per accent (Tailwind can't see interpolated names):
// a faint colored wash + border for the card, and a colored icon bubble.
const ACCENT: Record<Accent, { card: string; bubble: string }> = {
  teal: { card: 'border-teal-500/20 bg-teal-500/[0.04]', bubble: 'bg-teal-500/15 text-teal-600 dark:text-teal-300' },
  indigo: { card: 'border-indigo-500/20 bg-indigo-500/[0.04]', bubble: 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-300' },
  violet: { card: 'border-violet-500/20 bg-violet-500/[0.04]', bubble: 'bg-violet-500/15 text-violet-600 dark:text-violet-300' },
  amber: { card: 'border-amber-500/25 bg-amber-500/[0.05]', bubble: 'bg-amber-500/15 text-amber-600 dark:text-amber-300' },
  emerald: { card: 'border-emerald-500/20 bg-emerald-500/[0.04]', bubble: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-300' },
  rose: { card: 'border-rose-500/20 bg-rose-500/[0.04]', bubble: 'bg-rose-500/15 text-rose-600 dark:text-rose-300' },
  cyan: { card: 'border-cyan-500/20 bg-cyan-500/[0.04]', bubble: 'bg-cyan-500/15 text-cyan-600 dark:text-cyan-300' },
};

/**
 * A consistent dashboard summary tile: a colored icon + title with a "details"
 * link, a hero metric, and a row of secondary stats — or a gentle prompt when
 * there's nothing to show yet. Each card carries its own {@link Accent} colour.
 */
export function SummaryCard({
  icon,
  title,
  to,
  accent = 'teal',
  hero,
  empty,
  children,
}: {
  icon: ReactNode;
  title: string;
  to: string;
  accent?: Accent;
  hero?: Hero;
  empty?: { prompt: string; to?: string };
  children?: ReactNode;
}) {
  const a = ACCENT[accent];
  return (
    <Card className={cn('transition-shadow hover:shadow-pop', a.card)}>
      <CardContent className="flex h-full flex-col gap-4 p-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className={cn('flex size-8 items-center justify-center rounded-full', a.bubble)}>
              {icon}
            </span>
            <h3 className="font-semibold tracking-tight">{title}</h3>
          </div>
          <Link
            to={to}
            aria-label={`Open ${title}`}
            className="flex items-center gap-1 text-xs font-medium text-muted-foreground transition-colors hover:text-primary"
          >
            View <ArrowRight className="size-3.5" aria-hidden="true" />
          </Link>
        </div>

        {empty ? (
          <div className="flex flex-1 flex-col justify-center py-2 text-sm text-muted-foreground">
            <p>{empty.prompt}</p>
            {empty.to && (
              <Link to={empty.to} className="mt-1 font-medium text-primary hover:underline">
                Set it up →
              </Link>
            )}
          </div>
        ) : (
          <>
            {hero && (
              <div>
                <p className="text-xs text-muted-foreground">{hero.label}</p>
                <p
                  className={cn(
                    'text-2xl font-semibold tabular-nums',
                    hero.tone === 'good' && 'text-emerald-600 dark:text-emerald-400',
                    hero.tone === 'bad' && 'text-red-600 dark:text-red-400',
                  )}
                >
                  {hero.value}
                </p>
              </div>
            )}
            {children && (
              <div className="mt-auto grid grid-cols-2 gap-3 border-t pt-3">{children}</div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

/** One secondary stat inside a {@link SummaryCard}. */
export function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm font-medium tabular-nums">{value}</p>
    </div>
  );
}
