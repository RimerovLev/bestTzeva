"use client";

import { useRef, useState, type PointerEvent, type KeyboardEvent } from "react";

/**
 * Сравнение «до/после» с перетаскиваемым разделителем. Работает мышью, пальцем (pointer events) и с
 * клавиатуры (role=slider, стрелки). Контейнер с фиксированным соотношением сторон — картинки не
 * дёргают вёрстку (CLS). Направление физическое (не зависит от RTL) — это сравнение изображений.
 */
export function BeforeAfterSlider({
  beforeUrl,
  afterUrl,
  beforeLabel,
  afterLabel,
  alt,
}: {
  beforeUrl: string;
  afterUrl: string;
  beforeLabel: string;
  afterLabel: string;
  alt: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState(50);
  const dragging = useRef(false);

  function fromClientX(clientX: number) {
    const rect = ref.current?.getBoundingClientRect();
    if (!rect) return;
    setPos(Math.max(0, Math.min(100, ((clientX - rect.left) / rect.width) * 100)));
  }

  function onPointerDown(e: PointerEvent<HTMLDivElement>) {
    dragging.current = true;
    e.currentTarget.setPointerCapture(e.pointerId);
    fromClientX(e.clientX);
  }
  function onPointerMove(e: PointerEvent<HTMLDivElement>) {
    if (dragging.current) fromClientX(e.clientX);
  }
  function onPointerUp(e: PointerEvent<HTMLDivElement>) {
    dragging.current = false;
    if (e.currentTarget.hasPointerCapture?.(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId);
  }
  function onKeyDown(e: KeyboardEvent) {
    if (e.key === "ArrowLeft") setPos((p) => Math.max(0, p - 2));
    else if (e.key === "ArrowRight") setPos((p) => Math.min(100, p + 2));
    else if (e.key === "Home") setPos(0);
    else if (e.key === "End") setPos(100);
  }

  return (
    <div
      ref={ref}
      className="relative aspect-[16/10] w-full cursor-ew-resize touch-none select-none overflow-hidden rounded-card border border-line bg-paper-warm"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      {/* «после» — база */}
      {/* eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио; next/image требует whitelisting CDN */}
      <img src={afterUrl} alt={alt} loading="lazy" className="absolute inset-0 h-full w-full object-cover" />
      <span className="absolute bottom-2 inset-inline-end-2 rounded-pill bg-navy-deep/70 px-2 py-0.5 text-xs text-surface">{afterLabel}</span>

      {/* «до» — поверх, обрезано до pos% слева */}
      <div className="absolute inset-0 overflow-hidden" style={{ clipPath: `inset(0 ${100 - pos}% 0 0)` }}>
        {/* eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио */}
        <img src={beforeUrl} alt={alt} loading="lazy" className="absolute inset-0 h-full w-full object-cover" />
        <span className="absolute bottom-2 inset-inline-start-2 rounded-pill bg-navy-deep/70 px-2 py-0.5 text-xs text-surface">{beforeLabel}</span>
      </div>

      {/* разделитель + ручка */}
      <div className="absolute inset-y-0" style={{ left: `${pos}%` }}>
        <div className="absolute inset-y-0 -ms-px w-0.5 bg-surface" />
        <button
          type="button"
          role="slider"
          aria-label={`${beforeLabel} / ${afterLabel}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(pos)}
          onKeyDown={onKeyDown}
          className="absolute top-1/2 grid h-9 w-9 -translate-x-1/2 -translate-y-1/2 cursor-ew-resize place-items-center rounded-pill border-2 border-brass bg-surface text-brass shadow-card focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
        >
          ↔
        </button>
      </div>
    </div>
  );
}
