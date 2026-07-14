import { expect, test } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';

// Real end-to-end for the account-settings page against the live stack
// (Postgres + Mailpit + auth-service). Run `./dev.sh mailpit` first.

const PASSWORD = 'correct horse battery staple';
const MAILPIT = 'http://localhost:8025';

async function fetchCode(
  request: APIRequestContext,
  email: string,
  subject: string,
): Promise<string> {
  const query = `to:${email} subject:"${subject}"`;
  for (let attempt = 0; attempt < 20; attempt++) {
    const search = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(query)}`);
    const results = await search.json();
    if (results.messages?.length) {
      const message = await request.get(`${MAILPIT}/api/v1/message/${results.messages[0].ID}`);
      const body = await message.json();
      const match = (body.Text as string).match(/\b(\d{6})\b/);
      if (match) return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No "${subject}" email for ${email} in Mailpit`);
}

async function signupVerifyLogin(page: Page, request: APIRequestContext, email: string) {
  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign up' }).click();
  await expect(page.getByText('Check your email')).toBeVisible();
  const code = await fetchCode(request, email, 'verification');
  await page.getByLabel('Verification code').fill(code);
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByText('Email verified — log in to continue.')).toBeVisible();
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  // login lands on the dashboard; reach the profile (and its settings) via the nav
  await expect(page).toHaveURL(/\/dashboard/);
  await page.getByRole('link', { name: 'Profile' }).click();
  await expect(page.getByTestId('profile-email')).toHaveText(email);
}

test.describe('account settings against the real API', () => {
  test('change password: only the new password works afterward', async ({ page, request }) => {
    const email = `ui.settings.pw.${Date.now()}@example.com`;
    const newPassword = 'a totally different secret';
    await signupVerifyLogin(page, request, email);

    await page.getByRole('button', { name: 'Account settings' }).click();
    await page.locator('#currentPassword').fill(PASSWORD);
    await page.getByLabel('New password').fill(newPassword);
    await page.getByRole('button', { name: 'Change password' }).click();
    await expect(page.getByText(/Password changed/)).toBeVisible();

    // change-password revokes every session; a fresh load lands on login,
    // where only the NEW password works
    await page.goto('/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByText('Invalid email or password')).toBeVisible();
    await page.getByLabel('Password').fill(newPassword);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page).toHaveURL(/\/dashboard/);
    await page.getByRole('link', { name: 'Profile' }).click();
    await expect(page.getByTestId('profile-email')).toHaveText(email);
  });

  test('change email: code to the new address, then login moves', async ({ page, request }) => {
    const email = `ui.settings.email.${Date.now()}@example.com`;
    const newEmail = `ui.settings.newaddr.${Date.now()}@example.com`;
    await signupVerifyLogin(page, request, email);

    await page.getByRole('button', { name: 'Account settings' }).click();
    await page.getByLabel('New email').fill(newEmail);
    await page.locator('#emailCurrentPassword').fill(PASSWORD);
    await page.getByRole('button', { name: 'Send code' }).click();
    await expect(page.getByText(/Enter the code sent to/)).toBeVisible();

    const code = await fetchCode(request, newEmail, 'Confirm your new');
    await page.getByLabel('Verification code').fill(code);
    await page.getByRole('button', { name: 'Confirm new email' }).click();

    // profile now shows the new address
    await expect(page.getByTestId('profile-email')).toHaveText(newEmail);
  });
});
