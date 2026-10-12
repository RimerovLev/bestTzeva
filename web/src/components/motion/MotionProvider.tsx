"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

/** Яркие краски бренда — брызги, капли в герое, подсветка букв. */
export const BRIGHT = ["#e8322b", "#2b59f0", "#ffc933", "#2ed1a0", "#ff7ab6", "#7b61ff"] as const;

interface Particle { x: number; y: number; vx: number; vy: number; r: number; c: string; life: number; decay: number }

interface MotionValue {
  /** Брызги краски из точки экрана (координаты клиента). */
  burst: (x: number, y: number, n: number, colors: readonly string[], power?: number) => void;
  /** Короткое сообщение внизу экрана. */
  say: (message: string) => void;
  reduce: boolean;
}

const MotionContext = createContext<MotionValue | null>(null);

/**
 * Общий слой анимации сайта: холст брызг поверх страницы, курсор-«капля» с difference-смешиванием
 * (только мышь, без prefers-reduced-motion) и тост. Компоненты зовут burst/say через useMotion().
 */
export function MotionProvider({ children }: { children: ReactNode }) {
  const fxRef = useRef<HTMLCanvasElement>(null);
  const curRef = useRef<HTMLDivElement>(null);
  const parts = useRef<Particle[]>([]);
  const running = useRef(false);
  const [reduce, setReduce] = useState(false);
  const [toast, setToast] = useState<{ text: string; id: number; show: boolean }>({ text: "", id: 0, show: false });

  useEffect(() => {
    const mq = matchMedia("(prefers-reduced-motion: reduce)");
    const sync = () => setReduce(mq.matches);
    sync();
    mq.addEventListener("change", sync);
    return () => mq.removeEventListener("change", sync);
  }, []);

  // холст брызг: размер под окно и DPR
  useEffect(() => {
    const fx = fxRef.current;
    if (!fx) return;
    const ctx = fx.getContext("2d");
    const resize = () => {
      const d = Math.min(2, devicePixelRatio || 1);
      fx.width = Math.round(innerWidth * d);
      fx.height = Math.round(innerHeight * d);
      ctx?.setTransform(d, 0, 0, d, 0, 0);
    };
    resize();
    addEventListener("resize", resize);
    return () => removeEventListener("resize", resize);
  }, []);

  // курсор: плавно догоняет указатель, растёт над интерактивным, прячется над холстом валика
  useEffect(() => {
    const cur = curRef.current;
    if (!cur || reduce || !matchMedia("(pointer: fine)").matches) return;
    let cx = -100, cy = -100, tx = -100, ty = -100, raf = 0;
    const onMove = (e: PointerEvent) => {
      tx = e.clientX;
      ty = e.clientY;
      cur.classList.add("on");
      const t = e.target as Element | null;
      cur.classList.toggle("big", !!t?.closest?.("a, button, label, input[type=range], .stripe, .lift, .mega span"));
      cur.classList.toggle("hide", !!t?.closest?.(".vz-canvas"));
    };
    const onLeave = () => cur.classList.remove("on");
    const tick = () => {
      cx += (tx - cx) * 0.25;
      cy += (ty - cy) * 0.25;
      cur.style.transform = `translate3d(${cx}px,${cy}px,0) translate(-50%,-50%)`;
      raf = requestAnimationFrame(tick);
    };
    addEventListener("pointermove", onMove, { passive: true });
    document.documentElement.addEventListener("pointerleave", onLeave);
    raf = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(raf);
      removeEventListener("pointermove", onMove);
      document.documentElement.removeEventListener("pointerleave", onLeave);
      cur.classList.remove("on");
    };
  }, [reduce]);

  const loop = useCallback(() => {
    const fx = fxRef.current;
    const ctx = fx?.getContext("2d");
    if (!ctx) { running.current = false; return; }
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    parts.current = parts.current.filter((p) => p.life > 0 && p.y < innerHeight + 40);
    for (const p of parts.current) {
      p.vy += 0.32; p.vx *= 0.985; p.x += p.vx; p.y += p.vy; p.life -= p.decay;
      const sp = Math.hypot(p.vx, p.vy);
      ctx.save();
      ctx.globalAlpha = Math.max(0, p.life);
      ctx.translate(p.x, p.y);
      ctx.rotate(Math.atan2(p.vy, p.vx));
      ctx.scale(1 + Math.min(2.2, sp * 0.09), 1);
      ctx.beginPath();
      ctx.arc(0, 0, p.r, 0, Math.PI * 2);
      ctx.fillStyle = p.c;
      ctx.fill();
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = "rgba(18,18,18,.85)";
      ctx.stroke();
      ctx.restore();
    }
    if (parts.current.length) requestAnimationFrame(loop);
    else { running.current = false; ctx.clearRect(0, 0, innerWidth, innerHeight); }
  }, []);

  const burst = useCallback<MotionValue["burst"]>((x, y, n, colors, power = 1) => {
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2, s = (2 + Math.random() * 8) * power;
      parts.current.push({
        x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s - 4 * power, r: 3 + Math.random() * 7,
        c: colors[i % colors.length] ?? BRIGHT[0], life: 1, decay: 0.011 + Math.random() * 0.012,
      });
    }
    if (!running.current) { running.current = true; requestAnimationFrame(loop); }
  }, [loop]);

  const say = useCallback((text: string) => setToast({ text, id: Date.now(), show: true }), []);

  useEffect(() => {
    if (!toast.show) return;
    // текст остаётся до конца анимации скрытия — гасим только флаг
    const t = setTimeout(() => setToast((prev) => ({ ...prev, show: false })), 2700);
    return () => clearTimeout(t);
  }, [toast.id, toast.show]);

  const value = useMemo<MotionValue>(() => ({ burst, say, reduce }), [burst, say, reduce]);

  return (
    <MotionContext.Provider value={value}>
      {children}
      <canvas id="tb-fx" ref={fxRef} aria-hidden />
      <div id="tb-cursor" ref={curRef} aria-hidden />
      <div className={`toast${toast.show ? " show" : ""}`} role="status" aria-live="polite">
        {toast.text}
      </div>
    </MotionContext.Provider>
  );
}

export function useMotion(): MotionValue {
  const ctx = useContext(MotionContext);
  if (!ctx) throw new Error("useMotion must be used within MotionProvider");
  return ctx;
}
