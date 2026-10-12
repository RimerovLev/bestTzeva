"use client";

import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { RequestItem } from "@/lib/request/RequestProvider";
import { formatPrice } from "@/lib/format";

/**
 * Панель списка заявки: позиции со снимком названия, цвета, объёма и цены; изменение количества,
 * удаление, справочная сумма с пометкой «ориентировочно». Недоступная позиция подсвечивается точечно.
 */
export function RequestItemsPanel({
  items,
  locale,
  dict,
  unavailableIds,
  onQty,
  onRemove,
}: {
  items: RequestItem[];
  locale: Locale;
  dict: Dictionary;
  unavailableIds: Set<number>;
  onQty: (variantId: number, qty: number) => void;
  onRemove: (variantId: number) => void;
}) {
  const r = dict.request;
  const total = items.reduce((sum, i) => sum + (i.price ?? 0) * i.qty, 0);

  return (
    <div className="rounded-card border border-line bg-surface shadow-card">
      <h2 className="border-b border-line px-4 py-3 text-h3 font-bold text-navy">{r.items}</h2>

      <ul>
        {items.map((item) => {
          const unavailable = unavailableIds.has(item.variantId);
          return (
            <li
              key={item.variantId}
              className={`flex items-center gap-3 border-b border-line px-4 py-3 last:border-0 ${
                unavailable ? "bg-crit-bg/40" : ""
              }`}
            >
              <span
                className="h-8 w-8 shrink-0 rounded-pill border border-line"
                style={{ backgroundColor: item.colorHex ?? "var(--line)" }}
                aria-hidden
              />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-navy">{item.name}</p>
                <p className="text-xs text-ink-faint">
                  {item.colorCode && <span>{item.colorCode} · </span>}
                  {item.volumeLiters != null && (
                    <span>
                      <bdi className="numeric">{item.volumeLiters}</bdi> {dict.product.liters}
                    </span>
                  )}
                </p>
                {unavailable && <p className="mt-1 text-xs text-crit-fg">{r.unavailable}</p>}
              </div>

              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => onQty(item.variantId, item.qty - 1)}
                  disabled={item.qty <= 1}
                  aria-label="-"
                  className="h-8 w-8 rounded-control border border-line-strong text-ink-soft disabled:opacity-40"
                >
                  −
                </button>
                <span className="numeric w-8 text-center text-sm">{item.qty}</span>
                <button
                  type="button"
                  onClick={() => onQty(item.variantId, item.qty + 1)}
                  aria-label="+"
                  className="h-8 w-8 rounded-control border border-line-strong text-ink-soft"
                >
                  +
                </button>
              </div>

              <span className="numeric w-20 text-end text-sm font-medium text-navy">
                <bdi>{formatPrice(locale, (item.price ?? 0) * item.qty)}</bdi>
              </span>

              <button
                type="button"
                onClick={() => onRemove(item.variantId)}
                aria-label={r.remove}
                className="text-ink-faint hover:text-crit-fg"
              >
                ✕
              </button>
            </li>
          );
        })}
      </ul>

      <div className="border-t border-line px-4 py-3">
        <div className="flex items-center justify-between">
          <span className="text-sm text-ink-soft">{r.indicativeTotal}</span>
          <span className="numeric text-h3 font-bold text-navy">
            <bdi>{formatPrice(locale, total)}</bdi>
          </span>
        </div>
        <p className="mt-1 text-xs text-ink-faint">{r.indicativeNote}</p>
      </div>
    </div>
  );
}
