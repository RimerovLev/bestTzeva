"use client";

import { useEffect } from "react";

/**
 * Капли под секциями дотягиваются, маркер под словами прокрашивается — когда блок доходит до экрана.
 * Один наблюдатель на страницу вместо обёртки вокруг каждой секции.
 */
export function RevealEffects() {
  useEffect(() => {
    const io = new IntersectionObserver(
      (entries) => entries.forEach((en) => {
        if (!en.isIntersecting) return;
        en.target.classList.add("in");
        io.unobserve(en.target);
      }),
      { threshold: 0.25 },
    );
    document.querySelectorAll("main .drip, main .hl").forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);
  return null;
}
