import { cn } from '@/lib/utils';

/**
 * THE single place the brand mark lives. The mark is /logo.png (leaf +
 * growth-arrow tile, navy background) — also used as the favicon.
 * `withWordmark` toggles the "FinTrack" text next to the mark.
 */
export function Logo({
  className,
  withWordmark = true,
  size = 'md',
}: {
  className?: string;
  withWordmark?: boolean;
  size?: 'md' | 'lg';
}) {
  const box = size === 'lg' ? 'size-11 rounded-xl' : 'size-8 rounded-lg';
  const text = size === 'lg' ? 'text-2xl' : 'text-lg';

  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <img
        src="/logo.png"
        alt=""
        aria-hidden="true"
        className={cn('shrink-0 object-cover shadow-sm', box)}
      />
      {withWordmark && (
        <span className={cn('text-gradient font-bold tracking-tight', text)}>FinTrack</span>
      )}
    </span>
  );
}
