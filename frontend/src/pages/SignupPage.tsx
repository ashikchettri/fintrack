import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import { signupSchema } from '@/validators/auth';
import type { SignupValues } from '@/validators/auth';
import { AuthLayout } from '@/components/AuthLayout';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export default function SignupPage() {
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: '', password: '' },
  });

  async function onSubmit(values: SignupValues) {
    try {
      await api.signup(values.email, values.password);
      navigate('/verify-email', { state: { email: values.email } });
    } catch (error) {
      if (error instanceof ApiError) {
        // 400 carries our field→message extension; 409 = email taken
        if (error.problem.errors) {
          for (const [field, message] of Object.entries(error.problem.errors)) {
            setError(field as keyof SignupValues, { message });
          }
        } else {
          setError('root', { message: error.problem.detail ?? 'Signup failed' });
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
          <CardTitle>Create your account</CardTitle>
          <CardDescription>A household is created for you automatically.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                aria-invalid={errors.email ? true : undefined}
                placeholder="you@example.com"
                {...register('email')}
              />
              {errors.email && (
                <p className="text-sm text-destructive" role="alert">{errors.email.message}</p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                aria-invalid={errors.password ? true : undefined}
                placeholder="At least 12 characters"
                {...register('password')}
              />
              {errors.password && (
                <p className="text-sm text-destructive" role="alert">{errors.password.message}</p>
              )}
            </div>

            {errors.root && <Alert role="alert">{errors.root.message}</Alert>}

            <Button type="submit" disabled={isSubmitting} className="mt-1 w-full">
              {isSubmitting ? 'Creating…' : 'Sign up'}
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
