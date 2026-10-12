import Link from "next/link";
import type { CSSProperties } from "react";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ProductSummary } from "@/lib/catalog/api";
import { formatPrice } from "@/lib/format";
import { RAIL_COLORS } from "@/lib/motion/palette";

type PaintTypeKey = keyof Dictionary["enums"]["paint_type"];

/**
 * Карточка товара в сетке каталога — та же, что в ленте на главной: банка на цветном круге, тип,
 * имя, артикул, цена «от». Фото и цвета варианта в ProductSummary нет, поэтому круг декоративный
 * (по id — стабилен для товара) и конкретный оттенок не изображает. Точного остатка нет — только
 * «в наличии / нет». Ссылка — по slug (B-02).
 */
export function ProductCard({ product, locale, dict }: { product: ProductSummary; locale: Locale; dict: Dictionary }) {
  const min = product.min_price;
  const max = product.max_price ?? min;
  const typeKey = product.paint_type?.toUpperCase() as PaintTypeKey | undefined;
  const typeLabel = typeKey ? dict.enums.paint_type[typeKey] : undefined;
  const color = RAIL_COLORS[(product.id ?? 0) % RAIL_COLORS.length] ?? "#ffc933";
  const inStock = product.has_stock ?? false;

  return (
    <Link href={`/${locale}/products/${product.slug ?? product.id}`} className="prod card lift">
      <div className="prod-vis" style={{ "--c": color } as CSSProperties}>
        <span className={`avail${inStock ? "" : " out"}`}>{inStock ? dict.catalog.inStock : dict.catalog.outOfStock}</span>
        <div className="prod-can" />
      </div>
      {typeLabel && <span className="prod-type">{typeLabel}</span>}
      <h3>{product.name}</h3>
      {product.sku && <span className="prod-sku"><bdi>{product.sku}</bdi></span>}
      <div className="prod-foot">
        {min != null ? (
          <span className="price">
            {max != null && max !== min && <small>{dict.catalog.fromPrice} </small>}
            <bdi>{formatPrice(locale, min)}</bdi>
          </span>
        ) : <span />}
        <span className="add">{dict.catalog.details}</span>
      </div>
    </Link>
  );
}
