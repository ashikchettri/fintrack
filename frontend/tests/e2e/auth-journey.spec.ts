import { expect, test } from '@playwright/test';

// True end-to-end: real auth-service + Postgres behind the Vite proxy.
// Start the backend first: docker compose up -d postgres && ./gradlew bootRun
// (or `npm run test:e2e` in CI, where the workflow boots the stack).

const PASSWORD = 'correct horse battery staple';

test.describe('full auth journey against the real API', () => {
  test('signup → login → profile → session survives reload → logout', async ({ page }) => {
    const email = `ui.e2e.${Date.now()}@example.com`;

    // signup
    await page.goto('/signup');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Account created — log in to continue.')).toBeVisible();

    // login (email pre-filled from signup)
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Log in' }).click();

    // profile rendered from the real /users/me
    await expect(page.getByTestId('profile-email')).toHaveText(email);
    await expect(page.getByTestId('profile-role')).toHaveText('OWNER');
    await expect(page.getByTestId('profile-household')).toContainText('household');

    // the httpOnly refresh cookie restores the session across a reload (ADR 003)
    await page.reload();
    await expect(page.getByTestId('profile-email')).toHaveText(email);

    // logout revokes the cookie; guarded route bounces back to login
    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/profile');
    await expect(page).toHaveURL(/\/login/);
  });

  test('wrong password gets the real 401 problem message', async ({ page }) => {
    const email = `ui.e2e.badpw.${Date.now()}@example.com`;

    await page.goto('/signup');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Account created — log in to continue.')).toBeVisible();

    await page.getByLabel('Password').fill('definitely-not-the-password');
    await page.getByRole('button', { name: 'Log in' }).click();
    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('minimal edge-case email passes the real 201 path', async ({ page }) => {
    // proves short/edge emails survive both client regex and server validation
    // (unique per run — a static email 409s on the second execution)
    const edgeEmail = `e${Date.now()}@b.co`;
    await page.goto('/signup');
    await page.getByLabel('Email').fill(edgeEmail);
    await page.getByLabel('Password').fill(PASSWORD);
    await page.getByRole('button', { name: 'Sign up' }).click();
    await expect(page.getByText('Account created — log in to continue.')).toBeVisible();
  });
});
