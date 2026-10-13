"use client";

import { useEffect } from "react";

/**
 * Капли под секциями дотягиваются, маркер под словами прокрашивается — когда блок доходит до экрана.
 * Один наблюдатель на страницу вместо обёртки вокруг каждой секции. Тяжёлые фоновые картинки
 * (.lazy-bg) включаются заранее, за ~600px до экрана: иначе они грузились бы вместе с первым
 * экраном и отнимали сеть у главного контента (LCP, F-11.1).
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
    const bg = new IntersectionObserver(
      (entries) => entries.forEach((en) => {
        if (!en.isIntersecting) return;
        en.target.classList.add("bg-on");
        bg.unobserve(en.target);
      }),
      { rootMargin: "600px 0px" },
    );
    document.querySelectorAll("main .lazy-bg").forEach((el) => bg.observe(el));
    return () => { io.disconnect(); bg.disconnect(); };
  }, []);
  return null;
}
