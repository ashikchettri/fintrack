import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

// UI behavior against a mocked API: every /api call is intercepted, so these
// verify rendering/flows, not the backend contract (that's the e2e project).

const PROFILE = {
  userId: '4f2c8a10-0000-0000-0000-000000000001',
  email: 'jane@example.com',
  householdId: '4f2c8a10-0000-0000-0000-000000000002',
  householdName: "jane's household",
  role: 'OWNER',
  createdAt: '2026-07-12T00:00:00Z',
};

function problem(status: number, body: Record<string, unknown>) {
  return {
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({ status, ...body }),
  };
}

async function mockNoSession(page: Page) {
  // app bootstrap always tries a silent refresh — default to "no session"
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill(problem(401, { title: 'Invalid credentials', detail: 'Invalid refresh token' })),
  );
}

test.describe('signup', () => {
  test.beforeEach(async ({ page }) => mockNoSession(page));

  test('successful signup lands on verification, code unlocks login', async ({ page }) => {
    await page.route('**/api/v1/auth/signup', (route) =>
      route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(PROFILE) }),
    );
    await page.route('**/api/v1/auth/verify-email', (route) => route.fulfill({ status: 204 }));

    await page.goto('/signup');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Sign up' }).click();

    // verification step (ADR 004)
    await expect(page.getByText('Check your email')).toBeVisible();
    await page.getByLabel('Verification code').fill('1234');
    await page.getByRole('button', { name: 'Verify email' }).click();

    await expect(page.getByText('Email verified — log in to continue.')).toBeVisible();
    await expect(page.getByLabel('Email')).toHaveValue('jane@example.com');
  });

  test('a wrong code shows the problem detail and resend starts a cooldown', async ({ page }) => {
    await page.route('**/api/v1/auth/verify-email', (route) =>
      route.fulfill(
        problem(400, {
          title: 'Invalid verification code',
          detail: 'Invalid or expired verification code',
        }),
      ),
    );
    await page.route('**/api/v1/auth/resend-verification', (route) =>
      route.fulfill({ status: 204 }),
    );

    await page.goto('/verify-email');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Verification code').fill('0000');
    await page.getByRole('button', { name: 'Verify email' }).click();
    await expect(page.getByText('Invalid or expired verification code')).toBeVisible();

    await page.getByRole('button', { name: 'Resend code' }).click();
    await expect(page.getByText(/a new code is on its way/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Resend code \(\d+s\)/ })).toBeDisabled();
  });

  test('server validation errors render under the fields', async ({ page }) => {
    await page.route('**/api/v1/auth/signup', (route) =>
      route.fulfill(
        problem(400, {
          title: 'Validation error',
          errors: { email: 'email must be a valid email address' },
        }),
      ),
    );
    await page.goto('/signup');
    await page.getByLabel('Email').fill('looks@valid.example');
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Sign up' }).click();

    await expect(page.getByText('email must be a valid email address')).toBeVisible();
  });

  test('duplicate email shows the 409 problem detail', async ({ page }) => {
    await page.route('**/api/v1/auth/signup', (route) =>
      route.fulfill(
        problem(409, {
          title: 'Email already in use',
          detail: 'An account with this email already exists',
        }),
      ),
    );
    await page.goto('/signup');
    await page.getByLabel('Email').fill('taken@example.com');
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Sign up' }).click();

    await expect(page.getByText('An account with this email already exists')).toBeVisible();
  });
});

test.describe('login', () => {
  test.beforeEach(async ({ page }) => mockNoSession(page));

  test('wrong credentials show the generic message', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill(problem(401, { title: 'Invalid credentials', detail: 'Invalid email or password' })),
    );
    await page.goto('/login');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill('wrong-password-here');
    await page.getByRole('button', { name: 'Log in' }).click();

    await expect(page.getByText('Invalid email or password')).toBeVisible();
  });

  test('unverified account is routed to the verification screen', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill(
        problem(403, {
          type: 'https://fintrack.example/problems/email-not-verified',
          title: 'Email not verified',
          detail: 'Email address has not been verified',
        }),
      ),
    );
    await page.goto('/login');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Log in' }).click();

    await expect(page.getByText('Check your email')).toBeVisible();
  });

  test('throttled login shows the 429 message', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill(
        problem(429, {
          title: 'Too many attempts',
          detail: 'Too many failed login attempts — try again later',
        }),
      ),
    );
    await page.goto('/login');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill('any-password-here');
    await page.getByRole('button', { name: 'Log in' }).click();

    await expect(page.getByText(/Too many failed login attempts/)).toBeVisible();
  });

  test('successful login shows the profile, logout returns to login', async ({ page }) => {
    await page.route('**/api/v1/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'jwt-mock', tokenType: 'Bearer', expiresInSeconds: 900 }),
      }),
    );
    await page.route('**/api/v1/users/me', (route) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(PROFILE) }),
    );
    await page.route('**/api/v1/auth/logout', (route) => route.fulfill({ status: 204 }));

    await page.goto('/login');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Password').fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Log in' }).click();

    await expect(page.getByTestId('profile-email')).toHaveText('jane@example.com');
    await expect(page.getByTestId('profile-role')).toHaveText('OWNER');

    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('password reset', () => {
  test.beforeEach(async ({ page }) => mockNoSession(page));

  test('forgot → code + new password → login with confirmation', async ({ page }) => {
    await page.route('**/api/v1/auth/forgot-password', (route) => route.fulfill({ status: 204 }));
    await page.route('**/api/v1/auth/reset-password', (route) => route.fulfill({ status: 204 }));

    await page.goto('/login');
    await page.getByRole('link', { name: 'Forgot password?' }).click();

    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByRole('button', { name: 'Send reset code' }).click();

    await expect(page.getByText('Reset your password')).toBeVisible();
    await page.getByLabel('Reset code').fill('123456');
    await page.getByLabel('New password').fill('completely different secret');
    await page.getByRole('button', { name: 'Reset password' }).click();

    await expect(page.getByText('Password reset — log in with your new password.')).toBeVisible();
    await expect(page.getByLabel('Email')).toHaveValue('jane@example.com');
  });

  test('wrong reset code shows the problem detail', async ({ page }) => {
    await page.route('**/api/v1/auth/reset-password', (route) =>
      route.fulfill(
        problem(400, { title: 'Invalid reset code', detail: 'Invalid or expired reset code' }),
      ),
    );
    await page.goto('/reset-password');
    await page.getByLabel('Email').fill('jane@example.com');
    await page.getByLabel('Reset code').fill('000000');
    await page.getByLabel('New password').fill('completely different secret');
    await page.getByRole('button', { name: 'Reset password' }).click();

    await expect(page.getByText('Invalid or expired reset code')).toBeVisible();
  });
});

test.describe('route guarding', () => {
  test('visiting /profile without a session redirects to /login', async ({ page }) => {
    await mockNoSession(page);
    await page.goto('/profile');
    await expect(page).toHaveURL(/\/login/);
  });
});
