import { describe, expect, it } from 'vitest';
import { monthlyRepayment } from './finance';

describe('monthlyRepayment', () => {
  it('amortizes a standard loan', () => {
    // $500k at 6.25% over 30y ≈ $3,079/month
    expect(monthlyRepayment(500000, 6.25, 30)).toBeCloseTo(3079.0, 0);
  });

  it('handles a 0% rate as straight-line', () => {
    expect(monthlyRepayment(12000, 0, 1)).toBe(1000); // 12000 / 12
  });

  it('returns 0 for a non-positive principal or term', () => {
    expect(monthlyRepayment(0, 6, 30)).toBe(0);
    expect(monthlyRepayment(500000, 6, 0)).toBe(0);
  });
});
