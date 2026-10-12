"use client";

import Link from "next/link";
import { useEffect, useRef, type CSSProperties, type MouseEvent } from "react";
import { createFluid, type Fluid } from "@/lib/motion/fluid";
import { BRIGHT, useMotion } from "./MotionProvider";

export interface FluidHeroText {
  label: string;
  kicker: string;
  titleLines: string[];
  body: string;
  ctaTry: string;
  ctaCatalog: string;
  canLabel: string;
  ring: string;
  sticker1: string;
  sticker2: string;
  hint: string;
}

/**
 * Герой: живая краска на весь экран. Курсор/палец тянут краску, клик — клякса, банка выплёскивает
 * все цвета, без движения краска сама капает раз в пару секунд. Первый кадр считается сразу —
 * фоновая вкладка не крутит rAF, и без этого герой был бы чёрным. Без WebGL — градиентная подложка.
 */
export function FluidHero({ text }: { text: FluidHeroText }) {
  const { burst, reduce } = useMotion();
  const heroRef = useRef<HTMLElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const hintRef = useRef<HTMLSpanElement>(null);
  const fluidRef = useRef<Fluid | null>(null);

  useEffect(() => {
    const hero = heroRef.current, canvas = canvasRef.current, hint = hintRef.current;
    if (!hero || !canvas) return;
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    let fluid: Fluid | null = null;
    try { fluid = createFluid(canvas, BRIGHT); } catch { fluid = null; }
    if (!fluid) return;
    fluidRef.current = fluid;
    hero.classList.add("fluid");
    const F = fluid;

    const pt = { x: 0, y: 0, px: 0, py: 0, moved: false, has: false };
    let color = F.pigment(F.nextColor(), 1.1), colorAt = 0;
    let running = true, last = performance.now(), lastInput = 0, nextAuto = 0, interacted = false, raf = 0;

    F.randomSplats(7);
    for (let i = 0, n = reduced ? 70 : 26; i < n; i++) F.step(0.016);
    F.render();

    const io = new IntersectionObserver(([en]) => { running = !!en?.isIntersecting; });
    io.observe(hero);

    const toUv = (e: PointerEvent): [number, number] => {
      const r = canvas.getBoundingClientRect();
      return [(e.clientX - r.left) / r.width, 1 - (e.clientY - r.top) / r.height];
    };
    const onMove = (e: PointerEvent) => {
      const [x, y] = toUv(e), now = performance.now();
      if (!pt.has || now - lastInput > 400) { pt.px = x; pt.py = y; pt.has = true; color = F.pigment(F.nextColor(), 1.1); colorAt = now; }
      pt.x = x; pt.y = y; pt.moved = true; lastInput = now;
      if (!interacted) { interacted = true; hint?.classList.add("hide"); }
    };
    const onDown = (e: PointerEvent) => {
      if ((e.target as Element).closest("a, button")) return;
      const [x, y] = toUv(e);
      for (let i = 0; i < 3; i++) {
        const a = Math.random() * Math.PI * 2;
        F.splat(x, y, Math.cos(a) * 2200, Math.sin(a) * 2200, F.pigment(F.nextColor(), 1.2));
      }
      if (reduced) { F.step(0.016); F.render(); }
    };
    hero.addEventListener("pointermove", onMove, { passive: true });
    hero.addEventListener("pointerdown", onDown);

    const resize = () => F.resize();
    addEventListener("resize", resize);

    if (!reduced) {
      const frame = (now: number) => {
        raf = requestAnimationFrame(frame);
        if (!running || document.hidden) { last = now; return; }
        const dt = Math.min((now - last) / 1000, 0.0167);
        last = now;
        if (pt.moved) {
          let dx = pt.x - pt.px, dy = pt.y - pt.py;
          const a = canvas.width / canvas.height;
          if (a < 1) dx *= a; else dy /= a;
          if (Math.abs(dx) < 0.25 && Math.abs(dy) < 0.25) F.splat(pt.x, pt.y, dx * F.force, dy * F.force, color);
          if (now - colorAt > 1600) { color = F.pigment(F.nextColor(), 1.1); colorAt = now; }
          pt.px = pt.x; pt.py = pt.y; pt.moved = false;
        }
        if (now - lastInput > 3200 && now > nextAuto) {
          F.randomSplats(1 + (Math.random() < 0.4 ? 1 : 0));
          nextAuto = now + 1800 + Math.random() * 1600;
        }
        F.step(dt);
        F.render();
      };
      raf = requestAnimationFrame(frame);
    }

    return () => {
      cancelAnimationFrame(raf);
      io.disconnect();
      hero.removeEventListener("pointermove", onMove);
      hero.removeEventListener("pointerdown", onDown);
      removeEventListener("resize", resize);
      hero.classList.remove("fluid");
      fluidRef.current = null;
      F.dispose();
    };
  }, []);

  function spill(e: MouseEvent<HTMLButtonElement>) {
    const cr = e.currentTarget.getBoundingClientRect();
    const F = fluidRef.current, canvas = canvasRef.current;
    if (F && canvas) {
      const fr = canvas.getBoundingClientRect();
      const x = (cr.left + cr.width / 2 - fr.left) / fr.width, y = 1 - (cr.top + cr.height * 0.25 - fr.top) / fr.height;
      for (let i = 0; i < 9; i++) {
        const a = Math.PI * 0.6 + (i / 8) * Math.PI * 1.1;
        F.splat(x, y, Math.cos(a) * 3200, Math.sin(a) * 3200, F.pigment(BRIGHT[i % BRIGHT.length]!, 1.25));
      }
      if (reduce) { F.step(0.016); F.render(); }
    }
    burst(cr.left + cr.width / 2, cr.top + cr.height * 0.3, 40, BRIGHT, 1.2);
  }

  return (
    <section className="hero" ref={heroRef} aria-label={text.label}>
      <canvas className="hero-fluid" ref={canvasRef} aria-hidden />
      <div className="hero-fallback" aria-hidden />
      {/* краска внизу растворяется в синем блоке лент — без резкой линии на стыке */}
      <div className="hero-fade" aria-hidden />
      <div className="wrap hero-in">
        <div>
          <span className="kicker ld" style={{ "--d": ".05s" } as CSSProperties}><i />{text.kicker}</span>
          <h1 className="hero-title">
            {text.titleLines.map((line, i) => (
              <span key={i} className="ln">
                <span style={{ "--d": `${0.12 + i * 0.14}s` } as CSSProperties}>{line}</span>
              </span>
            ))}
          </h1>
        </div>
        <div className="hero-card card ld" style={{ "--d": ".55s" } as CSSProperties}>
          <p>{text.body}</p>
          <div className="row">
            <Link className="btn btn-red" href="#colors">
              {text.ctaTry}
              <svg className="icon-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </Link>
            <Link className="btn" href="#catalog">{text.ctaCatalog}</Link>
          </div>
        </div>
      </div>
      <button className="hero-can" type="button" aria-label={text.canLabel} onClick={spill} />
      <div className="badge-ring" aria-hidden>
        <svg viewBox="0 0 120 120">
          <defs><path id="ringp" d="M60 60m-46 0a46 46 0 1 1 92 0a46 46 0 1 1-92 0" /></defs>
          <circle cx="60" cy="60" r="58" fill="#ffc933" stroke="#121212" strokeWidth="3" />
          {/* textLength = длина окружности r=46: фраза ровно замыкает кольцо на любом языке */}
          <text><textPath href="#ringp" textLength="287" lengthAdjust="spacing">{text.ring}</textPath></text>
          <circle cx="60" cy="60" r="16" fill="#e8322b" stroke="#121212" strokeWidth="3" />
        </svg>
      </div>
      <span className="sticker st-1" aria-hidden>{text.sticker1}</span>
      <span className="sticker st-2" aria-hidden>{text.sticker2}</span>
      <span className="hero-hint" ref={hintRef} aria-hidden><i />{text.hint}</span>
    </section>
  );
}
