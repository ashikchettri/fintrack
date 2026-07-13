import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ForgotPasswordPage from './ForgotPasswordPage';
import ResetPasswordPage from './ResetPasswordPage';
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
      forgotPassword: vi.fn(),
      resetPassword: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);
const NEW_PASSWORD = 'completely different secret';

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

describe('ForgotPasswordPage', () => {
  it('requests a code and moves to the reset step', async () => {
    mockedApi.forgotPassword.mockResolvedValue(undefined);
    renderPageWithDestinations(<ForgotPasswordPage />, '/forgot-password', {
      '/reset-password': 'RESET_DEST',
    });
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.click(screen.getByRole('button', { name: 'Send reset code' }));

    await waitFor(() => expect(screen.getByText('RESET_DEST')).toBeInTheDocument());
    expect(mockedApi.forgotPassword).toHaveBeenCalledWith('jane@example.com');
  });
});

describe('ResetPasswordPage', () => {
  function renderReset() {
    return renderPageWithDestinations(<ResetPasswordPage />, '/reset-password', {
      '/login': 'LOGIN_DEST',
    });
  }

  it('resets with a 6-digit code and lands on login', async () => {
    mockedApi.resetPassword.mockResolvedValue(undefined);
    renderReset();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.type(screen.getByLabelText('Reset code'), '123456');
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    await waitFor(() => expect(screen.getByText('LOGIN_DEST')).toBeInTheDocument());
    expect(mockedApi.resetPassword).toHaveBeenCalledWith('jane@example.com', '123456', NEW_PASSWORD);
  });

  it('blocks short passwords client-side', async () => {
    renderReset();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.type(screen.getByLabelText('Reset code'), '123456');
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    expect(screen.getByText(/between 12 and 128 characters/)).toBeInTheDocument();
    expect(mockedApi.resetPassword).not.toHaveBeenCalled();
  });

  it('shows the generic problem detail for a wrong code', async () => {
    mockedApi.resetPassword.mockRejectedValue(
      new ApiError(400, {
        title: 'Invalid reset code',
        status: 400,
        detail: 'Invalid or expired reset code',
      }),
    );
    renderReset();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.type(screen.getByLabelText('Reset code'), '000000');
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Reset password' }));

    await waitFor(() =>
      expect(screen.getByText('Invalid or expired reset code')).toBeInTheDocument(),
    );
  });

  it('keeps the button disabled until the code has 6 digits', async () => {
    renderReset();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Email'), 'jane@example.com');
    await user.type(screen.getByLabelText('Reset code'), '123');
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD);

    expect(screen.getByRole('button', { name: 'Reset password' })).toBeDisabled();
  });
});
