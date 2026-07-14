import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { toast } from 'sonner';
import SettingsPage from './SettingsPage';
import { ApiError, api } from '../api/client';
import { renderPageWithDestinations } from '../test/utils';

vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>();
  return {
    ...actual,
    api: {
      refresh: vi.fn().mockResolvedValue(false),
      me: vi.fn(),
      changePassword: vi.fn(),
      requestEmailChange: vi.fn(),
      confirmEmailChange: vi.fn(),
    },
  };
});

const mockedApi = vi.mocked(api);
const NEW_PASSWORD = 'a brand new secret here';

beforeEach(() => {
  vi.clearAllMocks();
  mockedApi.refresh.mockResolvedValue(false);
});

function renderSettings() {
  return renderPageWithDestinations(<SettingsPage />, '/settings', { '/profile': 'PROFILE_DEST' });
}

describe('SettingsPage — navigation', () => {
  it('has a back link to the profile', async () => {
    renderPageWithDestinations(<SettingsPage />, '/settings', { '/profile': 'PROFILE_DEST' });

    await userEvent.setup().click(screen.getByRole('link', { name: 'Back' }));

    await waitFor(() => expect(screen.getByText('PROFILE_DEST')).toBeInTheDocument());
  });
});

describe('SettingsPage — change password', () => {
  it('submits current + new password and toasts success', async () => {
    mockedApi.changePassword.mockResolvedValue(undefined);
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Current password', { selector: '#currentPassword' }), 'correct horse battery staple');
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    await waitFor(() =>
      expect(mockedApi.changePassword).toHaveBeenCalledWith(
        'correct horse battery staple', NEW_PASSWORD));
    expect(toast.success).toHaveBeenCalledWith(expect.stringMatching(/Password changed/));
  });

  it('shows the incorrect-current-password problem inline', async () => {
    mockedApi.changePassword.mockRejectedValue(
      new ApiError(400, { title: 'Incorrect current password', status: 400,
        detail: 'Current password is incorrect' }),
    );
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Current password', { selector: '#currentPassword' }), 'wrong');
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    await waitFor(() =>
      expect(screen.getByText('Current password is incorrect')).toBeInTheDocument());
  });

  it('blocks a short new password client-side', async () => {
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Current password', { selector: '#currentPassword' }), 'whatever-current');
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    expect(await screen.findByText(/between 12 and 128 characters/)).toBeInTheDocument();
    expect(mockedApi.changePassword).not.toHaveBeenCalled();
  });

  it('maps server field errors onto the inputs', async () => {
    mockedApi.changePassword.mockRejectedValue(
      new ApiError(400, {
        title: 'Validation error',
        status: 400,
        errors: { newPassword: 'newPassword must be between 12 and 128 characters' },
      }),
    );
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Current password', { selector: '#currentPassword' }), 'correct horse battery staple');
    await user.type(screen.getByLabelText('New password'), 'aaaaaaaaaaaa');
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    await waitFor(() =>
      expect(screen.getByText('newPassword must be between 12 and 128 characters')).toBeInTheDocument());
  });

  it('toasts on a network failure', async () => {
    mockedApi.changePassword.mockRejectedValue(new TypeError('fetch failed'));
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Current password', { selector: '#currentPassword' }), 'correct horse battery staple');
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD);
    await user.click(screen.getByRole('button', { name: 'Change password' }));

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(expect.stringMatching(/Network error/)));
  });
});

describe('SettingsPage — change email', () => {
  it('requests a code then confirms and navigates to profile', async () => {
    mockedApi.requestEmailChange.mockResolvedValue(undefined);
    mockedApi.confirmEmailChange.mockResolvedValue(undefined);
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('New email'), 'new@example.com');
    // the change-email card's own current-password field
    await user.type(screen.getByLabelText('Current password', { selector: '#emailCurrentPassword' }),
      'correct horse battery staple');
    await user.click(screen.getByRole('button', { name: 'Send code' }));

    await waitFor(() =>
      expect(mockedApi.requestEmailChange).toHaveBeenCalledWith(
        'new@example.com', 'correct horse battery staple'));

    // now the confirmation step is shown
    const codeInput = await screen.findByLabelText('Verification code');
    await user.type(codeInput, '123456');
    await user.click(screen.getByRole('button', { name: 'Confirm new email' }));

    await waitFor(() => expect(mockedApi.confirmEmailChange).toHaveBeenCalledWith('123456'));
    await waitFor(() => expect(screen.getByText('PROFILE_DEST')).toBeInTheDocument());
  });

  async function reachConfirmStep(user: ReturnType<typeof userEvent.setup>) {
    mockedApi.requestEmailChange.mockResolvedValue(undefined);
    await user.type(screen.getByLabelText('New email'), 'new@example.com');
    await user.type(screen.getByLabelText('Current password', { selector: '#emailCurrentPassword' }),
      'correct horse battery staple');
    await user.click(screen.getByRole('button', { name: 'Send code' }));
    return screen.findByLabelText('Verification code');
  }

  it('shows the wrong-code problem inline at the confirm step', async () => {
    mockedApi.confirmEmailChange.mockRejectedValue(
      new ApiError(400, { title: 'Invalid verification code', status: 400,
        detail: 'Invalid or expired verification code' }),
    );
    renderSettings();
    const user = userEvent.setup();

    const codeInput = await reachConfirmStep(user);
    await user.type(codeInput, '000000');
    await user.click(screen.getByRole('button', { name: 'Confirm new email' }));

    await waitFor(() =>
      expect(screen.getByText('Invalid or expired verification code')).toBeInTheDocument());
  });

  it('"use a different address" returns to the request step', async () => {
    renderSettings();
    const user = userEvent.setup();

    await reachConfirmStep(user);
    await user.click(screen.getByRole('button', { name: 'Use a different address' }));

    expect(await screen.findByLabelText('New email')).toBeInTheDocument();
    expect(screen.queryByLabelText('Verification code')).not.toBeInTheDocument();
  });

  it('surfaces a taken-email 409 inline', async () => {
    mockedApi.requestEmailChange.mockRejectedValue(
      new ApiError(409, { title: 'Email already in use', status: 409,
        detail: 'An account with this email already exists' }),
    );
    renderSettings();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('New email'), 'taken@example.com');
    await user.type(screen.getByLabelText('Current password', { selector: '#emailCurrentPassword' }),
      'correct horse battery staple');
    await user.click(screen.getByRole('button', { name: 'Send code' }));

    await waitFor(() =>
      expect(screen.getByText('An account with this email already exists')).toBeInTheDocument());
  });
});
