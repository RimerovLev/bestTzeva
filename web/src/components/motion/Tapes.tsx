"use client";

import { useEffect, useRef } from "react";

/**
 * Две перекрещенные ленты под героем. Едут навстречу друг другу и разгоняются от скорости прокрутки.
 * Слова продублированы дважды — сдвиг сбрасывается на половину ширины без шва. Направление
 * движения не зависит от языка, поэтому контейнер в LTR.
 */
export function Tapes({ a, b }: { a: string[]; b: string[] }) {
  const root = useRef<HTMLDivElement>(null);
  const trkA = useRef<HTMLDivElement>(null);
  const trkB = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = root.current, ta = trkA.current, tb = trkB.current;
    if (!el || !ta || !tb || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let ax = 0, bx = -tb.scrollWidth / 2, vel = 0, lastY = scrollY, visible = true, raf = 0;
    const io = new IntersectionObserver(([en]) => { visible = !!en?.isIntersecting; });
    io.observe(el);
    const tick = () => {
      raf = requestAnimationFrame(tick);
      const dy = scrollY - lastY;
      lastY = scrollY;
      vel += (dy - vel) * 0.12;
      if (!visible) return;
      const sp = 0.7 + Math.min(16, Math.abs(vel) * 0.45);
      const ha = ta.scrollWidth / 2, hb = tb.scrollWidth / 2;
      ax -= sp; if (ax <= -ha) ax += ha;
      bx += sp; if (bx >= 0) bx -= hb;
      ta.style.transform = `translate3d(${ax.toFixed(1)}px,0,0)`;
      tb.style.transform = `translate3d(${bx.toFixed(1)}px,0,0)`;
    };
    raf = requestAnimationFrame(tick);
    return () => { cancelAnimationFrame(raf); io.disconnect(); };
  }, []);

  return (
    <div className="tapes" ref={root} dir="ltr" aria-hidden>
      <div className="tape tape-a">
        <div className="trk" ref={trkA}>{[...a, ...a].map((w, i) => <span key={i}>{w}</span>)}</div>
      </div>
      <div className="tape tape-b">
        <div className="trk" ref={trkB}>{[...b, ...b].map((w, i) => <span key={i}>{w}</span>)}</div>
      </div>
    </div>
  );
}
