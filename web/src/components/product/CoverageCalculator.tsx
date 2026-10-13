"use client";

import { useState, type FormEvent } from "react";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { calcCoverage, type CoverageCalcResult } from "@/lib/catalog/api";
import { resolveErrorMessage } from "@/lib/api/messages";
import { track } from "@/lib/analytics";

const COATS = [1, 2, 3];

/**
 * Калькулятор расхода — главный интерактивный элемент карточки (B-04). Пересчёт по кнопке, а не на
 * каждый ввод. Результат берётся с сервера (BigDecimal), клиент только показывает.
 */
export function CoverageCalculator({
  slug,
  locale,
  dict,
  selectedVariantId,
}: {
  slug: string;
  locale: Locale;
  dict: Dictionary;
  selectedVariantId: number | null;
}) {
  const p = dict.product;
  const [area, setArea] = useState("");
  const [coats, setCoats] = useState(2);
  const [result, setResult] = useState<CoverageCalcResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const areaNum = Number(area);
    if (!Number.isFinite(areaNum) || areaNum <= 0) return;
    setLoading(true);
    setError(null);
    try {
      setResult(await calcCoverage(slug, areaNum, coats, { locale }));
      track("Coverage Calculated", { coats, locale });
    } catch (err) {
      setError(resolveErrorMessage(dict.errors, err).text);
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  const selected = result?.variants?.find((v) => v.variant_id === selectedVariantId) ?? result?.variants?.[0];

  return (
    <form onSubmit={onSubmit} className="card calc-card">
      <h2>{p.calcTitle}</h2>

      <div className="calc-fields">
        <div className="field">
          <label htmlFor="cov-area">{p.calcArea}</label>
          <input
            id="cov-area"
            type="number"
            inputMode="decimal"
            min={0}
            step="0.5"
            dir="ltr"
            value={area}
            onChange={(e) => setArea(e.target.value)}
          />
        </div>

        <div>
          <p className="lbl">{p.calcCoats}</p>
          <div className="seg2" role="group" aria-label={p.calcCoats}>
            {COATS.map((c) => (
              <button key={c} type="button" onClick={() => setCoats(c)} aria-pressed={coats === c}>
                <bdi>{c}</bdi>
              </button>
            ))}
          </div>
        </div>

        <button type="submit" disabled={loading || !area} className="btn btn-red btn-sm">
          {loading ? dict.catalog.loading : p.calcButton}
        </button>
      </div>

      {error && <p className="form-err" role="alert">{error}</p>}

      {result && !result.calculable && <p className="small">{p.calcNotAvailable}</p>}

      {result?.calculable && selected && (
        <div className="calc-res" aria-live="polite">
          <b>
            {p.calcResult
              .replace("{cans}", String(selected.cans))
              .replace("{volume}", String(selected.volume_liters ?? ""))}
          </b>
          <p>{p.calcLeftover.replace("{liters}", String(selected.leftover_liters ?? ""))}</p>
          <p>{p.calcEstimate}</p>
        </div>
      )}
    </form>
  );
}
