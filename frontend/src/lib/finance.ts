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
