import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Logo } from '@/components/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';
import { cn } from '@/lib/utils';

/** Authenticated chrome: top bar + centered content column. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-6 px-4">
          {/* brand doubles as "home" — click to return to the dashboard */}
          <Link to="/dashboard" aria-label="Go to dashboard" className="rounded-md">
            <Logo />
          </Link>
          <nav className="flex items-center gap-1 text-sm">
            <ShellLink to="/dashboard">Dashboard</ShellLink>
            <ShellLink to="/profile">Profile</ShellLink>
          </nav>
          <ThemeToggle className="ml-auto" />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-10">{children}</main>
    </div>
  );
}

function ShellLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          'rounded-md px-3 py-1.5 transition-colors hover:text-foreground',
          isActive ? 'font-medium text-foreground' : 'text-muted-foreground',
        )
      }
    >
      {children}
    </NavLink>
  );
}
