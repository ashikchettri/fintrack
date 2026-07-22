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
  month: string | null;          // selected month ("2026-07") or null for all-time
  availableMonths: string[];     // months with activity, newest first
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
  month: string | null;
  availableMonths: string[];
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

/**
 * The household's home-loan profile — jointly held. `hasHomeLoan = false` means
 * "no loan / not answered yet". Amounts are numbers; interest is an annual %.
 */
export interface HomeLoan {
  hasHomeLoan: boolean;
  lender: string | null;
  loanAmount: number | null;
  interestRate: number | null;
  repaymentFrequency: 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY' | null;
  repaymentAmount: number | null;
  hasOffset: boolean;
  offsetBalance: number | null;
  ownership: 'JOINT' | 'SOLE' | null;
  currency: string;
  notes: string | null;
  updatedBy?: string | null;
  updatedAt?: string | null;
}

/** Dashboard rollup — the household budget (plan) vs the latest month's actuals. */
export interface Overview {
  currency: string;
  hasBudget: boolean;
  actualMonth: string | null;
  planned: { income: number; expenses: number; savings: number; leftover: number };
  actual: { income: number; expenses: number };
  /** Planned vs actual per canonical expense category, biggest budget first (ADR 008). */
  byCategory: { category: string; planned: number; actual: number }[];
}

/** Result of re-running the categorizer over the caller's transactions (ADR 009). */
export interface RecategorizeResult {
  reviewed: number;
  changed: number;
}

/** AI (or template) spending summary for a month (ADR 012). */
export interface MonthlySummary {
  month: string | null;
  currency: string | null;
  totals: { income: number; expenses: number; net: number; transactionCount: number };
  headline: string;
  insights: string[];
}

/** A grounded answer to a natural-language spending question (ADR 013). */
export interface InsightAnswer {
  question: string;
  answer: string;
}

export type NetWorthKind = 'ASSET' | 'LIABILITY';

/** One editable balance-sheet row (ADR 014). */
export interface NetWorthItem {
  kind: NetWorthKind;
  category: string | null;
  name: string;
  value: number;
}

/** The editable manual balance sheet. */
export interface NetWorthItems {
  currency: string;
  items: NetWorthItem[];
}

/** The net-worth summary — manual items folded with the home loan (ADR 014). */
export interface NetWorth {
  currency: string;
  totalAssets: number;
  totalLiabilities: number;
  netWorth: number;
  assets: NetWorthLine[];
  liabilities: NetWorthLine[];
}

/** One line in the net-worth breakdown; `source` is MANUAL or HOME_LOAN (derived). */
export interface NetWorthLine {
  name: string;
  category: string | null;
  value: number;
  source: 'MANUAL' | 'HOME_LOAN';
}

export type BudgetSection = 'INCOME' | 'EXPENSE' | 'SAVING';
export type BudgetFrequency = 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY' | 'QUARTERLY' | 'ANNUALLY';

/** One planned budget line — monthly/annual are derived from amount × frequency. */
export interface BudgetLine {
  section: BudgetSection;
  category: string | null;
  name: string;
  frequency: BudgetFrequency | null;
  amount: number | null;
}

/** The household budget: income, expenses (categorized) and savings. */
export interface Budget {
  currency: string;
  lines: BudgetLine[];
}

/**
 * The household's monthly cash-flow snapshot — inputs to the affordability
 * question. `monthlySurplus = monthlyIncome − monthlyAvgSpending`.
 */
export interface CashFlow {
  currency: string;
  monthlyIncome: number;
  monthlyLoanRepayment: number;
  monthlyAvgSpending: number;
  monthlySurplus: number;
  monthsOfSpendingData: number;
}

/** A household member for the roster (names for the shared-commitments view). */
export interface MemberResponse {
  memberId: string;
  name: string;
  role: string;
  isYou: boolean;
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