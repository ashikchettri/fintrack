import type { AmortPoint } from '@/lib/finance';

export interface PayoffSeries {
  points: AmortPoint[];
  color: string;
  label: string;
}

interface PayoffChartProps {
  series: PayoffSeries[];
  /** widest x extent, in months, across the series (the slower payoff) */
  xMaxMonths: number;
  /** starting loan balance — the top of the y-axis */
  yMax: number;
  formatValue: (value: number) => string;
}

// A fixed viewBox the SVG scales into; the element itself is width:100%.
const W = 640;
const H = 240;
const PAD_L = 56;
const PAD_R = 12;
const PAD_T = 12;
const PAD_B = 28;
const PLOT_W = W - PAD_L - PAD_R;
const PLOT_H = H - PAD_T - PAD_B;

/**
 * Loan balance over time — one line per scenario (e.g. minimum repayments vs
 * with extra). Hand-rolled SVG polylines, no chart library: x is months, y is
 * the outstanding balance. Each line starts from the full principal at month 0.
 */
export function PayoffChart({ series, xMaxMonths, yMax, formatValue }: PayoffChartProps) {
  if (xMaxMonths <= 0 || yMax <= 0) {
    return <p className="text-sm text-muted-foreground">Enter a loan amount and rate to see the payoff.</p>;
  }

  const x = (month: number) => PAD_L + (month / xMaxMonths) * PLOT_W;
  const y = (balance: number) => PAD_T + (1 - balance / yMax) * PLOT_H;

  // horizontal gridlines / y labels at 0, ¼, ½, ¾, full
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((f) => f * yMax);
  // x labels every 5 years, plus the final year
  const totalYears = Math.ceil(xMaxMonths / 12);
  const yearStep = totalYears > 12 ? 5 : totalYears > 6 ? 2 : 1;
  const yearTicks: number[] = [];
  for (let yr = 0; yr <= totalYears; yr += yearStep) yearTicks.push(yr);

  const linePoints = (points: AmortPoint[]) =>
    [`${x(0)},${y(yMax)}`, ...points.map((p) => `${x(p.month)},${y(p.balance)}`)].join(' ');

  return (
    <div>
      <div className="mb-3 flex flex-wrap gap-4 text-xs text-muted-foreground">
        {series.map((s) => (
          <span key={s.label} className="flex items-center gap-1.5">
            <span className="inline-block h-0.5 w-4 rounded-full" style={{ backgroundColor: s.color }} />
            {s.label}
          </span>
        ))}
      </div>

      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="h-auto w-full"
        role="img"
        aria-label="Loan balance over time"
        preserveAspectRatio="xMidYMid meet"
      >
        {/* y gridlines + labels */}
        {yTicks.map((value) => (
          <g key={value}>
            <line
              x1={PAD_L}
              x2={W - PAD_R}
              y1={y(value)}
              y2={y(value)}
              stroke="currentColor"
              className="text-border"
              strokeWidth={1}
            />
            <text
              x={PAD_L - 8}
              y={y(value) + 3}
              textAnchor="end"
              className="fill-muted-foreground text-[10px]"
            >
              {formatValue(value)}
            </text>
          </g>
        ))}

        {/* x labels (years) */}
        {yearTicks.map((yr) => (
          <text
            key={yr}
            x={x(yr * 12)}
            y={H - 8}
            textAnchor="middle"
            className="fill-muted-foreground text-[10px]"
          >
            {yr}y
          </text>
        ))}

        {/* one polyline per scenario */}
        {series.map((s) => (
          <polyline
            key={s.label}
            points={linePoints(s.points)}
            fill="none"
            stroke={s.color}
            strokeWidth={2.5}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        ))}
      </svg>
    </div>
  );
}
