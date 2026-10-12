/**
 * Состояние фильтров каталога живёт в query-параметрах адреса — чтобы отфильтрованной выборкой
 * можно было поделиться ссылкой и чтобы работала кнопка «назад». Имена параметров совпадают с
 * параметрами API (category_id, paint_type, …) ради читаемости ссылки.
 *
 * Значения enum и в параметрах, и в фасетах — В ВЕРХНЕМ регистре (INTERIOR, WATER); в поле
 * `paint_type` карточки они приходят в нижнем (см. [[api-wire-casing]]). Фильтры оперируют верхним.
 */
export const PAINT_TYPES = ["INTERIOR", "EXTERIOR", "WOOD", "METAL", "PRIMER", "ENAMEL", "OTHER"] as const;
export const USAGE_AREAS = ["INDOOR", "OUTDOOR", "BOTH"] as const;
export const FINISHES = ["MATTE", "SATIN", "GLOSS", "SEMI_GLOSS", "TEXTURED"] as const;
export const BASE_TYPES = ["WATER", "SOLVENT", "EPOXY"] as const;

export type PaintType = (typeof PAINT_TYPES)[number];
export type UsageArea = (typeof USAGE_AREAS)[number];
export type Finish = (typeof FINISHES)[number];
export type BaseType = (typeof BASE_TYPES)[number];

export interface CatalogFilters {
  categoryId?: number;
  paintType?: PaintType;
  usageArea?: UsageArea;
  finish?: Finish;
  baseType?: BaseType;
  priceMin?: number;
  priceMax?: number;
  inStock?: boolean;
  q?: string;
}

export const PAGE_SIZE = 12;

type RawParams = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function oneOf<T extends string>(value: string | undefined, allowed: readonly T[]): T | undefined {
  return value && (allowed as readonly string[]).includes(value) ? (value as T) : undefined;
}

function toNumber(value: string | undefined): number | undefined {
  if (value === undefined || value === "") return undefined;
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

/** Разбор фильтров из searchParams (серверный компонент получает их как объект). */
export function parseFilters(params: RawParams): CatalogFilters {
  return {
    categoryId: toNumber(first(params.category_id)),
    paintType: oneOf(first(params.paint_type), PAINT_TYPES),
    usageArea: oneOf(first(params.usage_area), USAGE_AREAS),
    finish: oneOf(first(params.finish), FINISHES),
    baseType: oneOf(first(params.base_type), BASE_TYPES),
    priceMin: toNumber(first(params.price_min)),
    priceMax: toNumber(first(params.price_max)),
    inStock: first(params.in_stock) === "true" ? true : undefined,
    q: first(params.q)?.trim() || undefined,
  };
}

/** Сериализация фильтров в URLSearchParams (для router.replace и ссылок). Пустые опускаются. */
export function filtersToSearchParams(filters: CatalogFilters): URLSearchParams {
  const sp = new URLSearchParams();
  if (filters.categoryId !== undefined) sp.set("category_id", String(filters.categoryId));
  if (filters.paintType) sp.set("paint_type", filters.paintType);
  if (filters.usageArea) sp.set("usage_area", filters.usageArea);
  if (filters.finish) sp.set("finish", filters.finish);
  if (filters.baseType) sp.set("base_type", filters.baseType);
  if (filters.priceMin !== undefined) sp.set("price_min", String(filters.priceMin));
  if (filters.priceMax !== undefined) sp.set("price_max", String(filters.priceMax));
  if (filters.inStock) sp.set("in_stock", "true");
  if (filters.q) sp.set("q", filters.q);
  return sp;
}

/** Стабильный ключ фильтров для кэша TanStack Query. */
export function filtersKey(filters: CatalogFilters): string {
  return filtersToSearchParams(filters).toString();
}

/** true, если хоть один фильтр активен (для показа «сбросить фильтры»). */
export function hasActiveFilters(filters: CatalogFilters): boolean {
  return filtersToSearchParams(filters).toString().length > 0;
}
