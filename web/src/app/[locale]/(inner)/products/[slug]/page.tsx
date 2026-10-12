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
import { JsonLd } from "@/components/seo/JsonLd";
import { breadcrumbLd, pageMeta } from "@/lib/seo";
import { PageBand, type BandTone } from "@/components/motion/PageBand";

/** Цвет плашки товара — по id, чтобы соседние товары отличались, а у одного товара не менялся. */
const BAND_TONES: BandTone[] = ["blue", "violet", "mint", "yellow"];


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

  const dict = await getDictionary(locale);
  const image = card.media?.find((m) => m.type === "IMAGE" && m.url)?.url;
  return pageMeta({
    locale,
    path: `/products/${card.slug ?? slug}`,
    siteName: dict.meta.siteName,
    title: card.name ?? card.sku,
    description: card.short_description ?? card.description,
    image,
  });
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
    getCategories({ locale, revalidate: 300 }).catch(() => [] as CategoryResponse[]),
  ]);

  if (!card) notFound();
  // Устаревший slug (бэкенд отдал 301, fetch последовал) → постоянный редирект на актуальный адрес.
  if (card.slug && card.slug !== slug) {
    permanentRedirect(`/${locale}/products/${card.slug}`);
  }

  const trail = buildTrail(categories, card.category_id ?? null, locale, dict.product);
  const jsonLd = productJsonLd(card, trail);
  const paintKey = card.paint_type?.toUpperCase() as keyof typeof dict.enums.paint_type | undefined;
  const paintType = paintKey ? dict.enums.paint_type[paintKey] : null;

  return (
    <article>
      <JsonLd data={jsonLd} />
      <PageBand
        tone={BAND_TONES[(card.id ?? 0) % BAND_TONES.length] ?? "blue"}
        title={card.name ?? card.sku ?? ""}
        body={card.short_description}
        product
        crumbs={<Breadcrumbs label={dict.product.breadcrumbLabel} items={[...trail, { label: card.name ?? card.sku ?? "" }]} />}
      >
        <div className="band-meta">
          {card.sku && <span className="pill"><bdi>{card.sku}</bdi></span>}
          {paintType && <span className="pill y">{paintType}</span>}
        </div>
      </PageBand>
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
    image: (card.media ?? []).filter((m) => m.type === "IMAGE" && m.url).map((m) => m.url),
    offers: {
      "@type": "AggregateOffer",
      priceCurrency: "ILS",
      lowPrice: card.min_price ?? undefined,
      highPrice: card.max_price ?? undefined,
      availability: inStock ? "https://schema.org/InStock" : "https://schema.org/OutOfStock",
    },
  };

  const breadcrumb = breadcrumbLd([...trail, { label: card.name ?? card.sku ?? "" }]);

  return [product, breadcrumb];
}
