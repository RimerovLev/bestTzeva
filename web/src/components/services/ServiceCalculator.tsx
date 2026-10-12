"use client";

import { useState, type FormEvent, type ReactNode } from "react";
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
    <div className="calc2">
      <form onSubmit={onCalculate} className="card form-card tb-form">
        <h2>{s.calcTitle}</h2>

        <div className="form-grid">
          <Field label={s.propertyType}>
            <select value={propertyType} onChange={(e) => setPropertyType(e.target.value as PropertyType)}>
              {PROPERTY_TYPES.map((p) => <option key={p} value={p}>{dict.enums.property_type[p]}</option>)}
            </select>
          </Field>
          <Field label={s.area}><input type="number" inputMode="decimal" min={0} dir="ltr" value={area} onChange={(e) => setArea(e.target.value)} /></Field>
          <Field label={s.ceiling}><input type="number" step="0.1" dir="ltr" value={ceiling} onChange={(e) => setCeiling(e.target.value)} /></Field>
          <Field label={s.rooms}><input type="number" dir="ltr" value={rooms} onChange={(e) => setRooms(e.target.value)} /></Field>
        </div>

        <CheckGroup label={s.workTypes} values={WORK_TYPES} selected={workTypes} labels={dict.enums.work_type} onToggle={(v) => setWorkTypes((l) => toggle(l, v))} />
        <CheckGroup label={s.options} values={SERVICE_OPTIONS} selected={options} labels={dict.enums.service_option} onToggle={(v) => setOptions((l) => toggle(l, v))} />

        <label className="chk">
          <input type="checkbox" checked={urgent} onChange={(e) => setUrgent(e.target.checked)} />
          {s.urgent}
        </label>

        {error && <p className="form-err" role="alert">{error}</p>}

        <button type="submit" disabled={loading} className="btn btn-red">
          {loading ? s.calculating : s.calcButton}
        </button>
      </form>

      <div className="card res-card" aria-live="polite">
        <div className="res-head">
          <h2>{s.resultTitle}</h2>
          {result?.is_estimate && <span className="pill">{s.estimateBadge}</span>}
        </div>

        {!result ? (
          <p className="res-empty">{s.resultEmpty}</p>
        ) : (
          <>
            <div>
              <p className="lbl">{s.range}</p>
              <p className="res-range" style={{ margin: 0 }}>
                <bdi>{formatPrice(locale, result.price_min ?? 0)}–{formatPrice(locale, result.price_max ?? 0)}</bdi>
              </p>
            </div>

            {(result.breakdown?.length ?? 0) > 0 && (
              <div>
                <p className="lbl">{s.breakdown}</p>
                <table className="bd">
                  <tbody>
                    {(result.breakdown ?? []).map((b, i) => (
                      <tr key={i}>
                        <td>{b.work_type ? dict.enums.work_type[b.work_type.toUpperCase() as keyof Dictionary["enums"]["work_type"]] ?? b.work_type : ""}</td>
                        <td className="m"><bdi>{b.area_sqm} {s.areaUnit}</bdi></td>
                        <td><b><bdi>{formatPrice(locale, b.price_min ?? 0)}–{formatPrice(locale, b.price_max ?? 0)}</bdi></b></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {result.disclaimer && <p className="note-box">{result.disclaimer}</p>}

            <button type="button" onClick={toMeasure} className="btn btn-red">{s.toMeasure}</button>
          </>
        )}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="field"><span className="field-l">{label}</span>{children}</label>;
}

function CheckGroup<T extends string>({ label, values, selected, labels, onToggle }: {
  label: string; values: readonly T[]; selected: T[]; labels: Record<string, string>; onToggle: (v: T) => void;
}) {
  return (
    <div>
      <p className="lbl">{label}</p>
      <div className="opt-list" role="group" aria-label={label}>
        {values.map((v) => (
          <button key={v} type="button" className="opt" onClick={() => onToggle(v)} aria-pressed={selected.includes(v)}>
            {labels[v] ?? v}
          </button>
        ))}
      </div>
    </div>
  );
}
