"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { formatPrice } from "@/lib/format";
import { resolveErrorMessage } from "@/lib/api/messages";
import {
  calculateService,
  MEASURE_PREFILL_KEY,
  PROPERTY_TYPES,
  WORK_TYPES,
  SERVICE_OPTIONS,
  type CalculateRequest,
  type CalculateResponse,
} from "@/lib/services/api";

type PropertyType = (typeof PROPERTY_TYPES)[number];
type WorkType = (typeof WORK_TYPES)[number];
type ServiceOption = (typeof SERVICE_OPTIONS)[number];

export function ServiceCalculator({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const s = dict.services;
  const router = useRouter();

  const [propertyType, setPropertyType] = useState<PropertyType>("APARTMENT");
  const [area, setArea] = useState("");
  const [ceiling, setCeiling] = useState("");
  const [rooms, setRooms] = useState("");
  const [workTypes, setWorkTypes] = useState<WorkType[]>(["WALLS"]);
  const [options, setOptions] = useState<ServiceOption[]>([]);
  const [urgent, setUrgent] = useState(false);

  const [result, setResult] = useState<CalculateResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggle<T>(list: T[], value: T): T[] {
    return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
  }

  function buildBody(): CalculateRequest {
    return {
      property_type: propertyType,
      area_sqm: area ? Number(area) : 0,
      ceiling_height_m: ceiling ? Number(ceiling) : undefined,
      rooms_count: rooms ? Number(rooms) : undefined,
      work_types: workTypes,
      options,
      urgent,
    };
  }

  async function onCalculate(e: FormEvent) {
    e.preventDefault();
    if (workTypes.length === 0 || !area) {
      setError(s.chooseWork);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setResult(await calculateService(buildBody(), { locale }));
    } catch (err) {
      setError(resolveErrorMessage(dict.errors, err).text);
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  function toMeasure() {
    try {
      sessionStorage.setItem(MEASURE_PREFILL_KEY, JSON.stringify(buildBody()));
    } catch {
      // приватный режим — параметры просто не перенесутся
    }
    router.push(`/${locale}/services/measure`);
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2 lg:items-start">
      <form onSubmit={onCalculate} className="rounded-card border border-line bg-surface p-6 shadow-card">
        <h2 className="text-h2 font-bold text-navy">{s.calcTitle}</h2>

        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label={s.propertyType}>
            <select value={propertyType} onChange={(e) => setPropertyType(e.target.value as PropertyType)} className={inputCls}>
              {PROPERTY_TYPES.map((p) => <option key={p} value={p}>{dict.enums.property_type[p]}</option>)}
            </select>
          </Field>
          <Field label={s.area}><input type="number" inputMode="decimal" min={0} value={area} onChange={(e) => setArea(e.target.value)} className={`numeric ${inputCls}`} /></Field>
          <Field label={s.ceiling}><input type="number" step="0.1" value={ceiling} onChange={(e) => setCeiling(e.target.value)} className={`numeric ${inputCls}`} /></Field>
          <Field label={s.rooms}><input type="number" value={rooms} onChange={(e) => setRooms(e.target.value)} className={`numeric ${inputCls}`} /></Field>
        </div>

        <CheckGroup label={s.workTypes} values={WORK_TYPES} selected={workTypes} labels={dict.enums.work_type} onToggle={(v) => setWorkTypes((l) => toggle(l, v))} />
        <CheckGroup label={s.options} values={SERVICE_OPTIONS} selected={options} labels={dict.enums.service_option} onToggle={(v) => setOptions((l) => toggle(l, v))} />

        <label className="mt-4 flex items-center gap-2 text-sm text-ink-soft">
          <input type="checkbox" checked={urgent} onChange={(e) => setUrgent(e.target.checked)} className="accent-brass" />
          {s.urgent}
        </label>

        {error && <p className="mt-3 text-sm text-crit-fg">{error}</p>}

        <button type="submit" disabled={loading} className="mt-5 w-full rounded-control bg-navy px-6 py-3 font-medium text-surface transition-colors hover:bg-navy-deep disabled:opacity-50">
          {loading ? s.calculating : s.calcButton}
        </button>
      </form>

      <div className="rounded-card border border-line bg-surface p-6 shadow-card">
        <div className="flex items-center justify-between">
          <h2 className="text-h2 font-bold text-navy">{s.resultTitle}</h2>
          {result?.is_estimate && <span className="rounded-pill bg-warn-bg px-3 py-1 text-xs text-warn-fg">{s.estimateBadge}</span>}
        </div>

        {!result ? (
          <p className="mt-6 text-ink-faint">—</p>
        ) : (
          <div className="mt-4 space-y-4">
            <div>
              <p className="text-sm text-ink-soft">{s.range}</p>
              <p className="text-display font-bold text-navy">
                <bdi className="numeric">{formatPrice(locale, result.price_min ?? 0)}–{formatPrice(locale, result.price_max ?? 0)}</bdi>
              </p>
            </div>

            {(result.breakdown?.length ?? 0) > 0 && (
              <div>
                <p className="mb-1 text-sm font-semibold text-ink-soft">{s.breakdown}</p>
                <table className="w-full text-sm">
                  <tbody>
                    {(result.breakdown ?? []).map((b, i) => (
                      <tr key={i} className="border-b border-line/60 last:border-0">
                        <td className="py-1.5">{b.work_type ? dict.enums.work_type[b.work_type.toUpperCase() as keyof Dictionary["enums"]["work_type"]] ?? b.work_type : ""}</td>
                        <td className="numeric py-1.5 text-end text-ink-soft">{b.area_sqm} m²</td>
                        <td className="numeric py-1.5 text-end font-medium text-navy">
                          <bdi>{formatPrice(locale, b.price_min ?? 0)}–{formatPrice(locale, b.price_max ?? 0)}</bdi>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {result.disclaimer && <p className="rounded-control bg-paper-warm px-3 py-2 text-xs text-ink-soft">{result.disclaimer}</p>}

            <button type="button" onClick={toMeasure} className="w-full rounded-control bg-brass px-6 py-3 font-medium text-surface transition-colors hover:bg-brass-light">
              {s.toMeasure}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

const inputCls = "rounded-control border border-line-strong bg-surface px-3 py-2 text-body outline-none focus:border-brass focus-visible:ring-2 focus-visible:ring-terracotta";

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="flex flex-col gap-1"><span className="text-sm font-medium text-ink-soft">{label}</span>{children}</label>;
}

function CheckGroup<T extends string>({ label, values, selected, labels, onToggle }: {
  label: string; values: readonly T[]; selected: T[]; labels: Record<string, string>; onToggle: (v: T) => void;
}) {
  return (
    <div className="mt-4">
      <p className="mb-2 text-sm font-medium text-ink-soft">{label}</p>
      <div className="flex flex-wrap gap-2">
        {values.map((v) => {
          const active = selected.includes(v);
          return (
            <button key={v} type="button" onClick={() => onToggle(v)} aria-pressed={active}
              className={`rounded-control border px-3 py-1.5 text-sm transition-colors ${active ? "border-brass bg-brass/10 font-medium text-navy" : "border-line-strong text-ink-soft hover:bg-paper-warm"}`}>
              {labels[v] ?? v}
            </button>
          );
        })}
      </div>
    </div>
  );
}
