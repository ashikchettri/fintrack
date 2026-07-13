import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const PASSWORD_MIN = 12;
const PASSWORD_MAX = 128;

export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const stateEmail = (location.state as { email?: string } | null)?.email;

  const [email, setEmail] = useState(stateEmail ?? '');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const passwordValid = newPassword.length >= PASSWORD_MIN && newPassword.length <= PASSWORD_MAX;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!passwordValid) {
      setError(`Password must be between ${PASSWORD_MIN} and ${PASSWORD_MAX} characters`);
      return;
    }
    setSubmitting(true);
    try {
      await api.resetPassword(email, code, newPassword);
      navigate('/login', {
        state: { email, flash: 'Password reset — log in with your new password.' },
      });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.problem.detail ?? 'Reset failed');
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
          <CardTitle>Reset your password</CardTitle>
          <CardDescription>
            Enter the 6-digit code we emailed{stateEmail ? ` to ${stateEmail}` : ' you'} and choose a
            new password.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
            {!stateEmail && (
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
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="code">Reset code</Label>
              <Input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="000000"
                className="text-center text-2xl tracking-[0.4em] font-semibold"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="newPassword">New password</Label>
              <Input
                id="newPassword"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                placeholder="At least 12 characters"
              />
            </div>

            {error && <Alert role="alert">{error}</Alert>}

            <Button
              type="submit"
              disabled={submitting || code.length !== 6 || !email}
              className="mt-1 w-full"
            >
              {submitting ? 'Resetting…' : 'Reset password'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            No code?{' '}
            <Link to="/forgot-password" className="font-medium text-primary hover:underline">
              Request a new one
            </Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
