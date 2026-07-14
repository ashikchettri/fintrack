import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from '@/components/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';

/** Authenticated chrome: top bar + centered content column. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-5xl items-center px-4">
          {/* brand doubles as "home" — click to return to the dashboard */}
          <Link to="/profile" aria-label="Go to dashboard" className="rounded-md">
            <Logo />
          </Link>
          <ThemeToggle className="ml-auto" />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-10">{children}</main>
    </div>
  );
}
