import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api/client';
import type { UserResponse } from '../api/types';

interface AuthContextValue {
  /** null = anonymous */
  user: UserResponse | null;
  /** true while the session-restore attempt on first load is in flight */
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [initializing, setInitializing] = useState(true);

  // Session bootstrap: the httpOnly cookie may still hold a live session from
  // a previous visit — one silent refresh restores it across page reloads.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (await api.refresh()) {
        try {
          const profile = await api.me();
          if (!cancelled) setUser(profile);
        } catch {
          // profile fetch failed — stay anonymous
        }
      }
      if (!cancelled) setInitializing(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    await api.login(email, password);
    setUser(await api.me());
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, initializing, login, logout }),
    [user, initializing, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}