import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ProductSummary } from "@/lib/catalog/api";
import { ProductCard } from "./ProductCard";

const GRID = "grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4";

export function ProductGrid({
  products,
  locale,
  dict,
}: {
  products: ProductSummary[];
  locale: Locale;
  dict: Dictionary;
}) {
  return (
    <div className={GRID}>
      {products.map((product) => (
        <ProductCard key={product.id} product={product} locale={locale} dict={dict} />
      ))}
    </div>
  );
}

/** Скелетон сетки на первичную загрузку/смену фильтров. */
export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className={GRID} aria-hidden>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="overflow-hidden rounded-card border border-line bg-surface shadow-card">
          <div className="aspect-square animate-pulse bg-line/50" />
          <div className="space-y-2 p-4">
            <div className="h-3 w-16 animate-pulse rounded bg-line/50" />
            <div className="h-5 w-3/4 animate-pulse rounded bg-line/50" />
            <div className="h-4 w-1/3 animate-pulse rounded bg-line/50" />
          </div>
        </div>
      ))}
    </div>
  );
}
