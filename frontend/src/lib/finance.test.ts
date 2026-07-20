import { describe, expect, it } from 'vitest';
import { amortize, annualAmount, monthlyAmount, monthlyRepayment } from './finance';

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

describe('amortize', () => {
  it('pays off a standard loan over its term and totals the interest', () => {
    // $500k at 6.25% over 30y: the amortized payment clears the loan in ~360 months
    const payment = monthlyRepayment(500000, 6.25, 30);
    const result = amortize(500000, 6.25, payment);

    expect(result.paidOff).toBe(true);
    expect(result.months).toBeGreaterThanOrEqual(359);
    expect(result.months).toBeLessThanOrEqual(360);
    // total interest on a 30y $500k loan at 6.25% is roughly $608k
    expect(result.totalInterest).toBeGreaterThan(600000);
    expect(result.totalInterest).toBeLessThan(620000);
    // the final balance lands at zero
    expect(result.schedule.at(-1)?.balance).toBe(0);
  });

  it('paying extra clears the loan sooner and pays less interest', () => {
    const payment = monthlyRepayment(500000, 6.25, 30);
    const base = amortize(500000, 6.25, payment);
    const withExtra = amortize(500000, 6.25, payment + 500);

    expect(withExtra.months).toBeLessThan(base.months);
    expect(withExtra.totalInterest).toBeLessThan(base.totalInterest);
  });

  it('handles a 0% rate as straight-line principal', () => {
    const result = amortize(12000, 0, 1000);
    expect(result.months).toBe(12);
    expect(result.totalInterest).toBe(0);
    expect(result.paidOff).toBe(true);
  });

  it('reports paidOff=false when the payment cannot cover the interest', () => {
    // $500k at 6% accrues $2,500 interest in month 1; a $1,000 payment never amortizes
    const result = amortize(500000, 6, 1000);
    expect(result.paidOff).toBe(false);
    expect(result.schedule).toHaveLength(0);
  });

  it('returns an empty schedule for a non-positive principal or payment', () => {
    expect(amortize(0, 6, 1000).schedule).toHaveLength(0);
    expect(amortize(500000, 6, 0).schedule).toHaveLength(0);
    expect(amortize(0, 6, 1000).paidOff).toBe(true);
  });
});
