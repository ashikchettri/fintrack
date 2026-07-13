import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api/client';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

// Client-side rules mirror the backend Bean Validation (SignupRequest):
// server remains the authority, this is just faster feedback.
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_MIN = 12;
const PASSWORD_MAX = 128;

export default function SignupPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!EMAIL_PATTERN.test(email)) errors.email = 'Enter a valid email address';
    if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
      errors.password = `Password must be between ${PASSWORD_MIN} and ${PASSWORD_MAX} characters`;
    }
    return errors;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);

    const clientErrors = validate();
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setSubmitting(true);
    try {
      await api.signup(email, password);
      navigate('/verify-email', { state: { email } });
    } catch (error) {
      if (error instanceof ApiError) {
        // 400 carries our field→message extension; 409 = email taken
        if (error.problem.errors) setFieldErrors(error.problem.errors);
        else setFormError(error.problem.detail ?? 'Signup failed');
      } else {
        setFormError('Network error — is the API running?');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle>Create your account</CardTitle>
          <CardDescription>A household is created for you automatically.</CardDescription>
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
                aria-invalid={fieldErrors.email ? true : undefined}
                placeholder="you@example.com"
              />
              {fieldErrors.email && (
                <p className="text-sm text-destructive" role="alert">{fieldErrors.email}</p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                aria-invalid={fieldErrors.password ? true : undefined}
                placeholder="At least 12 characters"
              />
              {fieldErrors.password && (
                <p className="text-sm text-destructive" role="alert">{fieldErrors.password}</p>
              )}
            </div>

            {formError && <Alert role="alert">{formError}</Alert>}

            <Button type="submit" disabled={submitting} className="mt-1 w-full">
              {submitting ? 'Creating…' : 'Sign up'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Log in
            </Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
