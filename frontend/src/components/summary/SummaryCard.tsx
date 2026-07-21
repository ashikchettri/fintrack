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

/**
 * A consistent dashboard summary tile: an icon + title with a "details" link, a
 * hero metric, and a row of secondary stats — or a gentle prompt when there's
 * nothing to show yet.
 */
export function SummaryCard({
  icon,
  title,
  to,
  hero,
  empty,
  children,
}: {
  icon: ReactNode;
  title: string;
  to: string;
  hero?: Hero;
  empty?: { prompt: string; to?: string };
  children?: ReactNode;
}) {
  return (
    <Card className="transition-shadow hover:shadow-pop">
      <CardContent className="flex h-full flex-col gap-4 p-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-primary">
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
