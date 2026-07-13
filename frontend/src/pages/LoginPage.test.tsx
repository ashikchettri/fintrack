import { beforeEach, describe, expect, it, vi } from 'vitest';
import { toast } from 'sonner';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage from './LoginPage';
import { ApiError, api } from '../api/client';
import { TEST_USER, renderPageWithDestinations } from '../test/utils';

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      signup: vi.fn(),
      login: vi.fn(),
      refresh: vi.fn().mockResolvedValue(false),
      logout: vi.fn(),
      me: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

function renderLogin() {
  return renderPageWithDestinations(<LoginPage />, '/login', {
    '/profile': 'PROFILE_DEST',
    '/verify-email': 'VERIFY_DEST',
  });
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

describe('LoginPage', () => {
  it('logs in and navigates to the profile', async () => {
    mockedApi.login.mockResolvedValue({ accessToken: 'jwt', tokenType: 'Bearer', expiresInSeconds: 900 });
    mockedApi.me.mockResolvedValue(TEST_USER);
    renderLogin();

    await fillAndSubmit('jane@example.com', 'correct horse battery staple');

    await waitFor(() => expect(screen.getByText('PROFILE_DEST')).toBeInTheDocument());
    expect(mockedApi.login).toHaveBeenCalledWith('jane@example.com', 'correct horse battery staple');
  });

  it('shows the generic message on 401 (wrong email or password)', async () => {
    mockedApi.login.mockRejectedValue(
      new ApiError(401, { title: 'Invalid credentials', status: 401, detail: 'Invalid email or password' }),
    );
    renderLogin();

    await fillAndSubmit('jane@example.com', 'wrong-password-here');

    await waitFor(() => expect(screen.getByText('Invalid email or password')).toBeInTheDocument());
  });

  it('routes unverified accounts to the verification screen on the 403 problem', async () => {
    mockedApi.login.mockRejectedValue(
      new ApiError(403, {
        type: 'https://fintrack.example/problems/email-not-verified',
        title: 'Email not verified',
        status: 403,
        detail: 'Email address has not been verified',
      }),
    );
    renderLogin();

    await fillAndSubmit('jane@example.com', 'correct horse battery staple');

    await waitFor(() => expect(screen.getByText('VERIFY_DEST')).toBeInTheDocument());
  });

  it('shows the throttle message on 429', async () => {
    mockedApi.login.mockRejectedValue(
      new ApiError(429, {
        title: 'Too many attempts',
        status: 429,
        detail: 'Too many failed login attempts — try again later',
      }),
    );
    renderLogin();

    await fillAndSubmit('jane@example.com', 'whatever-password');

    await waitFor(() =>
      expect(screen.getByText(/Too many failed login attempts/)).toBeInTheDocument(),
    );
  });

  it('toasts a network hint when the API is unreachable', async () => {
    mockedApi.login.mockRejectedValue(new TypeError('fetch failed'));
    renderLogin();

    await fillAndSubmit('jane@example.com', 'whatever-password');

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(expect.stringMatching(/Network error/)),
    );
  });
});
