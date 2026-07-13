import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import VerifyEmailPage from './VerifyEmailPage';
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
      verifyEmail: vi.fn(),
      resendVerification: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

function renderVerify() {
  return renderPageWithDestinations(<VerifyEmailPage />, '/verify-email', {
    '/login': 'LOGIN_DEST',
  });
}

describe('VerifyEmailPage', () => {
  it('verifies the code and navigates to login', async () => {
    mockedApi.verifyEmail.mockResolvedValue(undefined);
    renderVerify();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.type(screen.getByLabelText('Verification code'), '123456');
    await user.click(screen.getByRole('button', { name: 'Verify email' }));

    await waitFor(() => expect(screen.getByText('LOGIN_DEST')).toBeInTheDocument());
    expect(mockedApi.verifyEmail).toHaveBeenCalledWith('jane@example.com', '123456');
  });

  it('keeps the button disabled until 6 digits are entered and strips non-digits', async () => {
    renderVerify();
    const user = userEvent.setup();

    const codeInput = screen.getByLabelText('Verification code');
    await user.type(codeInput, '12ab');
    expect(codeInput).toHaveValue('12');
    expect(screen.getByRole('button', { name: 'Verify email' })).toBeDisabled();

    await user.type(codeInput, '3456');
    expect(screen.getByRole('button', { name: 'Verify email' })).toBeEnabled();
  });

  it('shows the generic problem detail for a wrong code', async () => {
    mockedApi.verifyEmail.mockRejectedValue(
      new ApiError(400, {
        title: 'Invalid verification code',
        status: 400,
        detail: 'Invalid or expired verification code',
      }),
    );
    renderVerify();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.type(screen.getByLabelText('Verification code'), '000000');
    await user.click(screen.getByRole('button', { name: 'Verify email' }));

    await waitFor(() =>
      expect(screen.getByText('Invalid or expired verification code')).toBeInTheDocument(),
    );
  });

  it('resend triggers the API and starts the cooldown', async () => {
    mockedApi.resendVerification.mockResolvedValue(undefined);
    renderVerify();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.click(screen.getByRole('button', { name: 'Resend code' }));

    await waitFor(() =>
      expect(screen.getByText(/a new code is on its way/)).toBeInTheDocument(),
    );
    expect(mockedApi.resendVerification).toHaveBeenCalledWith('jane@example.com');
    // cooldown: button disabled and counting down
    expect(screen.getByRole('button', { name: /Resend code \(\d+s\)/ })).toBeDisabled();
  });
});
