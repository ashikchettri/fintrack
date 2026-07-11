import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthProvider } from '../auth/AuthContext';
import type { UserResponse } from '../api/types';

export const TEST_USER: UserResponse = {
  userId: '4f2c8a10-0000-0000-0000-000000000001',
  email: 'jane@example.com',
  householdId: '4f2c8a10-0000-0000-0000-000000000002',
  householdName: "jane's household",
  role: 'OWNER',
  createdAt: '2026-07-12T00:00:00Z',
};

/** Full provider stack around arbitrary routes, mirroring main.tsx. */
export function renderWithProviders(ui: ReactNode, initialEntries: string[] = ['/']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

/** Renders a page at `path` with a marker destination to assert navigation. */
export function renderPageWithDestinations(
  page: ReactNode,
  path: string,
  destinations: Record<string, string>,
  initialEntries?: string[],
) {
  return renderWithProviders(
    <Routes>
      <Route path={path} element={page} />
      {Object.entries(destinations).map(([to, marker]) => (
        <Route key={to} path={to} element={<div>{marker}</div>} />
      ))}
    </Routes>,
    initialEntries ?? [path],
  );
}
