import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const routedState = location.state as { email?: string; flash?: string } | null;

  const [email, setEmail] = useState(routedState?.email ?? '');
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
        // distinct 403 problem type → straight to the verification screen
        if (err.problem.type?.endsWith('email-not-verified')) {
          navigate('/verify-email', { state: { email } });
          return;
        }
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
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle>Log in</CardTitle>
          <CardDescription>Welcome back — your household is waiting.</CardDescription>
        </CardHeader>
        <CardContent>
          {routedState?.flash && (
            <Alert variant="success" role="status" className="mb-4">
              {routedState.flash}
            </Alert>
          )}
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                placeholder="you@example.com"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <Label htmlFor="password">Password</Label>
                <Link
                  to="/forgot-password"
                  className="text-xs font-medium text-primary hover:underline"
                >
                  Forgot password?
                </Link>
              </div>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>

            {error && <Alert role="alert">{error}</Alert>}

            <Button type="submit" disabled={submitting} className="mt-1 w-full">
              {submitting ? 'Logging in…' : 'Log in'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            New here?{' '}
            <Link to="/signup" className="font-medium text-primary hover:underline">
              Create an account
            </Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
