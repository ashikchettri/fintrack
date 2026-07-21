import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, tokenStore } from './client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  fetchMock.mockReset();
  tokenStore.set(null);
});

describe('login', () => {
  it('stores the access token and sends credentials for the cookie', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { accessToken: 'jwt-abc', tokenType: 'Bearer', expiresInSeconds: 900 }),
    );

    await api.login('jane@example.com', 'pw-long-enough');

    expect(tokenStore.get()).toBe('jwt-abc');
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/auth/login');
    expect(init.credentials).toBe('include');
  });

  it('throws ApiError carrying the RFC 9457 problem body', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, { title: 'Invalid credentials', status: 401, detail: 'Invalid email or password' }),
    );

    await expect(api.login('jane@example.com', 'wrong')).rejects.toSatisfy(
      (e: unknown) =>
        e instanceof ApiError && e.status === 401 && e.problem.title === 'Invalid credentials',
    );
    expect(tokenStore.get()).toBeNull();
  });
});

describe('authenticated requests', () => {
  it('attaches the bearer token when one is held', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { email: 'jane@example.com' }));

    await api.me();

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt-held');
  });

  it('surfaces field errors from 400 validation problems', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        title: 'Validation error',
        status: 400,
        errors: { password: 'password must be between 12 and 128 characters' },
      }),
    );

    await expect(api.signup('jane@example.com', 'short')).rejects.toSatisfy(
      (e: unknown) => e instanceof ApiError && e.problem.errors?.password !== undefined,
    );
  });
});

describe('silent refresh', () => {
  it('me() retries once after a 401 when refresh succeeds', async () => {
    tokenStore.set('expired-jwt');
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401 }))
      .mockResolvedValueOnce(
        jsonResponse(200, { accessToken: 'fresh-jwt', tokenType: 'Bearer', expiresInSeconds: 900 }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { email: 'jane@example.com', role: 'OWNER' }));

    const profile = await api.me();

    expect(profile.email).toBe('jane@example.com');
    expect(tokenStore.get()).toBe('fresh-jwt');
    expect(fetchMock).toHaveBeenCalledTimes(3); // me → refresh → me
  });

  it('me() gives up when refresh also fails', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401 }))
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Invalid credentials', status: 401 }));

    await expect(api.me()).rejects.toBeInstanceOf(ApiError);
    expect(tokenStore.get()).toBeNull();
  });

  it('refresh() reports false and clears the token on failure', async () => {
    tokenStore.set('stale');
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { status: 401 }));

    await expect(api.refresh()).resolves.toBe(false);
    expect(tokenStore.get()).toBeNull();
  });
});

describe('verification endpoints', () => {
  it('verifyEmail posts email and code', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.verifyEmail('jane@example.com', '1234');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/auth/verify-email');
    expect(init.body).toBe(JSON.stringify({ email: 'jane@example.com', code: '1234' }));
  });

  it('resendVerification posts the email', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.resendVerification('jane@example.com');

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/auth/resend-verification');
  });
});

describe('account management', () => {
  it('changePassword posts current + new to the me endpoint', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.changePassword('old-secret', 'a brand new secret');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/users/me/password');
    expect(init.body).toBe(JSON.stringify({ currentPassword: 'old-secret', newPassword: 'a brand new secret' }));
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt-held');
  });

  it('requestEmailChange posts the new email and current password', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.requestEmailChange('new@example.com', 'secret');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/users/me/email');
    expect(init.body).toBe(JSON.stringify({ newEmail: 'new@example.com', currentPassword: 'secret' }));
  });

  it('confirmEmailChange posts the code', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.confirmEmailChange('123456');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/users/me/email/verify');
    expect(init.body).toBe(JSON.stringify({ code: '123456' }));
  });
});

describe('household membership', () => {
  it('inviteMember() POSTs the email with the bearer token', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 202 }));

    await api.inviteMember('partner@example.com');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/households/invites');
    expect(init.body).toBe(JSON.stringify({ email: 'partner@example.com' }));
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt-held');
  });

  it('householdMembers() GETs the roster', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, [{ memberId: 'm1', name: 'Jane', role: 'OWNER', isYou: true }]));

    const members = await api.householdMembers();

    expect(members[0].name).toBe('Jane');
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/households/members');
  });

  it('acceptInvite() POSTs to the public accept endpoint without a token', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(201, { email: 'partner@example.com', role: 'ADULT' }));

    await api.acceptInvite('partner@example.com', '123456', 'a long enough password', 'Ashik');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/households/invites/accept');
    expect(init.body).toBe(JSON.stringify({
      email: 'partner@example.com', code: '123456', password: 'a long enough password', name: 'Ashik',
    }));
    expect(new Headers(init.headers).get('Authorization')).toBeNull();
  });
});

describe('home loan', () => {
  it('getHomeLoan() GETs the profile', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { hasHomeLoan: true, loanAmount: 650000 }));

    const loan = await api.getHomeLoan();

    expect(loan.loanAmount).toBe(650000);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/home-loan');
  });

  it('saveHomeLoan() PUTs the profile', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { hasHomeLoan: true }));

    await api.saveHomeLoan({ hasHomeLoan: true, loanAmount: 650000, interestRate: 6.25 });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/household/home-loan');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe(JSON.stringify({ hasHomeLoan: true, loanAmount: 650000, interestRate: 6.25 }));
  });
});

describe('income', () => {
  it('getIncome() GETs the caller\'s income', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { annualIncome: 64000 }));

    const income = await api.getIncome();

    expect(income.annualIncome).toBe(64000);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/income');
  });

  it('saveIncome() PUTs the income', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { annualIncome: 64000 }));

    await api.saveIncome({ salaryAmount: 2000, salaryFrequency: 'FORTNIGHTLY' });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/household/income');
    expect(init.method).toBe('PUT');
  });

  it('householdIncome() GETs the summary', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { annualTotal: 154000, members: [] }));

    const summary = await api.householdIncome();

    expect(summary.annualTotal).toBe(154000);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/income/summary');
  });
});

describe('budget', () => {
  it('getBudget() GETs the budget', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { currency: 'AUD', lines: [] }));

    const budget = await api.getBudget();

    expect(budget.currency).toBe('AUD');
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/budget');
  });

  it('saveBudget() PUTs the budget', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { currency: 'AUD', lines: [] }));

    await api.saveBudget({ currency: 'AUD', lines: [] });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/household/budget');
    expect(init.method).toBe('PUT');
  });
});

describe('overview', () => {
  it('getOverview() GETs the rollup', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { hasBudget: true, planned: {}, actual: {} }));

    const o = await api.getOverview();

    expect(o.hasBudget).toBe(true);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/overview');
  });

  it('recategorizeTransactions() POSTs and returns the counts', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { reviewed: 12, changed: 3 }));

    const result = await api.recategorizeTransactions();

    expect(result).toEqual({ reviewed: 12, changed: 3 });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/transactions/recategorize');
    expect(init.method).toBe('POST');
  });
});

describe('insights', () => {
  it('getMonthlySummary() GETs, passing the month when given', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { headline: 'ok', insights: [] }));

    await api.getMonthlySummary('2026-06');

    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/insights/monthly-summary?month=2026-06');
  });

  it('askInsight() POSTs the question', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { question: 'q', answer: 'a' }));

    const result = await api.askInsight('how much on food?');

    expect(result.answer).toBe('a');
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/insights/ask');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ question: 'how much on food?' });
  });
});

describe('cash flow', () => {
  it('getCashFlow() GETs the snapshot', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { monthlySurplus: 4000, monthlyIncome: 7500 }));

    const cf = await api.getCashFlow();

    expect(cf.monthlySurplus).toBe(4000);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/cash-flow');
  });
});

describe('logout', () => {
  it('handles the 204 and clears the token', async () => {
    tokenStore.set('jwt-live');
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.logout();

    expect(tokenStore.get()).toBeNull();
  });
});

describe('finance endpoints', () => {
  it('dashboard() GETs the dashboard with the bearer token', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { currency: 'AUD', totals: { transactionCount: 0 } }));

    await api.dashboard();

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/dashboard');
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt-held');
  });

  it('dashboard(month) scopes the request to a month', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { month: '2026-07', totals: { transactionCount: 0 } }));

    await api.dashboard('2026-07');

    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/dashboard?month=2026-07');
  });

  it('householdShared(month) scopes the request to a month', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { month: '2026-07', memberCount: 0 }));

    await api.householdShared('2026-07');

    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/shared?month=2026-07');
  });

  it('transactions() GETs the transactions feed', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, []));

    await api.transactions();

    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/transactions');
  });

  it('importTransactions() posts multipart form data without a JSON content-type', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(201, { imported: 6, duplicatesSkipped: 0 }));

    const file = new File(['Date,Description,Amount\n'], 'statement.csv', { type: 'text/csv' });
    const summary = await api.importTransactions(file, 'AUD');

    expect(summary.imported).toBe(6);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/imports/transactions');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    expect(new Headers(init.headers).get('Content-Type')).toBeNull(); // browser sets the boundary
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt-held');
  });

  it('householdShared() GETs the private household view', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { totalShared: 1600, memberCount: 2 }));

    const view = await api.householdShared();

    expect(view.memberCount).toBe(2);
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/v1/household/shared');
  });

  it('setTransactionVisibility() PATCHes the visibility', async () => {
    tokenStore.set('jwt-held');
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { id: 't1', visibility: 'shared' }));

    await api.setTransactionVisibility('t1', 'shared');

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/transactions/t1/visibility');
    expect(init.method).toBe('PATCH');
    expect(init.body).toBe(JSON.stringify({ visibility: 'shared' }));
  });

  it('a finance call retries once after a 401 when a silent refresh succeeds', async () => {
    tokenStore.set('expired-jwt');
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { title: 'Unauthorized', status: 401 }))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'fresh', tokenType: 'Bearer', expiresInSeconds: 900 }))
      .mockResolvedValueOnce(jsonResponse(200, { currency: 'AUD', totals: { transactionCount: 0 } }));

    await api.dashboard();

    expect(fetchMock).toHaveBeenCalledTimes(3); // dashboard → refresh → dashboard
    expect(tokenStore.get()).toBe('fresh');
  });
});
