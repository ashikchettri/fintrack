import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { loginSchema } from '@/validators/auth';
import type { LoginValues } from '@/validators/auth';
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

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: routedState?.email ?? '', password: '' },
  });

  async function onSubmit(values: LoginValues) {
    try {
      await login(values.email, values.password);
      navigate('/dashboard');
    } catch (err) {
      if (err instanceof ApiError) {
        // distinct 403 problem type → straight to the verification screen
        if (err.problem.type?.endsWith('email-not-verified')) {
          navigate('/verify-email', { state: { email: values.email } });
          return;
        }
        // 401 → generic invalid credentials; 429 → throttle message.
        setError('root', { message: err.problem.detail ?? 'Login failed' });
      } else {
        toast.error('Network error — is the API running?');
      }
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
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                aria-invalid={errors.email ? true : undefined}
                {...register('email')}
              />
              {errors.email && (
                <p className="text-sm text-destructive" role="alert">{errors.email.message}</p>
              )}
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
                autoComplete="current-password"
                aria-invalid={errors.password ? true : undefined}
                {...register('password')}
              />
              {errors.password && (
                <p className="text-sm text-destructive" role="alert">{errors.password.message}</p>
              )}
            </div>

            {errors.root && <Alert role="alert">{errors.root.message}</Alert>}

            <Button type="submit" disabled={isSubmitting} className="mt-1 w-full">
              {isSubmitting ? 'Logging in…' : 'Log in'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            New here?{' '}
            <Link to="/signup" className="font-medium text-primary hover:underline">
              Create an account
            </Link>
          </p>
          <p className="mt-1 text-center text-sm text-muted-foreground">
            Have an invite?{' '}
            <Link to="/join" className="font-medium text-primary hover:underline">
              Join a household
            </Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
