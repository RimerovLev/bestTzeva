"use client";

import Image from "next/image";
import { useEffect, useRef } from "react";

const DURATION = 1700;

/**
 * Логотип в подвале «прокатывается» своим же валиком: логотип разрезан на два слоя (тело и валик с
 * ручкой, одинаковой высоты). Когда при прокрутке вниз табличка заметно показалась, валик едет от
 * левого края надписи вправо, за ним проявляется краска, и останавливается ровно там, где нарисован
 * на логотипе, — после этого картинка неотличима от исходной. Ушли из подвала и вернулись —
 * прокатывается снова.
 *
 * Направление физическое (картинка, а не текст), поэтому одинаково в иврите и русском. На сервере и
 * при prefers-reduced-motion логотип виден целиком: прячем его только из JS прямо перед анимацией.
 */
export function LogoRoller() {
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = box.current;
    if (!el || matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    let raf = 0;
    let state: "idle" | "playing" | "done" = "idle";

    const set = (p: number) => el.style.setProperty("--rp", p.toFixed(4));
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
  }, []);

  return (
    <div className="ml-art" ref={box} dir="ltr">
      <div className="ml-paint">
        <Image src="/assets/motion/logo-body.webp" alt="" width={649} height={153} sizes="(max-width: 1100px) 90vw, 1000px" />
      </div>
      {/* валик — вырезан из того же логотипа и в конце встаёт ровно на своё нарисованное место */}
      <Image className="ml-roller" src="/assets/motion/logo-roller.webp" alt="" width={128} height={153} aria-hidden />
    </div>
  );
}
