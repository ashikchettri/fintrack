import { expect, test } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';

// True end-to-end: real auth-service + Postgres + Mailpit behind the Vite proxy.
// Start the stack first: docker compose up -d postgres mailpit && ./gradlew bootRun

const PASSWORD = 'correct horse battery staple';
const MAILPIT = 'http://localhost:8025';

/** Polls Mailpit's REST API for the 4-digit code sent to `email` (ADR 004). */
async function fetchCodeFromMailpit(request: APIRequestContext, email: string): Promise<string> {
  for (let attempt = 0; attempt < 20; attempt++) {
    const search = await request.get(
      `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
    );
    const results = await search.json();
    if (results.messages?.length) {
      const message = await request.get(`${MAILPIT}/api/v1/message/${results.messages[0].ID}`);
      const body = await message.json();
      const match = (body.Text as string).match(/\b(\d{4})\b/);
      if (match) return match[1];
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No verification email for ${email} arrived in Mailpit`);
}

async function signupAndVerify(page: Page, request: APIRequestContext, email: string) {
  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign up' }).click();

  await expect(page.getByText('Check your email')).toBeVisible();
  const code = await fetchCodeFromMailpit(request, email);
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
});
