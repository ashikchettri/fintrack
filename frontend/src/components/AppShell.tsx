import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { Logo } from '@/components/Logo';
import { ThemeToggle } from '@/components/ThemeToggle';
import { cn } from '@/lib/utils';

/** Authenticated chrome: top bar + centered content column. */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-border/60 bg-background/70 backdrop-blur-lg">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-4 px-4">
          {/* brand doubles as "home" — click to return to the dashboard */}
          <Link to="/dashboard" aria-label="Go to dashboard" className="rounded-md">
            <Logo />
          </Link>
          <nav className="flex flex-wrap items-center gap-1 text-sm">
            <ShellLink to="/dashboard">Dashboard</ShellLink>
            <ShellLink to="/cash-flow">Cash flow</ShellLink>
            <ShellLink to="/home-loan">Home loan</ShellLink>
            <ShellLink to="/budget">Income &amp; expenses</ShellLink>
            <ShellLink to="/profile">Profile</ShellLink>
          </nav>
          <ThemeToggle className="ml-auto" />
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-10">{children}</main>
    </div>
  );
}

function ShellLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cn(
          'rounded-full px-3 py-1.5 font-medium transition-colors',
          isActive
            ? 'bg-primary/10 text-primary'
            : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
        )
      }
    >
      {children}
    </NavLink>
  );
}
