import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

// Dashboard UI against a mocked API: verifies the "upload → dashboard" flow and
// rendering, not the backend contract (that's the e2e project).

const PROFILE = {
  userId: '4f2c8a10-0000-0000-0000-000000000001',
  email: 'jane@example.com',
  householdId: '4f2c8a10-0000-0000-0000-000000000002',
  householdName: "jane's household",
  role: 'OWNER',
  createdAt: '2026-07-12T00:00:00Z',
};

const POPULATED = {
  currency: 'AUD',
  month: null,
  availableMonths: ['2026-07', '2026-06'],
  totals: { income: 3040, expenses: 217.7, net: 2822.3, transactionCount: 6 },
  byCategory: [
    { category: 'Transportation', spent: 132.5, share: 0.6086 },
    { category: 'Food & Drink', spent: 85.2, share: 0.3914 },
  ],
  byMonth: [
    { month: '2026-06', income: 3000, expenses: 0, net: 3000 },
    { month: '2026-07', income: 0, expenses: 12.5, net: -12.5 },
  ],
  topMerchants: [{ description: 'Reddy Express', spent: 120, count: 2 }],
  recent: [
    { id: 't1', date: '2026-07-11', description: 'Transport NSW', category: 'Transportation', amount: -12.5, accountId: 'a1', visibility: 'personal' },
    { id: 't2', date: '2026-06-01', description: 'Salary', category: 'Income', amount: 3000, accountId: 'a2', visibility: 'personal' },
  ],
};

const EMPTY = {
  currency: null,
  month: null,
  availableMonths: [],
  totals: { income: 0, expenses: 0, net: 0, transactionCount: 0 },
  byCategory: [], byMonth: [], topMerchants: [], recent: [],
};

function json(body: unknown, status = 200) {
  return { status, contentType: 'application/json', body: JSON.stringify(body) };
}

/** Log the app in so RequireAuth admits the dashboard route. */
async function login(page: Page) {
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill(json({ accessToken: 'jwt-mock', tokenType: 'Bearer', expiresInSeconds: 900 })),
  );
  await page.route('**/api/v1/users/me', (route) => route.fulfill(json(PROFILE)));
  // the shared-commitments card self-fetches; default to "nothing shared yet"
  await page.route('**/api/v1/household/shared', (route) =>
    route.fulfill(json({
      currency: null, totalShared: 0, memberCount: 0, fairShare: 0,
      settlement: { yourContribution: 0, fairShare: 0, balance: 0, status: 'settled', amount: 0 },
      contributions: [], byCategory: [], transactions: [],
    })),
  );
  // the shared card also fetches the roster (to name contributions)
  await page.route('**/api/v1/households/members', (route) =>
    route.fulfill(json([{ memberId: 'm1', name: 'Jane', role: 'OWNER', isYou: true }])),
  );
  // the budget-vs-actual card self-fetches the rollup; default to "no budget"
  await page.route('**/api/v1/household/overview', (route) => route.fulfill(json({ hasBudget: false })));
}

test.describe('dashboard', () => {
  test.beforeEach(async ({ page }) => login(page));

  test('renders KPIs, category chart and recent activity', async ({ page }) => {
    await page.route('**/api/v1/dashboard', (route) => route.fulfill(json(POPULATED)));

    await page.goto('/dashboard');

    await expect(page.getByTestId('kpi-income')).toHaveText('$3,040.00');
    await expect(page.getByTestId('kpi-net')).toHaveText('$2,822.30');
    await expect(page.getByText('Spending by category')).toBeVisible();
    await expect(page.getByText('Reddy Express')).toBeVisible();
    await expect(page.getByText('Transport NSW')).toBeVisible();
  });

  test('empty state → upload a CSV → dashboard populates', async ({ page }) => {
    // dashboard is empty until the import lands, then populated
    let imported = false;
    await page.route('**/api/v1/dashboard', (route) =>
      route.fulfill(json(imported ? POPULATED : EMPTY)),
    );
    await page.route('**/api/v1/imports/transactions', (route) => {
      imported = true;
      return route.fulfill(
        json({
          importId: 'i1', fileName: 'statement.csv', currency: 'AUD',
          rowsParsed: 6, imported: 6, duplicatesSkipped: 0, failedRows: 0,
          accountsCreated: ['Everyday'], dateFrom: '2025-08-17', dateTo: '2026-07-11',
          totalIncome: 3040, totalExpenses: 217.7, net: 2822.3, errors: [],
        }, 201),
      );
    });

    await page.goto('/dashboard');
    await expect(page.getByTestId('csv-dropzone')).toBeVisible();

    await page.setInputFiles('input[type="file"]', {
      name: 'statement.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('Date,Description,Amount\n2026-07-11,Coffee,-4.50\n'),
    });

    // success toast, then the dashboard refetches and fills in
    await expect(page.getByText('Imported 6 transactions')).toBeVisible();
    await expect(page.getByTestId('kpi-income')).toHaveText('$3,040.00');
  });
});
