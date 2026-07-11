import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const signedUpEmail = (location.state as { signedUpEmail?: string } | null)?.signedUpEmail;

  const [email, setEmail] = useState(signedUpEmail ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/profile');
    } catch (err) {
      if (err instanceof ApiError) {
        // 401 → generic invalid credentials; 429 → throttle message.
        // Both bodies come from the API's RFC 9457 problem details.
        setError(err.problem.detail ?? 'Login failed');
      } else {
        setError('Network error — is the API running?');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="card">
      <h1>Log in</h1>
      {signedUpEmail && (
        <p className="success" role="status">
          Account created — log in to continue.
        </p>
      )}
      <form onSubmit={onSubmit} noValidate>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
        />

        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        {error && <p className="form-error" role="alert">{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className="muted">
        New here? <Link to="/signup">Create an account</Link>
      </p>
    </main>
  );
}
