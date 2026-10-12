import type { Metadata } from "next";
import { locales, defaultLocale, type Locale } from "@/lib/i18n/config";

/**
 * Публичный адрес витрины — для canonical, hreflang, sitemap и JSON-LD. На проде задаётся
 * NEXT_PUBLIC_SITE_URL; без него ссылки строятся от localhost (разработка).
 */
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000").replace(/\/+$/, "");

export const abs = (path: string) => `${SITE_URL}${path}`;

/** Локаль Open Graph. */
export const OG_LOCALE: Record<Locale, string> = { he: "he_IL", ru: "ru_RU" };

/**
 * canonical + hreflang для страницы, одинаковой по пути во всех локалях. `path` — без префикса
 * локали и без query («» — главная, «/products»). x-default — иврит: основной язык сайта.
 * Query (фильтры каталога) в canonical не попадает намеренно — выборки не индексируются отдельно.
 */
export function alternatesFor(locale: Locale, path: string): NonNullable<Metadata["alternates"]> {
  const languages: Record<string, string> = {};
  for (const l of locales) languages[l] = `/${l}${path}`;
  languages["x-default"] = `/${defaultLocale}${path}`;
  return { canonical: `/${locale}${path}`, languages };
}

export interface Crumb {
  label: string;
  href?: string;
}

/** BreadcrumbList для JSON-LD. Последний элемент (текущая страница) — без ссылки. */
export function breadcrumbLd(items: Crumb[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((c, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: c.label,
      ...(c.href ? { item: abs(c.href) } : {}),
    })),
  };
}

/** Картинка по умолчанию для превью в мессенджерах и соцсетях. */
export const DEFAULT_OG_IMAGE = "/assets/motion/flat.webp";

/**
 * Метаданные индексируемой страницы: title, description, canonical + hreflang и Open Graph с
 * локалью. Next не сливает openGraph родителя и страницы, а заменяет — поэтому собираем целиком.
 */
export function pageMeta({
  locale,
  path,
  siteName,
  title,
  description,
  image,
  type = "website",
}: {
  locale: Locale;
  path: string;
  siteName: string;
  title?: string;
  description?: string | null;
  image?: string | null;
  type?: "website" | "article";
}): Metadata {
  const others = locales.filter((l) => l !== locale).map((l) => OG_LOCALE[l]);
  return {
    ...(title ? { title } : {}),
    ...(description ? { description } : {}),
    alternates: alternatesFor(locale, path),
    openGraph: {
      type,
      siteName,
      locale: OG_LOCALE[locale],
      alternateLocale: others,
      url: `/${locale}${path}`,
      ...(title ? { title } : {}),
      ...(description ? { description } : {}),
      images: [image || DEFAULT_OG_IMAGE],
    },
  };
}
