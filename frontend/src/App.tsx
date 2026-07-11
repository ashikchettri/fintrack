import { Navigate, Route, Routes } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';
import SignupPage from './pages/SignupPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, initializing } = useAuth();
  // hold rendering until the session-restore attempt finishes, otherwise a
  // reload with a live cookie would flash the login page
  if (initializing) return <main className="card"><p>Loading…</p></main>;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <>
      <header className="topbar">
        <span className="brand">FinTrack</span>
      </header>
      <Routes>
        <Route path="/" element={<Navigate to="/profile" replace />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/profile"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
