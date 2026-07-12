import { cva } from 'class-variance-authority';
import type { VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from '@/lib/utils';

const alertVariants = cva('w-full rounded-md border px-4 py-3 text-sm', {
  variants: {
    variant: {
      destructive: 'border-destructive/40 bg-destructive/10 text-destructive',
      success: 'border-[color:var(--success)]/40 bg-[color:var(--success)]/10 text-[color:var(--success)]',
    },
  },
  defaultVariants: {
    variant: 'destructive',
  },
});

type AlertProps = ComponentProps<'div'> & VariantProps<typeof alertVariants>;

export function Alert({ className, variant, ...props }: AlertProps) {
  return <div className={cn(alertVariants({ variant }), className)} {...props} />;
}
