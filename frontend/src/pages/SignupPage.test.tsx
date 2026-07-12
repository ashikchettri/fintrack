import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SignupPage from './SignupPage';
import { ApiError, api } from '../api/client';
import { renderPageWithDestinations } from '../test/utils';

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
const VALID_PASSWORD = 'correct horse battery staple';

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

function renderSignup() {
  return renderPageWithDestinations(<SignupPage />, '/signup', { '/verify-email': 'VERIFY_DEST' });
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup();
  if (email) await user.type(screen.getByLabelText('Email'), email);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign up' }));
}

describe('SignupPage', () => {
  it('validates email format client-side without calling the API', async () => {
    renderSignup();
    await fillAndSubmit('not-an-email', VALID_PASSWORD);

    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(mockedApi.signup).not.toHaveBeenCalled();
  });

  it('enforces the 12-character password rule client-side', async () => {
    renderSignup();
    await fillAndSubmit('jane@example.com', 'short');

    expect(screen.getByText(/between 12 and 128 characters/)).toBeInTheDocument();
    expect(mockedApi.signup).not.toHaveBeenCalled();
  });

  it('navigates to the verification screen after successful signup', async () => {
    mockedApi.signup.mockResolvedValue({
      userId: 'u1', email: 'jane@example.com', householdId: 'h1',
      householdName: "jane's household", role: 'OWNER', createdAt: '2026-07-12T00:00:00Z',
    });
    renderSignup();
    await fillAndSubmit('jane@example.com', VALID_PASSWORD);

    await waitFor(() => expect(screen.getByText('VERIFY_DEST')).toBeInTheDocument());
    expect(mockedApi.signup).toHaveBeenCalledWith('jane@example.com', VALID_PASSWORD);
  });

  it('renders server-side field errors from the 400 problem body', async () => {
    mockedApi.signup.mockRejectedValue(
      new ApiError(400, {
        title: 'Validation error',
        status: 400,
        errors: { email: 'email must be a valid email address' },
      }),
    );
    renderSignup();
    // passes client validation, fails server-side
    await fillAndSubmit('jane@example.com', VALID_PASSWORD);

    await waitFor(() =>
      expect(screen.getByText('email must be a valid email address')).toBeInTheDocument(),
    );
  });

  it('renders the 409 duplicate-email problem detail', async () => {
    mockedApi.signup.mockRejectedValue(
      new ApiError(409, {
        title: 'Email already in use',
        status: 409,
        detail: 'An account with this email already exists',
      }),
    );
    renderSignup();
    await fillAndSubmit('jane@example.com', VALID_PASSWORD);

    await waitFor(() =>
      expect(screen.getByText('An account with this email already exists')).toBeInTheDocument(),
    );
  });
});
