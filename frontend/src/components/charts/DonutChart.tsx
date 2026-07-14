import { colorAt } from './palette';

export interface DonutSlice {
  label: string;
  value: number;
}

interface DonutChartProps {
  data: DonutSlice[];
  /** rendered in the hole and used for the accessible summary */
  centerLabel?: string;
  centerValue?: string;
  formatValue?: (value: number) => string;
  /** cap the number of slices; the rest are rolled into "Other" (default 7) */
  maxSlices?: number;
}

const SIZE = 180;
const STROKE = 24;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const OTHER_COLOR = '#94a3b8'; // slate — neutral, clearly "the rest"

/**
 * A donut (pie with a hole) for category breakdown, drawn as stacked SVG circle
 * segments via stroke-dasharray — no chart library. Long tails are grouped into
 * a single "Other" slice so the legend stays short and the slices stay readable.
 */
export function DonutChart({ data, centerLabel, centerValue, formatValue, maxSlices = 7 }: DonutChartProps) {
  const total = data.reduce((sum, s) => sum + s.value, 0);
  const fmt = formatValue ?? ((v: number) => v.toFixed(2));

  if (total <= 0) {
    return <p className="text-sm text-muted-foreground">No spending to chart yet.</p>;
  }

  // keep the biggest slices; roll everything past the cap into one "Other"
  const sorted = [...data].sort((a, b) => b.value - a.value);
  const slices: (DonutSlice & { isOther?: boolean })[] =
    sorted.length > maxSlices
      ? [
          ...sorted.slice(0, maxSlices),
          {
            label: `Other (${sorted.length - maxSlices})`,
            value: sorted.slice(maxSlices).reduce((s, x) => s + x.value, 0),
            isOther: true,
          },
        ]
      : sorted;

  let offset = 0;
  const segments = slices.map((slice, i) => {
    const fraction = slice.value / total;
    const dash = fraction * CIRCUMFERENCE;
    const seg = {
      color: slice.isOther ? OTHER_COLOR : colorAt(i),
      dasharray: `${dash} ${CIRCUMFERENCE - dash}`,
      dashoffset: -offset,
      label: slice.label,
      value: slice.value,
      share: fraction,
    };
    offset += dash;
    return seg;
  });

  const summary = segments.map((s) => `${s.label} ${Math.round(s.share * 100)}%`).join(', ');

  return (
    <div className="flex flex-col items-center gap-6 sm:flex-row sm:items-center sm:gap-8">
      <svg
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        className="size-40 shrink-0 -rotate-90"
        role="img"
        aria-label={`Spending by category: ${summary}`}
      >
        {segments.map((s) => (
          <circle
            key={s.label}
            cx={SIZE / 2}
            cy={SIZE / 2}
            r={RADIUS}
            fill="none"
            stroke={s.color}
            strokeWidth={STROKE}
            strokeDasharray={s.dasharray}
            strokeDashoffset={s.dashoffset}
          >
            <title>{`${s.label}: ${fmt(s.value)} (${Math.round(s.share * 100)}%)`}</title>
          </circle>
        ))}
        {centerValue && (
          <text
            x="50%"
            y="50%"
            className="fill-foreground text-[13px] font-semibold"
            textAnchor="middle"
            dominantBaseline="middle"
            transform={`rotate(90 ${SIZE / 2} ${SIZE / 2})`}
          >
            {centerValue}
          </text>
        )}
      </svg>

      <ul className="w-full min-w-0 space-y-1.5" aria-hidden="true">
        {segments.map((s) => (
          <li key={s.label} className="flex items-center gap-2 text-sm">
            <span
              className="inline-block size-3 shrink-0 rounded-sm"
              style={{ backgroundColor: s.color }}
            />
            <span className="min-w-0 flex-1 truncate">{s.label}</span>
            <span className="shrink-0 tabular-nums font-medium">{fmt(s.value)}</span>
            <span className="w-9 shrink-0 text-right tabular-nums text-muted-foreground">
              {Math.round(s.share * 100)}%
            </span>
          </li>
        ))}
      </ul>
      {centerLabel && <span className="sr-only">{centerLabel}</span>}
    </div>
  );
}
