import { describe, expect, it } from 'vitest';
import { annualAmount, monthlyAmount, monthlyRepayment } from './finance';

describe('annualAmount / monthlyAmount', () => {
  it('annualizes by frequency', () => {
    expect(annualAmount(100, 'WEEKLY')).toBe(5200);
    expect(annualAmount(250, 'QUARTERLY')).toBe(1000);
    expect(annualAmount(1200, 'MONTHLY')).toBe(14400);
  });

  it('gives the monthly equivalent', () => {
    expect(monthlyAmount(600, 'WEEKLY')).toBeCloseTo(2600, 0); // 600×52/12
    expect(monthlyAmount(1200, 'ANNUALLY')).toBe(100);
  });

  it('is 0 when amount or frequency is missing', () => {
    expect(annualAmount(null, 'WEEKLY')).toBe(0);
    expect(monthlyAmount(100, null)).toBe(0);
  });
});

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
