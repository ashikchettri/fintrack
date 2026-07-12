import { Wallet } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * THE single place the brand mark lives. When the real logo is ready, replace
 * the <Wallet> icon (e.g. with an <img src="/logo.svg">) — every screen
 * updates. `withWordmark` toggles the "FinTrack" text next to the mark.
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
  const icon = size === 'lg' ? 'size-6' : 'size-4.5';
  const text = size === 'lg' ? 'text-2xl' : 'text-lg';

  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <span
        className={cn('inline-flex items-center justify-center bg-primary text-primary-foreground', box)}
      >
        <Wallet className={icon} aria-hidden="true" />
      </span>
      {withWordmark && (
        <span className={cn('font-bold tracking-tight', text)}>FinTrack</span>
      )}
    </span>
  );
}
