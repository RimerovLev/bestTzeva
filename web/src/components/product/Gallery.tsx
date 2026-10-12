"use client";

import { useState, useEffect, type CSSProperties } from "react";
import type { ProductMediaView } from "@/lib/catalog/api";

/**
 * Галерея карточки: медиа товара + выбранного варианта. Фото в ответе API может не быть — тогда
 * банка на круге цвета выбранного варианта (его настоящий HEX из API), круг перекрашивается при
 * смене варианта. Пропорции зафиксированы, чтобы вёрстка не дёргалась (CLS).
 */
export function Gallery({ media, alt, color }: { media: ProductMediaView[]; alt: string; color: string | null }) {
  const images = media.filter((m) => m.type === "IMAGE" && m.url);
  const [active, setActive] = useState(0);

  // Смена набора картинок (например, при выборе варианта) — вернуться к первой.
  useEffect(() => setActive(0), [media]);

  if (images.length === 0) {
    return (
      <div className="card gal-main gal-ph" style={{ "--c": color ?? "var(--yellow)" } as CSSProperties} role="img" aria-label={alt}>
        <div className="prod-can" />
      </div>
    );
  }

  const current = images[Math.min(active, images.length - 1)]!;

  return (
    <div className="gal">
      <div className="card gal-main">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={current.url} alt={alt} width={600} height={600} />
      </div>
      {images.length > 1 && (
        <div className="thumbs">
          {images.map((img, i) => (
            <button key={img.id} type="button" className="thumb" onClick={() => setActive(i)} aria-current={i === active}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={img.url} alt="" width={64} height={64} />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
