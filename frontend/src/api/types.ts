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

// ---- finance-service DTOs (mirror the Java records) ----------------------
// Money arrives as JSON numbers; `amount` is signed (spend < 0, income > 0).

/** Result of a CSV upload — the payload the "upload → dashboard" moment shows. */
export interface ImportSummary {
  importId: string;
  fileName: string;
  currency: string;
  rowsParsed: number;
  imported: number;
  duplicatesSkipped: number;
  failedRows: number;
  accountsCreated: string[];
  dateFrom: string | null;
  dateTo: string | null;
  totalIncome: number;
  totalExpenses: number;
  net: number;
  errors: { rowNumber: number; message: string }[];
}

export interface DashboardResponse {
  currency: string | null;
  totals: {
    income: number;
    expenses: number;
    net: number;
    transactionCount: number;
  };
  byCategory: { category: string; spent: number; share: number }[];
  byMonth: { month: string; income: number; expenses: number; net: number }[];
  topMerchants: { description: string; spent: number; count: number }[];
  recent: {
    id: string;
    date: string;
    description: string;
    category: string | null;
    amount: number;
    accountId: string;
    visibility: string;
  }[];
}

/**
 * The private household view of shared commitments (ADR 006) — only shared items
 * and agreed totals, never anyone's personal spending.
 */
export interface SharedHouseholdView {
  currency: string | null;
  totalShared: number;
  memberCount: number;
  fairShare: number;
  settlement: {
    yourContribution: number;
    fairShare: number;
    balance: number;
    status: 'owed' | 'owes' | 'settled';
    amount: number;
  };
  contributions: { memberId: string; covered: number; isYou: boolean }[];
  byCategory: { category: string; amount: number }[];
  transactions: TransactionResponse[];
}

export interface TransactionResponse {
  id: string;
  accountId: string;
  date: string;
  description: string;
  category: string | null;
  subcategory: string | null;
  amount: number;
  currency: string;
  tags: string | null;
  notes: string | null;
  visibility: string;
  source: string;
}