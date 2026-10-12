import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";
import type { components as PublicComponents } from "@/lib/api/schema.public";

export type ProductListItem = components["schemas"]["ProductListItem"];
export type ProductSaveRequest = components["schemas"]["ProductSaveRequest"];
export type ProductResponse = components["schemas"]["ProductResponse"];
export type BulkPriceRequest = components["schemas"]["BulkPriceRequest"];
export type CategoryResponse = PublicComponents["schemas"]["CategoryResponse"];

export const PAINT_TYPES = ["INTERIOR", "EXTERIOR", "WOOD", "METAL", "PRIMER", "ENAMEL", "OTHER"] as const;
export const USAGE_AREAS = ["INDOOR", "OUTDOOR", "BOTH"] as const;
export const FINISHES = ["MATTE", "SATIN", "GLOSS", "SEMI_GLOSS", "TEXTURED"] as const;
export const BASE_TYPES = ["WATER", "SOLVENT", "EPOXY"] as const;

export interface ProductFilters {
  category_id?: number;
  brand_id?: number;
  paint_type?: (typeof PAINT_TYPES)[number];
  active?: boolean;
  in_stock?: boolean;
  price_min?: number;
  price_max?: number;
  q?: string;
}

/** Список товаров (PageResponse). page — 0-based. */
export async function listProducts(filters: ProductFilters, page: number, size: number) {
  return unwrap(
    await api.GET("/api/v1/admin/products", {
      params: { query: { ...filters, page: { page, size } } },
    }),
  );
}

/** Создание товара-агрегата одним запросом (товар + переводы + характеристики + варианты). */
export async function createProduct(body: ProductSaveRequest): Promise<ProductResponse> {
  return unwrap(await api.POST("/api/v1/admin/products", { body }));
}

/** Мягкая деактивация/активация. */
export async function setProductActive(id: number, active: boolean): Promise<ProductResponse> {
  return unwrap(
    await api.PATCH("/api/v1/admin/products/{id}/active", {
      params: { path: { id } },
      body: { active },
    }),
  );
}

/** Удаление: hard=false — мягкое (деактивация на сервере), hard=true — физическое (если разрешено). */
export async function deleteProduct(id: number, hard: boolean): Promise<void> {
  const res = await api.DELETE("/api/v1/admin/products/{id}", {
    params: { path: { id }, query: { hard } },
  });
  if (!res.response.ok) unwrap(res);
}

/** Массовое изменение цен по фильтру (только процент). Возвращает число затронутых вариантов. */
export async function bulkPrice(body: BulkPriceRequest): Promise<number> {
  const res = unwrap(await api.POST("/api/v1/admin/products/bulk-price", { body }));
  return (res as { affected_variants?: number }).affected_variants ?? 0;
}

/** Категории (публичный справочник, с именами и счётчиками) — для фильтра и формы. */
export async function listCategories(): Promise<CategoryResponse[]> {
  return unwrap(await api.GET("/api/v1/categories"));
}
