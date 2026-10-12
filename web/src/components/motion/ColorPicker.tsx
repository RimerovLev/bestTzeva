"use client";

import { useEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent } from "react";

/** HSV (h 0–360, s/v 0–1) ↔ HEX. */
function hsvToHex(h: number, s: number, v: number): string {
  const f = (n: number) => {
    const k = (n + h / 60) % 6;
    return v - v * s * Math.max(0, Math.min(k, 4 - k, 1));
  };
  return "#" + [f(5), f(3), f(1)].map((x) => Math.round(x * 255).toString(16).padStart(2, "0")).join("");
}
function hexToHsv(hex: string): [number, number, number] {
  const r = parseInt(hex.slice(1, 3), 16) / 255, g = parseInt(hex.slice(3, 5), 16) / 255, b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b), d = max - Math.min(r, g, b);
  let h = 0;
  if (d) h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
  return [(h * 60 + 360) % 360, max ? d / max : 0, max];
}
const HEX_RE = /^#[0-9a-f]{6}$/i;

/**
 * Палитра «как в Paint»: квадрат насыщенности/яркости и полоса оттенка, плюс поле HEX. Квадрат
 * управляется мышью и пальцем (тянуть) и стрелками с клавиатуры. Цвет отдаётся наверх при каждом
 * изменении; HSV держим у себя, чтобы оттенок не «прыгал» на сером и чёрном, где HEX его теряет.
 * Направление физическое (это поле цвета, а не текст) — в RTL не зеркалится.
 */
export function ColorPicker({
  value,
  onChange,
  labels,
}: {
  value: string;
  onChange: (hex: string) => void;
  labels: { square: string; hue: string; hex: string };
}) {
  const [hsv, setHsv] = useState<[number, number, number]>(() => hexToHsv(value));
  const [hexInput, setHexInput] = useState(value.toUpperCase());
  const sq = useRef<HTMLDivElement>(null);
  const last = useRef(value.toLowerCase());

  // цвет поменяли снаружи (выбрали RAL) — подстроить квадрат
  useEffect(() => {
    if (value.toLowerCase() === last.current) return;
    last.current = value.toLowerCase();
    setHsv(hexToHsv(value));
    setHexInput(value.toUpperCase());
  }, [value]);

  function emit(next: [number, number, number]) {
    setHsv(next);
    const hex = hsvToHex(...next);
    last.current = hex;
    setHexInput(hex.toUpperCase());
    onChange(hex);
  }

  function fromPointer(e: PointerEvent<HTMLDivElement>) {
    const r = sq.current?.getBoundingClientRect();
    if (!r) return;
    const s = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    const v = 1 - Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
    emit([hsv[0], s, v]);
  }

  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    const step = e.shiftKey ? 0.1 : 0.02;
    const [h, s, v] = hsv;
    const next: Record<string, [number, number, number]> = {
      ArrowLeft: [h, Math.max(0, s - step), v],
      ArrowRight: [h, Math.min(1, s + step), v],
      ArrowUp: [h, s, Math.min(1, v + step)],
      ArrowDown: [h, s, Math.max(0, v - step)],
    };
    const n = next[e.key];
    if (!n) return;
    e.preventDefault();
    emit(n);
  }

  const [h, s, v] = hsv;
  return (
    <div className="cp" dir="ltr">
      <div
        ref={sq}
        className="cp-sq"
        style={{ "--hue": `hsl(${h} 100% 50%)` } as CSSProperties}
        role="slider"
        tabIndex={0}
        aria-label={labels.square}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(s * 100)}
        aria-valuetext={hsvToHex(h, s, v).toUpperCase()}
        onKeyDown={onKey}
        onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); fromPointer(e); }}
        onPointerMove={(e) => { if (e.currentTarget.hasPointerCapture(e.pointerId)) fromPointer(e); }}
      >
        <span className="cp-dot" style={{ left: `${s * 100}%`, top: `${(1 - v) * 100}%`, background: hsvToHex(h, s, v) }} />
      </div>
      <input
        className="cp-hue"
        type="range"
        min={0}
        max={359}
        value={Math.round(h)}
        aria-label={labels.hue}
        onChange={(e) => emit([Number(e.target.value), s, v])}
      />
      <label className="cp-hex">
        <span>{labels.hex}</span>
        <input
          value={hexInput}
          maxLength={7}
          spellCheck={false}
          onChange={(e) => {
            const t = e.target.value.trim();
            const val = t.startsWith("#") ? t : `#${t}`;
            setHexInput(val.toUpperCase());
            if (HEX_RE.test(val)) {
              const hex = val.toLowerCase();
              last.current = hex;
              setHsv(hexToHsv(hex));
              onChange(hex);
            }
          }}
        />
      </label>
    </div>
  );
}
