import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      // always 204 — the response reveals nothing about account existence
      await api.forgotPassword(email);
      navigate('/reset-password', { state: { email } });
    } catch {
      setError('Network error — is the API running?');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle>Forgot your password?</CardTitle>
          <CardDescription>
            Enter your email — if it has an account, we&apos;ll send a 6-digit reset code.
          </CardDescription>
        </CardHeader>
        <CardContent>
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

            {error && <Alert role="alert">{error}</Alert>}

            <Button type="submit" disabled={submitting || !email} className="mt-1 w-full">
              {submitting ? 'Sending…' : 'Send reset code'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Remembered it?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
