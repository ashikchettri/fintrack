import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const RESEND_COOLDOWN_SECONDS = 60; // mirrors the backend cooldown (ADR 004)

export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const stateEmail = (location.state as { email?: string } | null)?.email;

  const [email, setEmail] = useState(stateEmail ?? '');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [resendNotice, setResendNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => setCooldown((s) => s - 1), 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.verifyEmail(email, code);
      navigate('/login', { state: { email, flash: 'Email verified — log in to continue.' } });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.problem.detail ?? 'Verification failed');
      } else {
        setError('Network error — is the API running?');
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function onResend() {
    setError(null);
    setResendNotice(null);
    try {
      await api.resendVerification(email);
      setResendNotice('If that address has an unverified account, a new code is on its way.');
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch {
      setError('Network error — is the API running?');
    }
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle>Check your email</CardTitle>
          <CardDescription>
            We sent a 4-digit code{stateEmail ? ` to ${stateEmail}` : ''}. It expires in 15 minutes.
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
              <Label htmlFor="code">Verification code</Label>
              <Input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={4}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="0000"
                className="text-center text-2xl tracking-[0.5em] font-semibold"
              />
            </div>

            {error && <Alert role="alert">{error}</Alert>}
            {resendNotice && <Alert variant="success" role="status">{resendNotice}</Alert>}

            <Button type="submit" disabled={submitting || code.length !== 4} className="mt-1 w-full">
              {submitting ? 'Verifying…' : 'Verify email'}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={onResend}
              disabled={cooldown > 0 || !email}
              className="w-full"
            >
              {cooldown > 0 ? `Resend code (${cooldown}s)` : 'Resend code'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already verified?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
