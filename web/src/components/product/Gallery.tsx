"use client";

import { useState, useEffect } from "react";
import type { ProductMediaView } from "@/lib/catalog/api";

/**
 * Галерея карточки: медиа товара + выбранного варианта. Если картинок нет (в ответе API их может
 * не быть) — нейтральный плейсхолдер. Размеры зафиксированы, чтобы не дёргать вёрстку (CLS).
 */
export function Gallery({ media, alt }: { media: ProductMediaView[]; alt: string }) {
  const images = media.filter((m) => m.type === "IMAGE" && m.url);
  const [active, setActive] = useState(0);

  // Смена набора картинок (например, при выборе варианта) — вернуться к первой.
  useEffect(() => setActive(0), [media]);

  if (images.length === 0) {
    return (
      <div className="flex aspect-square items-center justify-center rounded-card border border-line bg-gradient-to-br from-paper-warm to-sage-pale">
        <span className="display text-display text-line-strong/70">בסט</span>
      </div>
    );
  }

  const current = images[Math.min(active, images.length - 1)]!;

  return (
    <div className="flex flex-col gap-3">
      <div className="aspect-square overflow-hidden rounded-card border border-line bg-surface">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={current.url} alt={alt} className="h-full w-full object-cover" width={600} height={600} />
      </div>
      {images.length > 1 && (
        <div className="flex gap-2 overflow-x-auto">
          {images.map((img, i) => (
            <button
              key={img.id}
              type="button"
              onClick={() => setActive(i)}
              aria-current={i === active}
              className={`h-16 w-16 shrink-0 overflow-hidden rounded-control border-2 transition-colors ${
                i === active ? "border-brass" : "border-line hover:border-line-strong"
              }`}
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={img.url} alt="" className="h-full w-full object-cover" width={64} height={64} />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
