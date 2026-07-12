import { expect, test } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';

// True end-to-end: real auth-service + Postgres + Mailpit behind the Vite proxy.
// Start the stack first: docker compose up -d postgres mailpit && ./gradlew bootRun

const PASSWORD = 'correct horse battery staple';
const MAILPIT = 'http://localhost:8025';

/** Polls Mailpit's REST API for a numeric code emailed to `email` (ADR 004/005). */
async function fetchCodeFromMailpit(
  request: APIRequestContext,
  email: string,
  { subject, digits }: { subject: string; digits: number },
): Promise<string> {
  const query = `to:${email} subject:"${subject}"`;
  for (let attempt = 0; attempt < 20; attempt++) {
    const search = await request.get(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(query)}`,
    );
    const results = await search.json();
    if (results.messages?.length) {
      const message = await request.get(`${MAILPIT}/api/v1/message/${results.messages[0].ID}`);
      const body = await message.json();
      const match = (body.Text as string).match(new RegExp(`\\b(\\d{${digits}})\\b`));
      if (match) return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No "${subject}" email for ${email} arrived in Mailpit`);
}

async function signupAndVerify(page: Page, request: APIRequestContext, email: string) {
  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign up' }).click();

  await expect(page.getByText('Check your email')).toBeVisible();
  const code = await fetchCodeFromMailpit(request, email, { subject: 'verification', digits: 4 });
  await page.getByLabel('Verification code').fill(code);
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByText('Email verified — log in to continue.')).toBeVisible();
}

test.describe('full auth journey against the real API', () => {
  test('signup → email code → verify → login → session survives reload → logout', async ({ page, request }) => {
    const email = `ui.e2e.${Date.now()}@example.com`;

    await signupAndVerify(page, request, email);

    // login (email pre-filled from the verify step)
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Log in' }).click();

    await expect(page.getByTestId('profile-email')).toHaveText(email);
    await expect(page.getByTestId('profile-role')).toHaveText('OWNER');

    // the httpOnly refresh cookie restores the session across a reload (ADR 003)
    await page.reload();
    await expect(page.getByTestId('profile-email')).toHaveText(email);

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/profile');
    await expect(page).toHaveURL(/\/login/);
  });

  test('unverified login is routed to the verification screen', async ({ page }) => {
    const email = `ui.e2e.unverified.${Date.now()}@example.com`;

    await page.goto('/signup');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Check your email')).toBeVisible();

    // skip verification, try to log in
    await page.goto('/login');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Log in' }).click();

    // the real 403 problem routes back to verification
    await expect(page.getByText('Check your email')).toBeVisible();
  });

  test('wrong password gets the real 401 problem message', async ({ page, request }) => {
    const email = `ui.e2e.badpw.${Date.now()}@example.com`;
    await signupAndVerify(page, request, email);

    await page.getByLabel('Password').fill('definitely-not-the-password');
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('forgot password → emailed code → reset → login with the new password', async ({ page, request }) => {
    const email = `ui.e2e.reset.${Date.now()}@example.com`;
    const newPassword = 'completely different secret';
    await signupAndVerify(page, request, email);

    // request the reset from the login screen — wait out the route change
    // before filling (both pages have an "Email" field)
    await page.getByRole('link', { name: 'Forgot password?' }).click();
    await expect(page.getByText('Forgot your password?')).toBeVisible();
    await page.getByLabel('Email').fill(email);
    await page.getByRole('button', { name: 'Send reset code' }).click();
    await expect(page.getByText('Reset your password')).toBeVisible();

    // real 6-digit code from the real inbox (ADR 005)
    const code = await fetchCodeFromMailpit(request, email, { subject: 'password reset', digits: 6 });
    await page.getByLabel('Reset code').fill(code);
    await page.getByLabel('New password').fill(newPassword);
    await page.getByRole('button', { name: 'Reset password' }).click();

    await expect(page.getByText('Password reset — log in with your new password.')).toBeVisible();
    await page.getByLabel('Password').fill(newPassword);
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByTestId('profile-email')).toHaveText(email);
  });
});
