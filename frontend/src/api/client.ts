import type {
  CashFlow,
  DashboardResponse,
  HomeLoan,
  HouseholdIncome,
  Income,
  ImportSummary,
  LoginResponse,
  MemberResponse,
  ProblemDetail,
  SharedHouseholdView,
  SignupResponse,
  TransactionResponse,
  UserResponse,
} from './types';

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

/** Multipart POST (file upload): let the browser set the multipart boundary. */
async function upload<T>(path: string, form: FormData): Promise<T> {
  const headers = new Headers();
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);

  const response = await fetch(path, { method: 'POST', body: form, headers, credentials: 'include' });
  const body: unknown = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, body as ProblemDetail);
  return body as T;
}

/**
 * Run an authenticated call, and if the access token has expired (401), do one
 * silent refresh and retry — the same recovery api.me() uses, shared so every
 * finance call survives a mid-session token expiry.
 */
async function withRefresh<T>(call: () => Promise<T>): Promise<T> {
  try {
    return await call();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401 && (await api.refresh())) {
      return call();
    }
    throw error;
  }
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

  forgotPassword(email: string): Promise<void> {
    return request<void>('/api/v1/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  },

  resetPassword(email: string, code: string, newPassword: string): Promise<void> {
    return request<void>('/api/v1/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ email, code, newPassword }),
    });
  },

  // ---- authenticated account management (bearer token attached automatically) ----

  changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return request<void>('/api/v1/users/me/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    });
  },

  requestEmailChange(newEmail: string, currentPassword: string): Promise<void> {
    return request<void>('/api/v1/users/me/email', {
      method: 'POST',
      body: JSON.stringify({ newEmail, currentPassword }),
    });
  },

  confirmEmailChange(code: string): Promise<void> {
    return request<void>('/api/v1/users/me/email/verify', {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  },

  // ---- household membership (auth-service) ----

  /** OWNER invites an email to the household; the invitee is emailed a code. */
  inviteMember(email: string): Promise<void> {
    return withRefresh(() => request<void>('/api/v1/households/invites', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }));
  },

  /** The caller's household roster — names for the shared-commitments view. */
  householdMembers(): Promise<MemberResponse[]> {
    return withRefresh(() => request<MemberResponse[]>('/api/v1/households/members'));
  },

  /** Public: accept an invite, creating an account inside the inviting household. */
  acceptInvite(email: string, code: string, password: string, name: string): Promise<SignupResponse> {
    return request<SignupResponse>('/api/v1/households/invites/accept', {
      method: 'POST',
      body: JSON.stringify({ email, code, password, name }),
    });
  },

  // ---- household financial profile (finance-service) ----

  /** The household's home-loan profile (empty shape if none saved yet). */
  getHomeLoan(): Promise<HomeLoan> {
    return withRefresh(() => request<HomeLoan>('/api/v1/household/home-loan'));
  },

  /** Upsert the household's home-loan profile. */
  saveHomeLoan(loan: Partial<HomeLoan>): Promise<HomeLoan> {
    return withRefresh(() => request<HomeLoan>('/api/v1/household/home-loan', {
      method: 'PUT',
      body: JSON.stringify(loan),
    }));
  },

  /** The caller's own income (empty shape if none saved). */
  getIncome(): Promise<Income> {
    return withRefresh(() => request<Income>('/api/v1/household/income'));
  },

  /** Upsert the caller's own income. */
  saveIncome(income: Partial<Income>): Promise<Income> {
    return withRefresh(() => request<Income>('/api/v1/household/income', {
      method: 'PUT',
      body: JSON.stringify(income),
    }));
  },

  /** The household's combined income — every member's annual income + the total. */
  householdIncome(): Promise<HouseholdIncome> {
    return withRefresh(() => request<HouseholdIncome>('/api/v1/household/income/summary'));
  },

  /** The household's monthly cash-flow snapshot (income, spending, surplus). */
  getCashFlow(): Promise<CashFlow> {
    return withRefresh(() => request<CashFlow>('/api/v1/household/cash-flow'));
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

  // ---- finance-service (proxied to :8082); each survives a token expiry ----

  dashboard(month?: string): Promise<DashboardResponse> {
    const query = month ? `?month=${encodeURIComponent(month)}` : '';
    return withRefresh(() => request<DashboardResponse>(`/api/v1/dashboard${query}`));
  },

  transactions(): Promise<TransactionResponse[]> {
    return withRefresh(() => request<TransactionResponse[]>('/api/v1/transactions'));
  },

  /** Upload a bank CSV; returns what landed (imported / duplicates / accounts). */
  importTransactions(file: File, currency: string): Promise<ImportSummary> {
    const form = new FormData();
    form.append('file', file);
    form.append('currency', currency);
    return withRefresh(() => upload<ImportSummary>('/api/v1/imports/transactions', form));
  },

  /** The private household view of shared commitments (ADR 006). */
  householdShared(month?: string): Promise<SharedHouseholdView> {
    const query = month ? `?month=${encodeURIComponent(month)}` : '';
    return withRefresh(() => request<SharedHouseholdView>(`/api/v1/household/shared${query}`));
  },

  /** Mark/unmark one of your own transactions as a shared commitment. */
  setTransactionVisibility(id: string, visibility: 'shared' | 'personal'): Promise<TransactionResponse> {
    return withRefresh(() => request<TransactionResponse>(
      `/api/v1/transactions/${id}/visibility`,
      { method: 'PATCH', body: JSON.stringify({ visibility }) },
    ));
  },
};