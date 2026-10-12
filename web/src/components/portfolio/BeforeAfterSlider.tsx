"use client";

import { useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type SyntheticEvent } from "react";

const hideBroken = (e: SyntheticEvent<HTMLImageElement>) => { e.currentTarget.style.visibility = "hidden"; };

/**
 * Сравнение «до/после» с перетаскиваемым разделителем: мышь, палец (pointer events, вертикальная
 * прокрутка страницы не блокируется) и клавиатура (role=slider, стрелки, Home/End). Картинка не имеет
 * направления письма — контейнер всегда LTR: «до» слева, «после» справа и в иврите тоже, иначе
 * подписи разъезжаются с фото. Пропорции фиксированы — без CLS.
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
    else return;
    e.preventDefault();
  }

  return (
    <div
      ref={ref}
      dir="ltr"
      className="cmp"
      style={{ "--pos": `${pos}%` } as CSSProperties}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    >
      {/* «после» — база */}
      {/* eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио; next/image требует whitelisting CDN */}
      <img src={afterUrl} alt={alt} loading="lazy" onError={hideBroken} />
      {/* «до» — поверх, обрезано до pos% слева */}
      <div className="cmp-before">
        {/* eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио */}
        <img src={beforeUrl} alt="" loading="lazy" onError={hideBroken} />
      </div>
      <span className="tag tag-before">{beforeLabel}</span>
      <span className="tag tag-after">{afterLabel}</span>
      <div className="handle" />
      <button
        type="button"
        role="slider"
        className="cmp-knob"
        aria-label={`${beforeLabel} / ${afterLabel}`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(pos)}
        onKeyDown={onKeyDown}
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#121212" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M9 6l-6 6 6 6M15 6l6 6-6 6" />
        </svg>
      </button>
    </div>
  );
}
