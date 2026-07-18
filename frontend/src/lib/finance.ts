/** Household finance maths for the affordability calculator. */

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
