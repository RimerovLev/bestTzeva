import type { MetadataRoute } from "next";
import { locales, defaultLocale } from "@/lib/i18n/config";
import { abs } from "@/lib/seo";
import { listProductSlugs } from "@/lib/catalog/api";
import { listPortfolioSlugs } from "@/lib/portfolio/api";

/** Пересборка раз в час: новые товары и проекты попадают в карту без деплоя. */
export const revalidate = 3600;

type Entry = MetadataRoute.Sitemap[number];

/**
 * Карта сайта: статические разделы, карточки товаров (slug из B-02) и проекты портфолио — каждая
 * страница в обеих локалях с перекрёстными hreflang. Заявка и замер не попадают (noindex).
 * Категории своих адресов не имеют (это фильтр ?category_id каталога, canonical — /products),
 * поэтому отдельно не перечисляются.
 * Если API недоступен на сборке, карта остаётся со статическими разделами, а не падает.
 */
export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const [products, projects] = await Promise.all([
    listProductSlugs({ locale: defaultLocale, revalidate: 300 }).catch(() => [] as string[]),
    listPortfolioSlugs({ locale: defaultLocale, revalidate: 3600 }).catch(() => [] as string[]),
  ]);

  const pages: { path: string; changeFrequency: Entry["changeFrequency"]; priority: number }[] = [
    { path: "", changeFrequency: "weekly", priority: 1 },
    { path: "/products", changeFrequency: "daily", priority: 0.9 },
    { path: "/services", changeFrequency: "monthly", priority: 0.8 },
    { path: "/portfolio", changeFrequency: "weekly", priority: 0.7 },
    { path: "/colors", changeFrequency: "monthly", priority: 0.6 },
    ...products.map((slug) => ({ path: `/products/${encodeURIComponent(slug)}`, changeFrequency: "weekly" as const, priority: 0.8 })),
    ...projects.map((slug) => ({ path: `/portfolio/${encodeURIComponent(slug)}`, changeFrequency: "monthly" as const, priority: 0.6 })),
  ];

  return pages.flatMap(({ path, changeFrequency, priority }) => {
    const languages = Object.fromEntries(locales.map((l) => [l, abs(`/${l}${path}`)]));
    return locales.map((locale) => ({
      url: abs(`/${locale}${path}`),
      changeFrequency,
      priority,
      alternates: { languages },
    }));
  });
}
