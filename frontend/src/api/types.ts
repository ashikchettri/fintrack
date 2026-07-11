// Mirrors the auth-service DTOs (Java records) — the typed contract between UI and API.

export interface SignupResponse {
  userId: string;
  email: string;
  householdId: string;
  householdName: string;
  role: string;
  createdAt: string;
}

// Refresh token is deliberately absent: it travels only as an httpOnly cookie (ADR 003)
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface UserResponse {
  userId: string;
  email: string;
  householdId: string;
  householdName: string;
  role: string;
  createdAt: string;
}

// RFC 9457 problem body; `errors` is our field→message extension on 400s
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}