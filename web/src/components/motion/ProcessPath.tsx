"use client";

import { useEffect, useRef } from "react";
import { clamp } from "@/lib/motion/sweep";

export interface ProcessStep { title: string; body: string }

/**
 * Четыре шага, соединённые линией, которая прорисовывается по мере прокрутки. Путь строится по
 * реальным центрам кружков (Catmull-Rom → Безье), поэтому одинаково работает в 4, 2 и 1 колонку
 * и в обоих направлениях письма.
 */
export function ProcessPath({ steps }: { steps: ProcessStep[] }) {
  const wrap = useRef<HTMLDivElement>(null);
  const svg = useRef<SVGSVGElement>(null);
  const base = useRef<SVGPathElement>(null);
  const live = useRef<SVGPathElement>(null);

  useEffect(() => {
    const box = wrap.current, s = svg.current, pb = base.current, pl = live.current;
    if (!box || !s || !pb || !pl) return;
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    const nums = Array.from(box.querySelectorAll<HTMLElement>(".num"));
    let len = 0, ticking = false;

    const onScroll = () => {
      const r = box.getBoundingClientRect();
      const p = reduced ? 1 : clamp((innerHeight * 0.82 - r.top) / (r.height + innerHeight * 0.25), 0, 1);
      pl.style.strokeDashoffset = (len * (1 - p)).toFixed(1);
      nums.forEach((n, i) => n.classList.toggle("lit", p >= (i / (nums.length - 1)) * 0.96 + 0.01));
    };
    const build = () => {
      const b = box.getBoundingClientRect();
      const pts = nums.map((n) => { const r = n.getBoundingClientRect(); return [r.left + r.width / 2 - b.left, r.top + r.height / 2 - b.top] as const; });
      if (pts.length < 2) return;
      s.setAttribute("viewBox", `0 0 ${b.width.toFixed(1)} ${b.height.toFixed(1)}`);
      const [sx, sy] = pts[0]!;
      let d = `M${sx.toFixed(1)} ${sy.toFixed(1)}`;
      for (let i = 0; i < pts.length - 1; i++) {
        const p1 = pts[i]!, p2 = pts[i + 1]!, p0 = pts[i - 1] ?? p1, p3 = pts[i + 2] ?? p2;
        const c1: [number, number] = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6 - 34];
        const c2: [number, number] = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6 + 34];
        d += ` C${c1[0].toFixed(1)} ${c1[1].toFixed(1)} ${c2[0].toFixed(1)} ${c2[1].toFixed(1)} ${p2[0].toFixed(1)} ${p2[1].toFixed(1)}`;
      }
      pb.setAttribute("d", d);
      pl.setAttribute("d", d);
      len = pl.getTotalLength();
      pl.style.strokeDasharray = String(len);
      onScroll();
    };
    const scroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => { onScroll(); ticking = false; });
    };
    let rT: ReturnType<typeof setTimeout> | undefined;
    const resize = () => { clearTimeout(rT); rT = setTimeout(build, 150); };
    build();
    document.fonts?.ready.then(build).catch(() => undefined);
    addEventListener("scroll", scroll, { passive: true });
    addEventListener("resize", resize);
    return () => { clearTimeout(rT); removeEventListener("scroll", scroll); removeEventListener("resize", resize); };
  }, []);

  return (
    <div className="steps-wrap" ref={wrap}>
      <svg className="steps-svg" ref={svg} aria-hidden>
        <path className="base" ref={base} />
        <path className="live" ref={live} />
      </svg>
      <ol className="steps">
        {steps.map((st, i) => (
          <li key={i} className="step card">
            <span className="num">{i + 1}</span>
            <h3>{st.title}</h3>
            <p>{st.body}</p>
          </li>
        ))}
      </ol>
    </div>
  );
}
