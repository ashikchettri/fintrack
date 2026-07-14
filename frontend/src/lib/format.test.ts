import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatMonth, formatPercent } from './format';

describe('formatMoney', () => {
  it('formats a positive amount with the currency symbol', () => {
    expect(formatMoney(1234.5, 'AUD')).toContain('1,234.50');
  });

  it('keeps the sign for spend (negative amounts)', () => {
    expect(formatMoney(-12.5, 'AUD')).toMatch(/-.*12\.50/);
  });

  it('defaults to AUD when currency is null', () => {
    expect(formatMoney(10, null)).toContain('10.00');
  });

  it('falls back to a plain number for an invalid currency code', () => {
    expect(formatMoney(9.99, 'NOTACODE')).toBe('9.99');
  });
});

describe('formatPercent', () => {
  it('rounds a 0–1 share to a whole percent', () => {
    expect(formatPercent(0.6086)).toBe('61%');
    expect(formatPercent(0)).toBe('0%');
  });
});

describe('formatMonth', () => {
  it('turns YYYY-MM into a short month label', () => {
    expect(formatMonth('2026-07')).toBe('Jul 2026');
  });

  it('returns the input unchanged when unparseable', () => {
    expect(formatMonth('garbage')).toBe('garbage');
  });
});

describe('formatDate', () => {
  it('formats an ISO date', () => {
    expect(formatDate('2026-07-11')).toMatch(/2026/);
  });

  it('returns the input unchanged when unparseable', () => {
    expect(formatDate('not-a-date')).toBe('not-a-date');
  });
});
