"use client";

import { useState, type FormEvent } from "react";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { calcCoverage, type CoverageCalcResult } from "@/lib/catalog/api";
import { resolveErrorMessage } from "@/lib/api/messages";

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
    } catch (err) {
      setError(resolveErrorMessage(dict.errors, err).text);
      setResult(null);
    } finally {
      setLoading(false);
    }
  }

  const selected = result?.variants?.find((v) => v.variant_id === selectedVariantId) ?? result?.variants?.[0];

  return (
    <form onSubmit={onSubmit} className="rounded-card border border-line bg-surface p-5 shadow-card">
      <h2 className="text-h3 font-bold text-navy">{p.calcTitle}</h2>

      <div className="mt-4 flex flex-wrap items-end gap-4">
        <label className="flex flex-col gap-1">
          <span className="text-sm text-ink-soft">{p.calcArea}</span>
          <input
            type="number"
            inputMode="decimal"
            min={0}
            step="0.5"
            value={area}
            onChange={(e) => setArea(e.target.value)}
            className="numeric w-32 rounded-control border border-line-strong bg-surface px-3 py-2 text-body focus:border-brass focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
          />
        </label>

        <div className="flex flex-col gap-1">
          <span className="text-sm text-ink-soft">{p.calcCoats}</span>
          <div className="flex gap-1" role="group" aria-label={p.calcCoats}>
            {COATS.map((c) => (
              <button
                key={c}
                type="button"
                onClick={() => setCoats(c)}
                aria-pressed={coats === c}
                className={`h-10 w-10 rounded-control border text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta ${
                  coats === c ? "border-brass bg-brass/10 font-medium text-navy" : "border-line-strong text-ink-soft hover:bg-paper-warm"
                }`}
              >
                <bdi className="numeric">{c}</bdi>
              </button>
            ))}
          </div>
        </div>

        <button
          type="submit"
          disabled={loading || !area}
          className="rounded-control bg-navy px-5 py-2 text-sm font-medium text-surface transition-colors hover:bg-navy-deep disabled:opacity-50"
        >
          {loading ? dict.catalog.loading : p.calcButton}
        </button>
      </div>

      {error && <p className="mt-3 text-sm text-crit-fg">{error}</p>}

      {result && !result.calculable && (
        <p className="mt-4 text-sm text-ink-soft">{p.calcNotAvailable}</p>
      )}

      {result?.calculable && selected && (
        <div className="mt-4 rounded-control bg-paper-warm/60 p-4">
          <p className="text-h3 font-bold text-navy">
            {p.calcResult
              .replace("{cans}", String(selected.cans))
              .replace("{volume}", String(selected.volume_liters ?? ""))}
          </p>
          <p className="mt-1 text-sm text-ink-soft">
            {p.calcLeftover.replace("{liters}", String(selected.leftover_liters ?? ""))}
          </p>
          <p className="mt-2 text-xs text-ink-faint">{p.calcEstimate}</p>
        </div>
      )}
    </form>
  );
}
