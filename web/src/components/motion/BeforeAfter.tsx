"use client";

import { useEffect, useRef, useState, type CSSProperties } from "react";
import { sweepHint } from "@/lib/motion/sweep";

/**
 * До / после на одной фотографии (левая половина — до, правая — после). Шторка — нативный range:
 * клавиатура и скринридер из коробки. При первом показе шторка сама проезжает, подсказывая жест.
 * Картинка не имеет направления письма — внутри всегда LTR, иначе в иврите ползунок идёт наоборот.
 */
export function BeforeAfter({ before, after, beforeAlt, afterAlt, rangeLabel }: {
  before: string; after: string; beforeAlt: string; afterAlt: string; rangeLabel: string;
}) {
  const box = useRef<HTMLDivElement>(null);
  const range = useRef<HTMLInputElement>(null);
  const touched = useRef(false);
  const [pos, setPos] = useState(50);

  useEffect(() => {
    const el = box.current, input = range.current;
    if (!el || !input || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let cancel: (() => void) | undefined;
    const io = new IntersectionObserver(([en]) => {
      if (!en?.isIntersecting || touched.current) return;
      io.disconnect();
      cancel = sweepHint((v) => setPos(v), 88, 50, 1600, () => touched.current);
    }, { threshold: 0.55 });
    io.observe(el);
    return () => { io.disconnect(); cancel?.(); };
  }, []);

  return (
    <div className="ba reveal" ref={box} dir="ltr" style={{ "--pos": `${pos}%` } as CSSProperties}>
      <div className="ba-layer ba-after" role="img" aria-label={afterAlt} />
      <div className="ba-layer ba-before" role="img" aria-label={beforeAlt} />
      <span className="tag tag-before">{before}</span>
      <span className="tag tag-after">{after}</span>
      <div className="handle" />
      <input
        ref={range}
        type="range"
        min={0}
        max={100}
        step={0.1}
        value={pos}
        aria-label={rangeLabel}
        onChange={(e) => { touched.current = true; setPos(Number(e.target.value)); }}
      />
    </div>
  );
}
