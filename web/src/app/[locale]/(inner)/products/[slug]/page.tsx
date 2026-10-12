import type { Metadata } from "next";
import { notFound, permanentRedirect } from "next/navigation";
import { isLocale, type Locale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import {
  getProductBySlug,
  getCategories,
  type ProductCard,
  type CategoryResponse,
} from "@/lib/catalog/api";
import { ApiError } from "@/lib/api/problem";
import { ProductDetail } from "@/components/product/ProductDetail";
import { Breadcrumbs, type Crumb } from "@/components/product/Breadcrumbs";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "";

async function loadCard(slug: string, locale: Locale): Promise<ProductCard | null> {
  try {
    return await getProductBySlug(slug, { locale, revalidate: 300 });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string; slug: string }>;
}): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isLocale(locale)) return {};
  const card = await loadCard(slug, locale);
  if (!card) return {};

  const canonicalPath = `/${locale}/products/${card.slug ?? slug}`;
  const description = card.short_description ?? card.description ?? undefined;
  return {
    title: card.name ?? card.sku,
    description,
    alternates: { canonical: canonicalPath },
    openGraph: {
      type: "website",
      title: card.name ?? card.sku ?? "",
      description,
      url: SITE_URL ? `${SITE_URL}${canonicalPath}` : canonicalPath,
    },
  };
}

export default async function ProductPage({
  params,
}: {
  params: Promise<{ locale: string; slug: string }>;
}) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) notFound();

  const [dict, card, categories] = await Promise.all([
    getDictionary(locale),
    loadCard(slug, locale),
    getCategories({ locale, revalidate: 3600 }).catch(() => [] as CategoryResponse[]),
  ]);

  if (!card) notFound();
  // Устаревший slug (бэкенд отдал 301, fetch последовал) → постоянный редирект на актуальный адрес.
  if (card.slug && card.slug !== slug) {
    permanentRedirect(`/${locale}/products/${card.slug}`);
  }

  const trail = buildTrail(categories, card.category_id ?? null, locale, dict.product);
  const jsonLd = productJsonLd(card, trail);

  return (
    <article>
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <Breadcrumbs
        items={[
          ...trail,
          { label: card.name ?? card.sku ?? "" },
        ]}
      />
      <ProductDetail card={card} locale={locale} dict={dict} />
    </article>
  );
}

/** Хлебные крошки: Главная → Каталог → предки категории (сверху вниз). */
function buildTrail(
  categories: CategoryResponse[],
  categoryId: number | null,
  locale: Locale,
  labels: { breadcrumbHome: string; breadcrumbCatalog: string },
): Crumb[] {
  const flat = new Map<number, CategoryResponse>();
  const walk = (list: CategoryResponse[]) => {
    for (const c of list) {
      if (c.id != null) flat.set(c.id, c);
      if (c.children?.length) walk(c.children);
    }
  };
  walk(categories);

  const ancestors: Crumb[] = [];
  let currentId: number | null = categoryId;
  const guard = new Set<number>();
  while (currentId != null && flat.has(currentId) && !guard.has(currentId)) {
    guard.add(currentId);
    const c = flat.get(currentId)!;
    ancestors.unshift({
      label: c.name ?? c.slug ?? String(c.id),
      href: `/${locale}/products?category_id=${c.id}`,
    });
    currentId = c.parent_id ?? null;
  }

  return [
    { label: labels.breadcrumbHome, href: `/${locale}` },
    { label: labels.breadcrumbCatalog, href: `/${locale}/products` },
    ...ancestors,
  ];
}

function productJsonLd(card: ProductCard, trail: Crumb[]) {
  const inStock = card.has_stock ?? false;
  const product = {
    "@context": "https://schema.org",
    "@type": "Product",
    name: card.name ?? card.sku,
    sku: card.sku,
    description: card.short_description ?? card.description ?? undefined,
    offers: {
      "@type": "AggregateOffer",
      priceCurrency: "ILS",
      lowPrice: card.min_price ?? undefined,
      highPrice: card.max_price ?? undefined,
      availability: inStock ? "https://schema.org/InStock" : "https://schema.org/OutOfStock",
    },
  };

  const breadcrumb = {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: [...trail, { label: card.name ?? card.sku ?? "" }].map((c, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: c.label,
      item: c.href && SITE_URL ? `${SITE_URL}${c.href}` : undefined,
    })),
  };

  return [product, breadcrumb];
}
