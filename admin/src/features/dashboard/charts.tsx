import { useState } from "react";

export interface Series { key: string; label: string; color: string; values: number[] }

/**
 * Линейный график заявок по времени (SVG, без библиотеки: нужны две линии и подсказка — это
 * дешевле, чем тянуть Recharts ради одного экрана). Ось времени — слева направо и в иврите
 * (docs/design-system.md, «RTL и две локали» п.4), поэтому контейнер dir="ltr". Цвета серий —
 * токены админки.
 */
export function LineChart({ labels, series, height = 240, emptyLabel }: {
  labels: string[];
  series: Series[];
  height?: number;
  emptyLabel: string;
}) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 720, H = height, padL = 34, padR = 12, padT = 12, padB = 28;
  const n = labels.length;
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  const top = niceMax(max);
  const x = (i: number) => padL + (n <= 1 ? (W - padL - padR) / 2 : (i * (W - padL - padR)) / (n - 1));
  const y = (v: number) => padT + (1 - v / top) * (H - padT - padB);
  const ticks = [0, top / 2, top];
  const every = Math.max(1, Math.ceil(n / 8));
  const total = series.reduce((a, s) => a + s.values.reduce((b, v) => b + v, 0), 0);

  if (n === 0 || total === 0) {
    return <div className="grid place-items-center text-sm text-ink-faint" style={{ height }}>{emptyLabel}</div>;
  }

  return (
    <div dir="ltr" className="relative">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" role="img" aria-label={series.map((s) => s.label).join(", ")}
        onMouseLeave={() => setHover(null)}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={padL} x2={W - padR} y1={y(t)} y2={y(t)} stroke="var(--line)" strokeDasharray={t ? "3 4" : undefined} />
            <text x={padL - 6} y={y(t) + 4} textAnchor="end" fontSize="11" fill="var(--ink-faint)">{Math.round(t)}</text>
          </g>
        ))}
        {labels.map((l, i) => (i % every === 0 || i === n - 1) && (
          <text key={i} x={x(i)} y={H - 8} textAnchor="middle" fontSize="11" fill="var(--ink-faint)">{l}</text>
        ))}
        {series.map((s) => {
          const d = s.values.map((v, i) => `${i ? "L" : "M"}${x(i).toFixed(1)} ${y(v).toFixed(1)}`).join(" ");
          const area = `${d} L${x(n - 1).toFixed(1)} ${y(0)} L${x(0).toFixed(1)} ${y(0)} Z`;
          return (
            <g key={s.key}>
              <path d={area} fill={s.color} opacity="0.08" />
              <path d={d} fill="none" stroke={s.color} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
              {hover !== null && <circle cx={x(hover)} cy={y(s.values[hover] ?? 0)} r="4" fill="var(--surface)" stroke={s.color} strokeWidth="2.5" />}
            </g>
          );
        })}
        {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={padT} y2={H - padB} stroke="var(--line-strong)" />}
        {/* зоны наведения по точкам */}
        {labels.map((_, i) => (
          <rect key={i} x={x(i) - (W - padL - padR) / Math.max(1, n - 1) / 2} y={padT} width={(W - padL - padR) / Math.max(1, n - 1)}
            height={H - padT - padB} fill="transparent" onMouseEnter={() => setHover(i)} />
        ))}
      </svg>
      {hover !== null && (
        <div className="pointer-events-none absolute top-1 rounded-control border border-line bg-surface px-3 py-2 text-xs shadow-card"
          style={{ left: `clamp(0px, calc(${(x(hover) / W) * 100}% - 70px), calc(100% - 150px))` }}>
          <div className="mb-1 font-medium text-navy">{labels[hover]}</div>
          {series.map((s) => (
            <div key={s.key} className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-1.5"><i className="inline-block h-2 w-2 rounded-pill" style={{ background: s.color }} />{s.label}</span>
              <b className="numeric">{s.values[hover] ?? 0}</b>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** «Красивый» верх оси: 1/2/5 × 10^n. */
function niceMax(v: number): number {
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const m = v / p;
  return (m <= 1 ? 1 : m <= 2 ? 2 : m <= 5 ? 5 : 10) * p;
}

export interface Slice { key: string; label: string; value: number; color: string }

/** Кольцевая диаграмма с легендой: доля каждого элемента в сумме (спрос по цветам). */
export function Donut({ slices, centerLabel, emptyLabel }: { slices: Slice[]; centerLabel: string; emptyLabel: string }) {
  const total = slices.reduce((a, s) => a + s.value, 0);
  if (total === 0) return <div className="grid h-48 place-items-center text-sm text-ink-faint">{emptyLabel}</div>;
  const R = 70, r = 46, C = 90;
  let acc = 0;
  const arcs = slices.map((s) => {
    const a0 = (acc / total) * Math.PI * 2 - Math.PI / 2;
    acc += s.value;
    const a1 = (acc / total) * Math.PI * 2 - Math.PI / 2;
    const large = a1 - a0 > Math.PI ? 1 : 0;
    const p = (rad: number, a: number) => `${(C + rad * Math.cos(a)).toFixed(2)} ${(C + rad * Math.sin(a)).toFixed(2)}`;
    // полный круг одной долей рисуем двумя дугами (SVG не умеет дугу в 360°)
    const d = slices.length === 1
      ? `M${p(R, -Math.PI / 2)} A${R} ${R} 0 1 1 ${p(R, Math.PI / 2)} A${R} ${R} 0 1 1 ${p(R, -Math.PI / 2)} M${p(r, -Math.PI / 2)} A${r} ${r} 0 1 0 ${p(r, Math.PI / 2)} A${r} ${r} 0 1 0 ${p(r, -Math.PI / 2)}Z`
      : `M${p(R, a0)} A${R} ${R} 0 ${large} 1 ${p(R, a1)} L${p(r, a1)} A${r} ${r} 0 ${large} 0 ${p(r, a0)} Z`;
    return { ...s, d };
  });
  return (
    <div className="flex flex-wrap items-center gap-6">
      <svg viewBox="0 0 180 180" className="h-44 w-44 shrink-0" role="img" aria-label={centerLabel}>
        {arcs.map((a) => <path key={a.key} d={a.d} fill={a.color} fillRule="evenodd" stroke="var(--surface)" strokeWidth="2" />)}
        <text x={C} y={C - 2} textAnchor="middle" fontSize="22" fontWeight="700" fill="var(--navy)">{total}</text>
        <text x={C} y={C + 16} textAnchor="middle" fontSize="10" fill="var(--ink-faint)">{centerLabel}</text>
      </svg>
      <ul className="min-w-[9rem] flex-1 space-y-2 text-sm">
        {slices.map((s) => (
          <li key={s.key} className="flex items-center justify-between gap-3">
            <span className="flex min-w-0 items-center gap-2">
              <i className="inline-block h-3 w-3 shrink-0 rounded-pill" style={{ background: s.color }} />
              <bdi className="truncate">{s.label}</bdi>
            </span>
            <span className="numeric text-ink-soft"><bdi>{Math.round((s.value / total) * 100)}%</bdi></span>
          </li>
        ))}
      </ul>
    </div>
  );
}
