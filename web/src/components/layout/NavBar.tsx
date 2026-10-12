"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { locales, type Locale } from "@/lib/i18n/config";
import { useRequest } from "@/lib/request/RequestProvider";

export interface NavItem { anchor: string; href: string; label: string }

interface Labels { home: string; sections: string; menu: string; request: string; contact: string; languageMenu: string }

/** Подпись переключателя: код языка, а не название — короче и одинаково в обоих направлениях. */
const LANG_CODE: Record<Locale, string> = { he: "עב", ru: "RU" };

export function NavBar({ locale, items, labels }: { locale: Locale; items: NavItem[]; labels: Labels }) {
  const pathname = usePathname();
  const isHome = pathname === `/${locale}`;
  const { count } = useRequest();
  const navRef = useRef<HTMLElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState<string | null>(null);
  const [bump, setBump] = useState(0);
  const prevCount = useRef(count);

  // фон и прогресс прокрутки
  useEffect(() => {
    const nav = navRef.current;
    if (!nav) return;
    let ticking = false;
    const onScroll = () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const y = scrollY, max = document.documentElement.scrollHeight - innerHeight;
        nav.classList.toggle("scrolled", y > 30);
        nav.style.setProperty("--p", (max > 0 ? Math.min(1, y / max) : 0).toFixed(4));
        ticking = false;
      });
    };
    onScroll();
    addEventListener("scroll", onScroll, { passive: true });
    return () => removeEventListener("scroll", onScroll);
  }, [pathname]);

  // подсветка текущего раздела — только на главной
  useEffect(() => {
    setActive(null);
    if (!isHome) return;
    const spy = new IntersectionObserver(
      (entries) => entries.forEach((en) => { if (en.isIntersecting) setActive(en.target.id || null); }),
      { rootMargin: "-45% 0px -50% 0px" },
    );
    document.querySelectorAll("main section[id]").forEach((s) => spy.observe(s));
    return () => spy.disconnect();
  }, [isHome]);

  // счётчик заявки подпрыгивает, когда позиций стало больше
  useEffect(() => {
    if (count > prevCount.current) setBump((b) => b + 1);
    prevCount.current = count;
  }, [count]);

  useEffect(() => setOpen(false), [pathname]);

  function pathForLocale(target: Locale): string {
    const segments = pathname.split("/");
    segments[1] = target;
    return segments.join("/") || `/${target}`;
  }

  const langSwitch = (
    <div className="lang" role="group" aria-label={labels.languageMenu}>
      {locales.map((target) =>
        target === locale ? (
          <span key={target} aria-current="true" lang={target}>{LANG_CODE[target]}</span>
        ) : (
          <Link key={target} href={pathForLocale(target)} hrefLang={target} lang={target}>{LANG_CODE[target]}</Link>
        ),
      )}
    </div>
  );

  return (
    <header ref={navRef} className={`nav${isHome ? "" : " solid"}`}>
      <div className="wrap nav-inner">
        <Link className="logo" href={`/${locale}`} aria-label={labels.home} />
        <nav className={`links${open ? " open" : ""}`} id="nav-links" aria-label={labels.sections}>
          {items.map((item) => (
            <Link
              key={item.anchor}
              href={item.href}
              className={active === item.anchor ? "active" : undefined}
              onClick={() => setOpen(false)}
            >
              {item.label}
            </Link>
          ))}
          <div className="links-extra">
            {langSwitch}
            <Link className="btn btn-red btn-sm" href={`/${locale}#contact`} onClick={() => setOpen(false)}>{labels.contact}</Link>
          </div>
        </nav>
        <div className="nav-cta">
          <Link className="cart" href={`/${locale}/request`} aria-label={labels.request}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden>
              <path d="M9 4h6a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" />
              <path d="M10 9h4M10 13h4" strokeLinecap="round" />
            </svg>
            <span className="cart-label">{labels.request}</span>
            {count > 0 && (
              <span key={bump} className={`badge${bump ? " bump" : ""}`}>
                <bdi>{count}</bdi>
              </span>
            )}
          </Link>
          {langSwitch}
          <Link className="btn btn-red btn-sm" href={`/${locale}#contact`}>{labels.contact}</Link>
          <button
            className="menu-btn"
            type="button"
            aria-expanded={open}
            aria-controls="nav-links"
            aria-label={labels.menu}
            onClick={() => setOpen((v) => !v)}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden>
              <path d="M4 7h16M4 12h16M4 17h10" />
            </svg>
          </button>
        </div>
        <div className="progress" aria-hidden />
      </div>
    </header>
  );
}
