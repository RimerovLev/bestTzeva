"use client";

import Image from "next/image";
import { useEffect, useRef } from "react";
import { BRIGHT, useMotion } from "./MotionProvider";

const DURATION = 1700;

/**
 * Логотип в подвале «прокатывается» валиком: когда при прокрутке вниз табличка заметно показалась,
 * валик едет слева направо и за ним проявляется надпись. Останавливается у правого края — там, где
 * нарисован валик самого логотипа, — и растворяется в нём. Ушли из подвала целиком и вернулись —
 * прокатывается снова.
 *
 * Направление физическое (картинка, а не текст), поэтому одинаково в иврите и русском. На сервере и
 * при prefers-reduced-motion логотип виден целиком: прячем его только из JS прямо перед анимацией.
 */
export function LogoRoller() {
  const { burst } = useMotion();
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = box.current;
    if (!el || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let raf = 0;
    let state: "idle" | "playing" | "done" = "idle";

    const set = (p: number) => el.style.setProperty("--rp", `${(p * 100).toFixed(2)}%`);
    const hide = () => {
      cancelAnimationFrame(raf);
      state = "idle";
      el.classList.remove("rolling", "rolled");
      set(0);
    };
    const play = () => {
      state = "playing";
      el.classList.add("rolling");
      el.classList.remove("rolled");
      const t0 = performance.now();
      const step = (now: number) => {
        const k = Math.min(1, (now - t0) / DURATION);
        set(k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2);
        if (k < 1) {
          raf = requestAnimationFrame(step);
          return;
        }
        state = "done";
        el.classList.remove("rolling");
        el.classList.add("rolled");
        const r = el.getBoundingClientRect();
        burst(r.right - r.width * 0.06, r.top + r.height * 0.35, 22, BRIGHT, 0.7);
      };
      raf = requestAnimationFrame(step);
    };

    hide();
    const io = new IntersectionObserver(
      ([en]) => {
        if (!en) return;
        if (en.intersectionRatio >= 0.45 && state === "idle") play();
        else if (!en.isIntersecting && state !== "idle") hide();
      },
      { threshold: [0, 0.45] },
    );
    io.observe(el);
    return () => {
      io.disconnect();
      cancelAnimationFrame(raf);
      el.classList.remove("rolling", "rolled");
      el.style.removeProperty("--rp");
    };
  }, [burst]);

  return (
    <div className="ml-art" ref={box} dir="ltr">
      <div className="ml-paint">
        <Image src="/assets/motion/logo.webp" alt="" width={649} height={153} sizes="(max-width: 1100px) 90vw, 1000px" />
      </div>
      <span className="ml-roller" aria-hidden>
        <svg className="ml-handle" viewBox="0 0 60 80" aria-hidden>
          <path d="M30 78V58H52V20" fill="none" stroke="#121212" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
          <rect x="44" y="0" width="16" height="26" rx="6" fill="#2b59f0" stroke="#121212" strokeWidth="3" />
        </svg>
        <span className="ml-cyl" />
      </span>
    </div>
  );
}
