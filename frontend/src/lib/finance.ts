/** Household finance maths for the affordability calculator + budget. */

/** Payments per year for each budget/pay frequency. */
export const FREQUENCY_PER_YEAR: Record<string, number> = {
  WEEKLY: 52,
  FORTNIGHTLY: 26,
  MONTHLY: 12,
  QUARTERLY: 4,
  ANNUALLY: 1,
};

/** Annualize a per-period amount by its frequency (0 if either is missing). */
export function annualAmount(amount: number | null, frequency: string | null): number {
  if (amount == null || !frequency) return 0;
  return amount * (FREQUENCY_PER_YEAR[frequency] ?? 0);
}

/** The monthly equivalent of a per-period amount. */
export function monthlyAmount(amount: number | null, frequency: string | null): number {
  return annualAmount(amount, frequency) / 12;
}


/**
 * The standard amortized monthly repayment for a `principal` borrowed at
 * `annualRatePercent` (e.g. 6.25) over `termYears`. Returns 0 for a
 * non-positive principal or term; handles a 0% rate as straight-line.
 *
 *   M = P·r·(1+r)^n / ((1+r)^n − 1)
 */
export function monthlyRepayment(principal: number, annualRatePercent: number, termYears: number): number {
  const n = Math.round(termYears * 12);
  if (n <= 0 || principal <= 0) return 0;
  const r = annualRatePercent / 100 / 12;
  if (r === 0) return principal / n;
  const factor = (1 + r) ** n;
  return (principal * r * factor) / (factor - 1);
}

/** One month of an amortization schedule (cumulative figures at month-end). */
export interface AmortPoint {
  /** 1-based month index. */
  month: number;
  /** Remaining balance after this month's payment. */
  balance: number;
  /** Total interest paid from month 1 through this month. */
  interestPaid: number;
}

export interface AmortResult {
  schedule: AmortPoint[];
  /** Months until the balance reaches zero (schedule length). */
  months: number;
  /** Total interest over the life of the loan. */
  totalInterest: number;
  /** false when the payment can't cover the monthly interest (never amortizes). */
  paidOff: boolean;
}

// Safety cap for the month-by-month loop: 100 years. A payment that barely
// covers interest could otherwise run for tens of thousands of iterations.
const MAX_MONTHS = 1200;

/**
 * Simulate a loan month by month at a fixed `monthlyPayment`, returning the
 * running balance + cumulative interest. `monthlyPayment` is the *total* applied
 * each month (base repayment plus any extra), so the caller compares scenarios
 * by re-running with a larger payment.
 *
 * Interest each month accrues on the opening balance; the remainder pays down
 * principal. If a payment can't cover the interest the loan never amortizes and
 * `paidOff` is false.
 */
export function amortize(principal: number, annualRatePercent: number, monthlyPayment: number): AmortResult {
  if (principal <= 0 || monthlyPayment <= 0) {
    return { schedule: [], months: 0, totalInterest: 0, paidOff: principal <= 0 };
  }
  const r = annualRatePercent / 100 / 12;
  let balance = principal;
  let interestPaid = 0;
  const schedule: AmortPoint[] = [];

  for (let month = 1; month <= MAX_MONTHS && balance > 0; month++) {
    const interest = balance * r;
    if (r > 0 && monthlyPayment <= interest) {
      return { schedule, months: schedule.length, totalInterest: interestPaid, paidOff: false };
    }
    // don't overpay the final month: cap the principal portion at the balance
    const principalPortion = Math.min(monthlyPayment - interest, balance);
    balance -= principalPortion;
    interestPaid += interest;
    schedule.push({ month, balance: Math.max(0, balance), interestPaid });
  }

  return { schedule, months: schedule.length, totalInterest: interestPaid, paidOff: balance <= 0 };
}
