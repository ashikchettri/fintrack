/**
 * Categorical colours for charts. Chosen to stay legible on both light and dark
 * backgrounds (medium saturation/lightness), and to be distinguishable in order.
 */
export const CATEGORY_COLORS = [
  '#6366f1', // indigo
  '#f59e0b', // amber
  '#10b981', // emerald
  '#ef4444', // red
  '#3b82f6', // blue
  '#ec4899', // pink
  '#14b8a6', // teal
  '#a855f7', // purple
  '#84cc16', // lime
  '#f97316', // orange
];

export function colorAt(index: number): string {
  return CATEGORY_COLORS[index % CATEGORY_COLORS.length];
}

// semantic money colours (income vs spend)
export const INCOME_COLOR = '#10b981';
export const EXPENSE_COLOR = '#ef4444';
