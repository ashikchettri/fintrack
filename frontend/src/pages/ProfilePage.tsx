import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';

export default function ProfilePage() {
  const navigate = useNavigate();
  const { logout } = useAuth();

  // Server state via TanStack Query: cached, refetched on focus, retried by
  // the client's silent-refresh path if the access token expired meanwhile.
  const { data: profile, isPending, isError } = useQuery({
    queryKey: ['me'],
    queryFn: api.me,
    retry: false,
  });

  async function onLogout() {
    await logout();
    navigate('/login');
  }

  if (isPending) return <main className="card"><p>Loading profile…</p></main>;
  if (isError || !profile) {
    return (
      <main className="card">
        <p className="form-error" role="alert">Could not load your profile.</p>
      </main>
    );
  }

  return (
    <main className="card">
      <h1>Your profile</h1>
      <dl className="profile">
        <dt>Email</dt>
        <dd data-testid="profile-email">{profile.email}</dd>
        <dt>Household</dt>
        <dd data-testid="profile-household">{profile.householdName}</dd>
        <dt>Role</dt>
        <dd data-testid="profile-role">{profile.role}</dd>
        <dt>Member since</dt>
        <dd>{new Date(profile.createdAt).toLocaleDateString()}</dd>
      </dl>
      <button type="button" onClick={onLogout}>Log out</button>
    </main>
  );
}
