import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ProductSummary } from "@/lib/catalog/api";
import { ProductCard } from "./ProductCard";

export function ProductGrid({ products, locale, dict }: { products: ProductSummary[]; locale: Locale; dict: Dictionary }) {
  return (
    <div className="prod-grid">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} locale={locale} dict={dict} />
      ))}
    </div>
  );
}

/** Скелетон сетки на первичную загрузку/смену фильтров. */
export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="prod-grid" aria-hidden>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="prod card sk">
          <div className="prod-vis" />
          <div className="sk-line" style={{ width: "40%" }} />
          <div className="sk-line" style={{ width: "80%", height: 18 }} />
          <div className="sk-line" style={{ width: "30%" }} />
        </div>
      ))}
    </div>
  );
}
