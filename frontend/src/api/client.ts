import type { LoginResponse, ProblemDetail, SignupResponse, UserResponse } from './types';

/** A non-2xx response, carrying the parsed RFC 9457 problem body. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

// Access token lives in module memory only — never localStorage/sessionStorage
// (ADR 003: persistence is the refresh cookie's job, and JS can't touch that).
let accessToken: string | null = null;

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string | null): void => {
    accessToken = token;
  },
};

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);

  // credentials: include → the browser attaches/stores the httpOnly refresh cookie
  const response = await fetch(path, { ...init, headers, credentials: 'include' });

  if (response.status === 204) return undefined as T;
  const body: unknown = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, body as ProblemDetail);
  return body as T;
}

export const api = {
  signup(email: string, password: string): Promise<SignupResponse> {
    return request<SignupResponse>('/api/v1/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  },

  verifyEmail(email: string, code: string): Promise<void> {
    return request<void>('/api/v1/auth/verify-email', {
      method: 'POST',
      body: JSON.stringify({ email, code }),
    });
  },

  resendVerification(email: string): Promise<void> {
    return request<void>('/api/v1/auth/resend-verification', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  async login(email: string, password: string): Promise<LoginResponse> {
    const res = await request<LoginResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    tokenStore.set(res.accessToken);
    return res;
  },

  /** Silent refresh: succeeds iff the browser still holds a live refresh cookie. */
  async refresh(): Promise<boolean> {
    try {
      const res = await request<LoginResponse>('/api/v1/auth/refresh', { method: 'POST' });
      tokenStore.set(res.accessToken);
      return true;
    } catch {
      tokenStore.set(null);
      return false;
    }
  },

  async logout(): Promise<void> {
    await request<void>('/api/v1/auth/logout', { method: 'POST' });
    tokenStore.set(null);
  },

  async me(): Promise<UserResponse> {
    try {
      return await request<UserResponse>('/api/v1/users/me');
    } catch (error) {
      // access token expired mid-session: one silent refresh, then retry once
      if (error instanceof ApiError && error.status === 401 && (await api.refresh())) {
        return request<UserResponse>('/api/v1/users/me');
      }
      throw error;
    }
  },
};