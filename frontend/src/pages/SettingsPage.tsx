import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import {
  changeEmailSchema,
  changePasswordSchema,
  confirmEmailSchema,
} from '@/validators/settings';
import type {
  ChangeEmailValues,
  ChangePasswordValues,
  ConfirmEmailValues,
} from '@/validators/settings';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

/** Maps an API failure to inline form errors + a toast fallback. */
function useApiFormError<T extends Record<string, unknown>>(
  setError: (field: keyof T | 'root', e: { message: string }) => void,
) {
  return (error: unknown, fallback: string) => {
    if (error instanceof ApiError) {
      if (error.problem.errors) {
        for (const [field, message] of Object.entries(error.problem.errors)) {
          setError(field as keyof T, { message });
        }
      } else {
        setError('root', { message: error.problem.detail ?? fallback });
      }
    } else {
      toast.error('Network error — is the API running?');
    }
  };
}

function ChangePasswordCard() {
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema) });
  const handleApiError = useApiFormError<ChangePasswordValues>(setError);

  async function onSubmit(values: ChangePasswordValues) {
    try {
      await api.changePassword(values.currentPassword, values.newPassword);
      reset();
      toast.success('Password changed. Other sessions were signed out.');
    } catch (error) {
      handleApiError(error, 'Could not change password');
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Change password</CardTitle>
        <CardDescription>Signs out your other devices.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="currentPassword">Current password</Label>
            <Input id="currentPassword" type="password" autoComplete="current-password"
              aria-invalid={errors.currentPassword ? true : undefined} {...register('currentPassword')} />
            {errors.currentPassword && (
              <p className="text-sm text-destructive" role="alert">{errors.currentPassword.message}</p>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="newPassword">New password</Label>
            <Input id="newPassword" type="password" autoComplete="new-password"
              placeholder="At least 12 characters"
              aria-invalid={errors.newPassword ? true : undefined} {...register('newPassword')} />
            {errors.newPassword && (
              <p className="text-sm text-destructive" role="alert">{errors.newPassword.message}</p>
            )}
          </div>
          {errors.root && <Alert role="alert">{errors.root.message}</Alert>}
          <Button type="submit" disabled={isSubmitting} className="w-full">
            {isSubmitting ? 'Saving…' : 'Change password'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function ChangeEmailCard() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);

  const requestForm = useForm<ChangeEmailValues>({ resolver: zodResolver(changeEmailSchema) });
  const confirmForm = useForm<ConfirmEmailValues>({ resolver: zodResolver(confirmEmailSchema) });
  const handleRequestError = useApiFormError<ChangeEmailValues>(requestForm.setError);
  const handleConfirmError = useApiFormError<ConfirmEmailValues>(confirmForm.setError);

  async function onRequest(values: ChangeEmailValues) {
    try {
      await api.requestEmailChange(values.newEmail, values.currentPassword);
      setPendingEmail(values.newEmail);
      toast.success(`We sent a code to ${values.newEmail}.`);
    } catch (error) {
      handleRequestError(error, 'Could not start the email change');
    }
  }

  async function onConfirm(values: ConfirmEmailValues) {
    try {
      await api.confirmEmailChange(values.code);
      setPendingEmail(null);
      confirmForm.reset();
      requestForm.reset();
      // profile query holds the old email — mark it stale (refetches on /profile)
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      toast.success('Email updated.');
      navigate('/profile');
    } catch (error) {
      handleConfirmError(error, 'Could not confirm the new email');
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Change email</CardTitle>
        <CardDescription>
          {pendingEmail
            ? `Enter the code sent to ${pendingEmail}.`
            : 'We email a code to the new address before switching.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {!pendingEmail ? (
          <form key="request-email" onSubmit={requestForm.handleSubmit(onRequest)} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="newEmail">New email</Label>
              <Input id="newEmail" type="email" autoComplete="email" placeholder="you@example.com"
                aria-invalid={requestForm.formState.errors.newEmail ? true : undefined}
                {...requestForm.register('newEmail')} />
              {requestForm.formState.errors.newEmail && (
                <p className="text-sm text-destructive" role="alert">
                  {requestForm.formState.errors.newEmail.message}
                </p>
              )}
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="emailCurrentPassword">Current password</Label>
              <Input id="emailCurrentPassword" type="password" autoComplete="current-password"
                aria-invalid={requestForm.formState.errors.currentPassword ? true : undefined}
                {...requestForm.register('currentPassword')} />
              {requestForm.formState.errors.currentPassword && (
                <p className="text-sm text-destructive" role="alert">
                  {requestForm.formState.errors.currentPassword.message}
                </p>
              )}
            </div>
            {requestForm.formState.errors.root && (
              <Alert role="alert">{requestForm.formState.errors.root.message}</Alert>
            )}
            <Button type="submit" disabled={requestForm.formState.isSubmitting} className="w-full">
              {requestForm.formState.isSubmitting ? 'Sending…' : 'Send code'}
            </Button>
          </form>
        ) : (
          <form key="confirm-email" onSubmit={confirmForm.handleSubmit(onConfirm)} noValidate className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="emailCode">Verification code</Label>
              <Input id="emailCode" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
                placeholder="000000"
                className="text-center text-2xl tracking-[0.4em] font-semibold"
                aria-invalid={confirmForm.formState.errors.code ? true : undefined}
                {...confirmForm.register('code')} />
              {confirmForm.formState.errors.code && (
                <p className="text-sm text-destructive" role="alert">
                  {confirmForm.formState.errors.code.message}
                </p>
              )}
            </div>
            {confirmForm.formState.errors.root && (
              <Alert role="alert">{confirmForm.formState.errors.root.message}</Alert>
            )}
            <Button type="submit" disabled={confirmForm.formState.isSubmitting} className="w-full">
              {confirmForm.formState.isSubmitting ? 'Confirming…' : 'Confirm new email'}
            </Button>
            <Button type="button" variant="ghost" onClick={() => setPendingEmail(null)} className="w-full">
              Use a different address
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

export default function SettingsPage() {
  return (
    <AppShell>
      <div className="mx-auto flex max-w-md flex-col gap-6">
        <h1 className="text-xl font-semibold tracking-tight">Account settings</h1>
        <ChangePasswordCard />
        <ChangeEmailCard />
      </div>
    </AppShell>
  );
}
