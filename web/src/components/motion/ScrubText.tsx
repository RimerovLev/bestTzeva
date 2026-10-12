"use client";

import { useEffect, useRef } from "react";
import { clamp } from "@/lib/motion/sweep";

/**
 * Абзац, который «проявляется» по словам в такт прокрутке. Слова уже размечены на сервере
 * (текст доступен без JS и скринридерам целиком), клиент только меняет прозрачность.
 */
export function ScrubText({ text }: { text: string }) {
  const ref = useRef<HTMLParagraphElement>(null);
  const words = text.trim().split(/\s+/);

  useEffect(() => {
    const el = ref.current;
    if (!el || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const spans = Array.from(el.querySelectorAll<HTMLElement>(".w"));
    let ticking = false;
    const update = () => {
      const r = el.getBoundingClientRect();
      const p = clamp((innerHeight * 0.85 - r.top) / (r.height + innerHeight * 0.35), 0, 1), k = p * (spans.length + 3);
      spans.forEach((s, i) => { s.style.opacity = (0.25 + 0.75 * clamp(k - i, 0, 1)).toFixed(2); });
    };
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => { update(); ticking = false; });
    };
    update();
    addEventListener("scroll", onScroll, { passive: true });
    return () => removeEventListener("scroll", onScroll);
  }, []);

  return (
    <p className="scrub" ref={ref}>
      {words.map((w, i) => (
        <span key={i}><span className="w">{w}</span>{i < words.length - 1 ? " " : ""}</span>
      ))}
    </p>
  );
}
