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

describe('logout', () => {
  it('handles the 204 and clears the token', async () => {
    tokenStore.set('jwt-live');
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await api.logout();

    expect(tokenStore.get()).toBeNull();
  });
});
