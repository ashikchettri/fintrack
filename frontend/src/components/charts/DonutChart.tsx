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
}

const SIZE = 180;
const STROKE = 26;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * A donut (pie with a hole) for category breakdown, drawn as stacked SVG circle
 * segments via stroke-dasharray — no chart library. Rotated so slices start at
 * 12 o'clock. Accompanied by a legend with values and shares.
 */
export function DonutChart({ data, centerLabel, centerValue, formatValue }: DonutChartProps) {
  const total = data.reduce((sum, s) => sum + s.value, 0);
  const fmt = formatValue ?? ((v: number) => v.toFixed(2));

  if (total <= 0) {
    return <p className="text-sm text-muted-foreground">No spending to chart yet.</p>;
  }

  let offset = 0;
  const segments = data.map((slice, i) => {
    const fraction = slice.value / total;
    const dash = fraction * CIRCUMFERENCE;
    const seg = {
      color: colorAt(i),
      dasharray: `${dash} ${CIRCUMFERENCE - dash}`,
      dashoffset: -offset,
      label: slice.label,
      value: slice.value,
      share: fraction,
    };
    offset += dash;
    return seg;
  });

  const summary = segments
    .map((s) => `${s.label} ${Math.round(s.share * 100)}%`)
    .join(', ');

  return (
    <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-center sm:gap-8">
      <svg
        viewBox={`0 0 ${SIZE} ${SIZE}`}
        className="h-44 w-44 shrink-0 -rotate-90"
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
            className="rotate-90 fill-foreground text-[13px] font-semibold"
            textAnchor="middle"
            dominantBaseline="middle"
            transform={`rotate(90 ${SIZE / 2} ${SIZE / 2})`}
          >
            {centerValue}
          </text>
        )}
      </svg>

      <ul className="w-full space-y-1.5" aria-hidden="true">
        {segments.map((s) => (
          <li key={s.label} className="flex items-center gap-2 text-sm">
            <span
              className="inline-block size-3 shrink-0 rounded-sm"
              style={{ backgroundColor: s.color }}
            />
            <span className="truncate">{s.label}</span>
            <span className="ml-auto tabular-nums font-medium">{fmt(s.value)}</span>
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
