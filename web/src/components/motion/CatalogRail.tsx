"use client";

import Link from "next/link";
import { useEffect, useRef, type CSSProperties, type ReactNode } from "react";
import { clamp } from "@/lib/motion/sweep";

export interface RailProduct {
  key: string;
  href: string;
  name: string;
  type: string | null;
  price: string | null;
  priceFrom: boolean;
  color: string;
}

interface RailText {
  bannerAlt: string;
  bannerTitle: string;
  bannerBody: string;
  from: string;
  details: string;
  allTitle: string;
  allBody: string;
  allCta: string;
}

/**
 * Каталог лентой: на десктопе секция «прилипает», а вертикальная прокрутка двигает ленту вбок;
 * на тач-устройствах, низких экранах и при reduced-motion — обычный горизонтальный скролл со
 * snap. В RTL лента начинается справа и едет вправо — знак сдвига зависит от направления письма.
 */
export function CatalogRail({ products, allHref, text, children }: {
  products: RailProduct[];
  allHref: string;
  text: RailText;
  children: ReactNode;
}) {
  const sec = useRef<HTMLElement>(null);
  const view = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const bar = useRef<HTMLElement>(null);

  useEffect(() => {
    const hs = sec.current, v = view.current, t = track.current, b = bar.current;
    if (!hs || !v || !t || !b) return;
    const rtl = getComputedStyle(hs).direction === "rtl";
    let pinOn = false, dist = 0, ticking = false;

    const setup = () => {
      const ok = innerWidth >= 900 && innerHeight >= 640 && matchMedia("(pointer: fine)").matches
        && !matchMedia("(prefers-reduced-motion: reduce)").matches;
      hs.classList.toggle("pinned", ok);
      t.style.transform = "";
      if (!ok) { hs.style.height = ""; pinOn = false; return; }
      dist = Math.max(0, t.scrollWidth - innerWidth);
      hs.style.height = `${innerHeight + dist}px`;
      pinOn = dist > 0;
    };
    const update = () => {
      let p: number;
      if (pinOn) {
        const r = hs.getBoundingClientRect();
        p = clamp(-r.top / Math.max(1, hs.offsetHeight - innerHeight), 0, 1);
        t.style.transform = `translate3d(${((rtl ? 1 : -1) * p * dist).toFixed(1)}px,0,0)`;
      } else {
        const max = v.scrollWidth - v.clientWidth;
        p = max > 0 ? Math.abs(v.scrollLeft) / max : 0;
      }
      b.style.setProperty("--hp", p.toFixed(4));
    };
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => { update(); ticking = false; });
    };
    let rT: ReturnType<typeof setTimeout> | undefined;
    const onResize = () => { clearTimeout(rT); rT = setTimeout(() => { setup(); update(); }, 150); };

    setup();
    update();
    document.fonts?.ready.then(() => { setup(); update(); }).catch(() => undefined);
    addEventListener("scroll", onScroll, { passive: true });
    addEventListener("resize", onResize);
    v.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      clearTimeout(rT);
      removeEventListener("scroll", onScroll);
      removeEventListener("resize", onResize);
      v.removeEventListener("scroll", onScroll);
      hs.classList.remove("pinned");
      hs.style.height = "";
    };
  }, [products.length]);

  return (
    <section className="sec s-yellow hs drip" id="catalog" ref={sec} style={{ "--dx": "-260px" } as CSSProperties}>
      <div className="hs-sticky">
        <div className="wrap">{children}</div>
        <div className="hs-view" ref={view}>
          <div className="hs-track" ref={track}>
            <div className="banner">
              <div className="banner-img lazy-bg" role="img" aria-label={text.bannerAlt} />
              <div className="banner-cap"><b>{text.bannerTitle}</b><span>{text.bannerBody}</span></div>
            </div>
            {products.map((p) => (
              <Link key={p.key} href={p.href} className="prod card lift">
                <div className="prod-vis" style={{ "--c": p.color } as CSSProperties}><div className="prod-can" /></div>
                {p.type && <span className="prod-type">{p.type}</span>}
                <h3>{p.name}</h3>
                <div className="prod-foot">
                  {p.price ? (
                    <span className="price">
                      {p.priceFrom && <small>{text.from} </small>}
                      <bdi>{p.price}</bdi>
                    </span>
                  ) : <span />}
                  <span className="add">{text.details}</span>
                </div>
              </Link>
            ))}
            <Link href={allHref} className="prod prod-all card lift">
              <h3>{text.allTitle}</h3>
              <p>{text.allBody}</p>
              <span className="btn btn-yellow btn-sm">{text.allCta}</span>
            </Link>
          </div>
        </div>
        <div className="wrap">
          <div className="hs-bar" aria-hidden><i ref={bar} /></div>
        </div>
      </div>
    </section>
  );
}
