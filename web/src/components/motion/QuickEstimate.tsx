"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type CSSProperties } from "react";
import type { Locale } from "@/lib/i18n/config";
import { formatPrice } from "@/lib/format";
import { resolveErrorMessage, type ErrorMessages } from "@/lib/api/messages";
import { calculateService, MEASURE_PREFILL_KEY, type CalculateRequest } from "@/lib/services/api";
import { track } from "@/lib/analytics";

export interface QuickEstimateText {
  title: string;
  body: string;
  typeLabel: string;
  apartment: string;
  house: string;
  area: string;
  areaUnit: string;
  estimate: string;
  idle: string;
  calc: string;
  calculating: string;
  toMeasure: string;
  fullCalc: string;
}

const MIN = 20, MAX = 300;
type Kind = "APARTMENT" | "HOUSE";

/**
 * Быстрая оценка на главной. Цену считает сервер (POST /service-requests/calculate) — клиент
 * только форматирует; запрос по кнопке, а не на каждое движение ползунка (лимит 60/час на IP).
 * Число «досчитывается» анимацией от предыдущего значения. Параметры переносятся в заявку на замер.
 */
export function QuickEstimate({ locale, text, errors }: { locale: Locale; text: QuickEstimateText; errors: ErrorMessages }) {
  const router = useRouter();
  const [kind, setKind] = useState<Kind>("APARTMENT");
  const [area, setArea] = useState(80);
  const [range, setRange] = useState<{ min: number; max: number } | null>(null);
  const [shown, setShown] = useState<{ min: number; max: number } | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const raf = useRef(0);

  const body = (): CalculateRequest => ({ property_type: kind, area_sqm: area, work_types: ["WALLS"] });

  function animateTo(target: { min: number; max: number }) {
    cancelAnimationFrame(raf.current);
    const from = shown ?? { min: 0, max: 0 };
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) { setShown(target); return; }
    const t0 = performance.now();
    const step = (now: number) => {
      const k = Math.min(1, (now - t0) / 520), e = 1 - Math.pow(1 - k, 3);
      setShown({ min: Math.round(from.min + (target.min - from.min) * e), max: Math.round(from.max + (target.max - from.max) * e) });
      if (k < 1) raf.current = requestAnimationFrame(step);
      else setShown(target);
    };
    raf.current = requestAnimationFrame(step);
  }

  async function calc() {
    setLoading(true);
    setError(null);
    try {
      const res = await calculateService(body(), { locale });
      const next = { min: res.price_min ?? 0, max: res.price_max ?? res.price_min ?? 0 };
      setRange(next);
      setNote(res.disclaimer ?? null);
      animateTo(next);
      track("Service Calculator Used", { source: "home", property_type: kind, locale });
    } catch (err) {
      setError(resolveErrorMessage(errors, err).text);
    } finally {
      setLoading(false);
    }
  }

  function toMeasure() {
    try { sessionStorage.setItem(MEASURE_PREFILL_KEY, JSON.stringify(body())); } catch { /* приватный режим */ }
    router.push(`/${locale}/services/measure`);
  }

  // параметры поменялись — старая цифра больше не про них
  const stale = (k: Kind, a: number) => { setKind(k); setArea(a); setRange(null); setNote(null); };
  const fill = ((area - MIN) / (MAX - MIN)) * 100;

  return (
    <div className="est card reveal">
      <div>
        <h3>{text.title}</h3>
        <p className="small" style={{ fontSize: 15 }}>{text.body}</p>
        <div className="seg2" role="group" aria-label={text.typeLabel}>
          <button type="button" aria-pressed={kind === "APARTMENT"} onClick={() => stale("APARTMENT", area)}>{text.apartment}</button>
          <button type="button" aria-pressed={kind === "HOUSE"} onClick={() => stale("HOUSE", area)}>{text.house}</button>
        </div>
        <div className="range-row">
          <label htmlFor="est-area">{text.area}</label>
          <span><bdi>{area} {text.areaUnit}</bdi></span>
        </div>
        <input
          className="rng"
          id="est-area"
          type="range"
          min={MIN}
          max={MAX}
          step={5}
          value={area}
          style={{ "--fill": `${fill}%` } as CSSProperties}
          onChange={(e) => stale(kind, Number(e.target.value))}
        />
        <div className="est-actions">
          <button className="btn btn-red btn-sm" type="button" onClick={calc} disabled={loading}>
            {loading ? text.calculating : text.calc}
          </button>
          <button className="btn btn-sm" type="button" onClick={toMeasure}>{text.toMeasure}</button>
        </div>
        {error && <p className="est-err" role="alert">{error}</p>}
      </div>
      <div className="est-out" aria-live="polite">
        <small>{text.estimate}</small>
        {range && shown ? (
          <bdi>{shown.min === shown.max ? formatPrice(locale, shown.min) : `${formatPrice(locale, shown.min)}–${formatPrice(locale, shown.max)}`}</bdi>
        ) : (
          <span className="muted">{text.idle}</span>
        )}
        {note && range && <p className="small" style={{ marginTop: 12, fontFamily: "var(--f-body)" }}>{note}</p>}
        <p className="small" style={{ marginTop: 12, fontFamily: "var(--f-body)" }}>
          <Link href={`/${locale}/services`}>{text.fullCalc}</Link>
        </p>
      </div>
    </div>
  );
}
