import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UserPlus, Users } from 'lucide-react';
import { toast } from 'sonner';
import { ApiError, api } from '../api/client';
import type { MemberResponse } from '../api/types';
import { inviteSchema } from '@/validators/auth';
import type { InviteValues } from '@/validators/auth';
import { AppShell } from '@/components/AppShell';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export default function HouseholdPage() {
  const { data: members, isPending, isError } = useQuery({
    queryKey: ['household-members'],
    queryFn: api.householdMembers,
    retry: false,
  });

  if (isPending) {
    return <AppShell><p className="text-muted-foreground">Loading your household…</p></AppShell>;
  }
  if (isError || !members) {
    return <AppShell><Alert role="alert" className="max-w-md">Could not load your household.</Alert></AppShell>;
  }

  const isOwner = members.some((m) => m.isYou && m.role === 'OWNER');

  return (
    <AppShell>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Household</h1>
        <p className="text-sm text-muted-foreground">
          Everyone here can coordinate shared costs — without exposing personal spending.
        </p>
      </div>

      <div className="mx-auto max-w-lg space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Users className="size-4" aria-hidden="true" />
              Members
            </CardTitle>
          </CardHeader>
          <CardContent className="divide-y">
            {members.map((m) => <MemberRow key={m.memberId} member={m} />)}
          </CardContent>
        </Card>

        {isOwner && <InviteCard />}
      </div>
    </AppShell>
  );
}

function MemberRow({ member }: { member: MemberResponse }) {
  return (
    <div className="flex items-center justify-between py-3">
      <div className="flex items-center gap-2">
        <span className="font-medium">{member.name}</span>
        {member.isYou && <span className="text-xs text-muted-foreground">(you)</span>}
      </div>
      <span className="rounded-full bg-secondary px-2.5 py-0.5 text-xs font-semibold">{member.role}</span>
    </div>
  );
}

/** OWNER-only: invite someone by email. */
function InviteCard() {
  const queryClient = useQueryClient();
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } =
    useForm<InviteValues>({ resolver: zodResolver(inviteSchema), defaultValues: { email: '' } });

  const mutation = useMutation({
    mutationFn: (email: string) => api.inviteMember(email),
    onSuccess: (_data, email) => {
      toast.success('Invitation sent', { description: `${email} will get a code to join.` });
      void queryClient.invalidateQueries({ queryKey: ['household-members'] });
      reset();
    },
  });

  async function onSubmit(values: InviteValues) {
    try {
      await mutation.mutateAsync(values.email);
    } catch (error) {
      if (error instanceof ApiError) {
        setError('email', { message: error.problem.detail ?? 'Could not send the invite' });
      } else {
        toast.error('Network error — is the API running?');
      }
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <UserPlus className="size-4" aria-hidden="true" />
          Invite a member
        </CardTitle>
        <CardDescription>They&apos;ll get a code by email to join this household.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-3 sm:flex-row sm:items-start">
          <div className="flex-1">
            <Label htmlFor="invite-email" className="sr-only">Email</Label>
            <Input id="invite-email" type="email" placeholder="partner@example.com"
                   aria-invalid={errors.email ? true : undefined} {...register('email')} />
            {errors.email && <p className="mt-1 text-sm text-destructive" role="alert">{errors.email.message}</p>}
          </div>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Sending…' : 'Send invite'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
