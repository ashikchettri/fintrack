import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LogOut, Settings, User, Users } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { cn } from '@/lib/utils';

/**
 * Top-left account menu: the user's avatar opens a dropdown with their identity
 * and the account actions (profile, household, settings, log out) — replacing
 * the old "Profile" nav link.
 */
export function ProfileMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // close on outside click or Escape
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const initial = (user?.email?.charAt(0) ?? '?').toUpperCase();

  async function onLogout() {
    setOpen(false);
    await logout();
    navigate('/login');
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-label="Account menu"
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="flex size-9 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground outline-none ring-offset-2 ring-offset-background transition-shadow hover:shadow-pop focus-visible:ring-2 focus-visible:ring-ring"
      >
        {initial}
      </button>

      {open && (
        <div className="absolute right-0 top-11 z-20 w-60 overflow-hidden rounded-xl border bg-card text-card-foreground shadow-pop">
          <div className="border-b px-3 py-3">
            <p className="truncate text-sm font-medium">{user?.email}</p>
            {user?.householdName && (
              <p className="truncate text-xs text-muted-foreground">
                {user.householdName}
                {user.role && <span className="ml-1">· {user.role}</span>}
              </p>
            )}
          </div>
          <div className="p-1">
            <MenuLink to="/profile" icon={<User className="size-4" />} onClick={() => setOpen(false)}>
              Profile
            </MenuLink>
            <MenuLink to="/profile" icon={<Users className="size-4" />} onClick={() => setOpen(false)}>
              Household
            </MenuLink>
            <MenuLink to="/settings" icon={<Settings className="size-4" />} onClick={() => setOpen(false)}>
              Account settings
            </MenuLink>
          </div>
          <div className="border-t p-1">
            <button
              type="button"
              onClick={onLogout}
              className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
            >
              <LogOut className="size-4" aria-hidden="true" />
              Log out
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function MenuLink({ to, icon, onClick, children }: {
  to: string; icon: ReactNode; onClick: () => void; children: ReactNode;
}) {
  return (
    <Link
      to={to}
      onClick={onClick}
      className={cn(
        'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground',
        'transition-colors hover:bg-secondary hover:text-foreground',
      )}
    >
      <span className="text-foreground/70" aria-hidden="true">{icon}</span>
      {children}
    </Link>
  );
}
