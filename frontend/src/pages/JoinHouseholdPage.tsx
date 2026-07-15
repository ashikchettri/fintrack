import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { acceptInviteSchema } from '@/validators/auth';
import type { AcceptInviteValues } from '@/validators/auth';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

/**
 * Accept a household invite: enter the emailed code + set up an account, then
 * land straight in the shared household (auto-login on success).
 */
export default function JoinHouseholdPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AcceptInviteValues>({
    resolver: zodResolver(acceptInviteSchema),
    defaultValues: { email: '', code: '', password: '', name: '' },
  });

  async function onSubmit(values: AcceptInviteValues) {
    try {
      await api.acceptInvite(values.email, values.code, values.password, values.name);
      await login(values.email, values.password); // straight into the household
      navigate('/dashboard');
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.problem.errors) {
          for (const [field, message] of Object.entries(error.problem.errors)) {
            setError(field as keyof AcceptInviteValues, { message });
          }
        } else {
          setError('root', { message: error.problem.detail ?? 'Could not accept the invite' });
        }
      } else {
        toast.error('Network error — is the API running?');
      }
    }
  }

  return (
    <AuthLayout>
      <Card>
        <CardHeader>
          <CardTitle>Join a household</CardTitle>
          <CardDescription>Enter the invite code from your email to set up your account.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="name">Your name</Label>
              <Input id="name" autoComplete="name" placeholder="Ashik"
                     aria-invalid={errors.name ? true : undefined} {...register('name')} />
              {errors.name && <p className="text-sm text-destructive" role="alert">{errors.name.message}</p>}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" autoComplete="email" placeholder="you@example.com"
                     aria-invalid={errors.email ? true : undefined} {...register('email')} />
              {errors.email && <p className="text-sm text-destructive" role="alert">{errors.email.message}</p>}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="code">Invite code</Label>
              <Input id="code" inputMode="numeric" autoComplete="one-time-code" placeholder="6-digit code"
                     aria-invalid={errors.code ? true : undefined} {...register('code')} />
              {errors.code && <p className="text-sm text-destructive" role="alert">{errors.code.message}</p>}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" autoComplete="new-password" placeholder="At least 12 characters"
                     aria-invalid={errors.password ? true : undefined} {...register('password')} />
              {errors.password && <p className="text-sm text-destructive" role="alert">{errors.password.message}</p>}
            </div>

            {errors.root && <Alert role="alert">{errors.root.message}</Alert>}

            <Button type="submit" disabled={isSubmitting} className="mt-1 w-full">
              {isSubmitting ? 'Joining…' : 'Join household'}
            </Button>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">Log in</Link>
          </p>
        </CardContent>
      </Card>
    </AuthLayout>
  );
}
