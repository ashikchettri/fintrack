import { useQuery } from '@tanstack/react-query';
import { Landmark, Wallet } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { AppShell } from '@/components/AppShell';
import { HouseholdSection } from '@/components/HouseholdSection';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default function ProfilePage() {
  const navigate = useNavigate();

  // Server state via TanStack Query: cached, refetched on focus, retried by
  // the client's silent-refresh path if the access token expired meanwhile.
  const { data: profile, isPending, isError } = useQuery({
    queryKey: ['me'],
    queryFn: api.me,
    retry: false,
  });

  if (isPending) {
    return (
      <AppShell>
        <p className="text-muted-foreground">Loading profile…</p>
      </AppShell>
    );
  }
  if (isError || !profile) {
    return (
      <AppShell>
        <Alert role="alert" className="max-w-md">Could not load your profile.</Alert>
      </AppShell>
    );
  }

  const initial = profile.email.charAt(0).toUpperCase();

  return (
    <AppShell>
      <div className="mx-auto max-w-lg space-y-6">
        <Card>
          <CardHeader className="items-center pb-4 text-center">
            <span className="mb-2 flex size-16 items-center justify-center rounded-full bg-primary text-2xl font-semibold text-primary-foreground">
              {initial}
            </span>
            <CardTitle>Your profile</CardTitle>
            <CardDescription data-testid="profile-email">{profile.email}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <dl className="divide-y">
              <div className="flex items-center justify-between py-3">
                <dt className="text-sm text-muted-foreground">Household</dt>
                <dd className="text-sm font-medium" data-testid="profile-household">
                  {profile.householdName}
                </dd>
              </div>
              <div className="flex items-center justify-between py-3">
                <dt className="text-sm text-muted-foreground">Role</dt>
                <dd data-testid="profile-role">
                  <span className="rounded-full bg-secondary px-2.5 py-0.5 text-xs font-semibold">
                    {profile.role}
                  </span>
                </dd>
              </div>
              <div className="flex items-center justify-between py-3">
                <dt className="text-sm text-muted-foreground">Member since</dt>
                <dd className="text-sm font-medium">
                  {new Date(profile.createdAt).toLocaleDateString()}
                </dd>
              </div>
            </dl>
            <Button variant="outline" onClick={() => navigate('/home-loan')} className="w-full">
              <Landmark className="size-4" aria-hidden="true" />
              Home loan
            </Button>
            <Button variant="outline" onClick={() => navigate('/income')} className="w-full">
              <Wallet className="size-4" aria-hidden="true" />
              Income
            </Button>
          </CardContent>
        </Card>

        <HouseholdSection />
      </div>
    </AppShell>
  );
}
