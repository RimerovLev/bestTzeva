"use client";

import { useEffect, useRef, useState, type CSSProperties } from "react";

const TIKTOK_URL = "https://www.tiktok.com/@bestzeva";
const INSTAGRAM_URL = "https://www.instagram.com/bestzeva_ltd";

/**
 * Дефолтные кадры (фото работ) — показываются, если владелец не загрузил свои через админку
 * (слоты social_1..social_6 в «Картинки сайта»). Счётчики просмотров — декоративные.
 */
const DEFAULT_PHOTOS = [
  "/assets/motion/demo-white.webp",
  "/assets/section-works.jpg",
  "/assets/section-prices.jpg",
  "/assets/motion/works.webp",
  "/assets/hero-marble.jpg",
  "/assets/motion/flat.webp",
];
const VIEWS = ["9.8K", "2565", "428", "187", "3.4K", "960"];

const Play = () => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="#fff" aria-hidden>
    <path d="M8 5v14l11-7z" />
  </svg>
);

const TikTokMark = () => (
  <svg width="54" height="54" viewBox="0 0 24 24" fill="#fff" aria-hidden>
    <path d="M16.5 3c.3 2 1.5 3.4 3.5 3.7v2.5c-1.3.1-2.5-.3-3.6-1v5.9c0 3.1-2.5 5.4-5.4 5.4S5.6 17.2 5.6 14.1c0-3 2.4-5.2 5.4-5.1v2.6c-.4-.1-.8-.2-1.2-.1-1.4.1-2.5 1.3-2.4 2.8.1 1.4 1.3 2.5 2.7 2.4 1.4 0 2.5-1.2 2.5-2.7V3h3.9z" />
  </svg>
);

const InstaMark = () => (
  <svg width="54" height="54" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="1.9" aria-hidden>
    <rect x="3" y="3" width="18" height="18" rx="5.2" />
    <circle cx="12" cy="12" r="4" />
    <circle cx="17.3" cy="6.7" r="1.2" fill="#fff" stroke="none" />
  </svg>
);

function Feed({ tiles, insta }: { tiles: { img: string; v: string }[]; insta?: boolean }) {
  // дублируем список, чтобы прокрутка зацикливалась без скачка
  const loop = [...tiles, ...tiles];
  return (
    <div className="sp-feed">
      {loop.map((t, i) => (
        <div key={i} className="sp-tile" style={{ backgroundImage: `url("${t.img}")` }}>
          <span className={`sp-badge${insta ? " insta" : ""}`}>
            <Play />
          </span>
          <span className="sp-views">▶ {t.v}</span>
        </div>
      ))}
    </div>
  );
}

function Phone({
  href,
  variant,
  reveal,
  label,
  tiles,
}: {
  href: string;
  variant: "tiktok" | "insta";
  reveal: boolean;
  label: string;
  tiles: { img: string; v: string }[];
}) {
  const isInsta = variant === "insta";
  const splashStyle: CSSProperties = isInsta
    ? { background: "linear-gradient(135deg,#7048e8,#d6336c 55%,#f76707)" }
    : { background: "#0b0b0f" };
  return (
    <a
      className="sp-phone"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={label}
      style={{ transform: isInsta ? "rotate(3deg)" : "rotate(-3deg)" }}
    >
      <span className="sp-notch" />
      <div className="sp-screen">
        <Feed tiles={tiles} insta={isInsta} />
        <div className="sp-splash" style={{ ...splashStyle, opacity: reveal ? 0 : 1 }}>
          {isInsta ? <InstaMark /> : <TikTokMark />}
          <span className="sp-splash-label">{label}</span>
        </div>
      </div>
      <span className={`sp-caption sp-caption--${variant}`}>
        <span className="sp-cap-ico" aria-hidden>{isInsta ? <InstaMark /> : <TikTokMark />}</span>
        {label}
        <span className="sp-cap-arrow" aria-hidden>↗</span>
      </span>
    </a>
  );
}

/**
 * Два телефона (TikTok + Instagram): 1.5с показывают заставку с логотипом, затем она гаснет и внутри
 * бесконечно скроллится лента «видео». Клик по телефону — переход на профиль. Запускаем прокрутку/таймер
 * только когда блок виден (IntersectionObserver) — чтобы заставка «срабатывала» при долистывании.
 */
export function SocialPhones({
  photos,
  labelTiktok,
  labelInstagram,
}: {
  photos?: (string | null)[];
  labelTiktok: string;
  labelInstagram: string;
}) {
  const list = DEFAULT_PHOTOS.map((d, i) => (photos && photos[i]) || d);
  const ttTiles = list.map((img, i) => ({ img, v: VIEWS[i % VIEWS.length] ?? "" }));
  const igTiles = [...ttTiles].reverse();

  const ref = useRef<HTMLDivElement>(null);
  const [reveal, setReveal] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          timer = setTimeout(() => setReveal(true), 1500);
          io.disconnect();
        }
      },
      { threshold: 0.4 },
    );
    io.observe(el);
    return () => {
      io.disconnect();
      if (timer) clearTimeout(timer);
    };
  }, []);

  return (
    <div className="sp-wrap reveal" ref={ref}>
      <Phone href={TIKTOK_URL} variant="tiktok" reveal={reveal} label={labelTiktok} tiles={ttTiles} />
      <Phone href={INSTAGRAM_URL} variant="insta" reveal={reveal} label={labelInstagram} tiles={igTiles} />
    </div>
  );
}
