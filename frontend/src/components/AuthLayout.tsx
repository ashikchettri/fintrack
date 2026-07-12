import type { ReactNode } from 'react';
import { Logo } from '@/components/Logo';

/** Split-screen shell for signup/login: brand panel left, form right. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-primary p-10 text-primary-foreground lg:flex">
        <Logo size="lg" className="text-primary-foreground [&_span:first-child]:bg-primary-foreground [&_span:first-child]:text-primary" />
        <div className="space-y-4">
          <h2 className="text-3xl font-semibold leading-tight tracking-tight">
            Your household&apos;s money,
            <br />
            finally in one place.
          </h2>
          <p className="max-w-md text-sm/6 opacity-80">
            Track spending together while keeping personal transactions personal —
            sharing is always your choice.
          </p>
        </div>
        <p className="text-xs opacity-60">
          Private by default · Argon2id-secured · Open source
        </p>
        {/* soft decorative rings */}
        <div className="pointer-events-none absolute -right-24 -top-24 size-80 rounded-full border-[1.5rem] border-primary-foreground/10" />
        <div className="pointer-events-none absolute -bottom-32 -right-8 size-96 rounded-full border-[1.5rem] border-primary-foreground/5" />
      </aside>

      <div className="flex flex-col">
        <header className="flex items-center p-6 lg:hidden">
          <Logo />
        </header>
        <div className="flex flex-1 items-center justify-center px-4 pb-16">
          <div className="w-full max-w-sm">{children}</div>
        </div>
      </div>
    </div>
  );
}
