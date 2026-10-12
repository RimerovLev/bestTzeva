import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ProductSummary } from "@/lib/catalog/api";
import { formatPrice } from "@/lib/format";
import { PaintCanTile } from "@/components/brand/PaintCanTile";

type PaintTypeKey = keyof Dictionary["enums"]["paint_type"];

/**
 * Карточка товара в сетке каталога (по макету 1: плитка-банка, имя, цена, латунный CTA).
 * Фото и цветовых кружков вариантов в ответе API нет (ProductSummary их не отдаёт) — вместо фото
 * стилизованная банка PaintCanTile. Ссылка ведёт по slug (B-02). Точного остатка нет — только бейдж.
 */
export function ProductCard({
  product,
  locale,
  dict,
}: {
  product: ProductSummary;
  locale: Locale;
  dict: Dictionary;
}) {
  const min = product.min_price ?? 0;
  const max = product.max_price ?? min;
  const priceLabel = min === max ? formatPrice(locale, min) : `${dict.catalog.fromPrice} ${formatPrice(locale, min)}`;

  const paintTypeKey = product.paint_type?.toUpperCase() as PaintTypeKey | undefined;
  const paintTypeLabel = paintTypeKey ? dict.enums.paint_type[paintTypeKey] : undefined;

  return (
    <Link
      href={`/${locale}/products/${product.slug ?? product.id}`}
      className="group flex flex-col overflow-hidden rounded-card border border-line bg-surface shadow-card transition duration-200 hover:-translate-y-0.5 hover:shadow-lift focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
    >
      <div className="relative">
        <PaintCanTile seed={product.id ?? 0} className="aspect-[4/3] w-full" />
        <span className="absolute bottom-2 inset-inline-end-2">
          <AvailabilityBadge inStock={product.has_stock ?? false} dict={dict} />
        </span>
      </div>

      <div className="flex flex-1 flex-col gap-1 p-4">
        {paintTypeLabel && <span className="text-xs font-medium uppercase tracking-wide text-brass">{paintTypeLabel}</span>}
        <h3 className="text-h3 font-semibold leading-snug text-navy transition-colors group-hover:text-brass">
          {product.name}
        </h3>
        <span className="text-xs text-ink-faint">
          <bdi className="numeric">{product.sku}</bdi>
        </span>

        <div className="mt-3 flex items-center justify-between gap-2">
          <span className="text-h2 font-bold text-navy">
            <bdi className="numeric">{priceLabel}</bdi>
          </span>
        </div>

        <span className="mt-3 flex items-center justify-center gap-1.5 rounded-control bg-brass px-4 py-2 text-sm font-semibold text-surface transition-colors group-hover:bg-brass-light">
          {dict.catalog.details}
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden className="icon-chevron">
            <path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
      </div>
    </Link>
  );
}

function AvailabilityBadge({ inStock, dict }: { inStock: boolean; dict: Dictionary }) {
  return (
    <span
      className={`rounded-pill px-2 py-1 text-xs font-medium shadow-sm ${
        inStock ? "bg-ok-bg text-ok-fg" : "bg-surface/90 text-ink-soft"
      }`}
    >
      {inStock ? dict.catalog.inStock : dict.catalog.outOfStock}
    </span>
  );
}
