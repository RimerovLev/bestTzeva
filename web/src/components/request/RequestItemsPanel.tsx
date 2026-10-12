"use client";

import type { CSSProperties } from "react";
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
    <div className="card req-card">
      <h2>{r.items}</h2>

      <ul className="req-list">
        {items.map((item) => {
          const unavailable = unavailableIds.has(item.variantId);
          return (
            <li key={item.variantId} className={`req-row${unavailable ? " bad" : ""}`}>
              <span className="req-sw" style={{ "--c": item.colorHex ?? "var(--paper)" } as CSSProperties} aria-hidden />
              <div className="min-w-0">
                <b>{item.name}</b>
                <span className="req-meta">
                  {item.colorCode && <bdi>{item.colorCode}</bdi>}
                  {item.colorCode && item.volumeLiters != null && " · "}
                  {item.volumeLiters != null && <><bdi>{item.volumeLiters}</bdi> {dict.product.liters}</>}
                </span>
                {unavailable && <p className="req-bad">{r.unavailable}</p>}
              </div>

              <div className="qty">
                <button type="button" onClick={() => onQty(item.variantId, item.qty - 1)} disabled={item.qty <= 1} aria-label={r.qtyMinus}>−</button>
                <span aria-label={r.qty}><bdi>{item.qty}</bdi></span>
                <button type="button" onClick={() => onQty(item.variantId, item.qty + 1)} aria-label={r.qtyPlus}>+</button>
              </div>

              <span className="req-sum"><bdi>{formatPrice(locale, (item.price ?? 0) * item.qty)}</bdi></span>

              <button type="button" className="rm" onClick={() => onRemove(item.variantId)} aria-label={`${r.remove}: ${item.name}`}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" aria-hidden><path d="M6 6l12 12M18 6 6 18" /></svg>
              </button>
            </li>
          );
        })}
      </ul>

      <div className="req-total">
        <div>
          <span>{r.indicativeTotal}</span>
          <strong><bdi>{formatPrice(locale, total)}</bdi></strong>
        </div>
        <p>{r.indicativeNote}</p>
      </div>
    </div>
  );
}
