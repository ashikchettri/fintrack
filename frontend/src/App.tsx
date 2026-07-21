import { Navigate, Route, Routes } from 'react-router-dom';
import { Suspense, lazy } from 'react';
import type { ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';

// Route-level code splitting: each page is its own chunk, fetched on first
// navigation, so the initial load no longer ships every page (charts, forms,
// the full authenticated app) up front.
const BankStatementPage = lazy(() => import('./pages/BankStatementPage'));
const BudgetPage = lazy(() => import('./pages/BudgetPage'));
const CashFlowPage = lazy(() => import('./pages/CashFlowPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'));
const HomeLoanPage = lazy(() => import('./pages/HomeLoanPage'));
const IncomePage = lazy(() => import('./pages/IncomePage'));
const InsightsPage = lazy(() => import('./pages/InsightsPage'));
const JoinHouseholdPage = lazy(() => import('./pages/JoinHouseholdPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const SignupPage = lazy(() => import('./pages/SignupPage'));
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage'));

/** Centered loading state while a route's chunk (or the session) resolves. */
function PageFallback() {
  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-muted-foreground">Loading…</p>
    </main>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth();
  // hold rendering until the session-restore attempt finishes, otherwise a
  // reload with a live cookie would flash the login page
  if (initializing) {
    return <PageFallback />;
  }
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/join" element={<JoinHouseholdPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <DashboardPage />
            </RequireAuth>
          }
        />
        <Route
          path="/bank"
          element={
            <RequireAuth>
              <BankStatementPage />
            </RequireAuth>
          }
        />
        {/* household moved into the profile page */}
        <Route path="/household" element={<Navigate to="/profile" replace />} />
        <Route
          path="/home-loan"
          element={
            <RequireAuth>
              <HomeLoanPage />
            </RequireAuth>
          }
        />
        <Route
          path="/income"
          element={
            <RequireAuth>
              <IncomePage />
            </RequireAuth>
          }
        />
        <Route
          path="/cash-flow"
          element={
            <RequireAuth>
              <CashFlowPage />
            </RequireAuth>
          }
        />
        <Route
          path="/insights"
          element={
            <RequireAuth>
              <InsightsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/budget"
          element={
            <RequireAuth>
              <BudgetPage />
            </RequireAuth>
          }
        />
        <Route
          path="/profile"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        <Route
          path="/settings"
          element={
            <RequireAuth>
              <SettingsPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
