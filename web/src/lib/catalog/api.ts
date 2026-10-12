import type { components } from "@/lib/api/schema";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "@/lib/api/problem";
import type { Locale } from "@/lib/i18n/config";
import { filtersToSearchParams, PAGE_SIZE, type CatalogFilters } from "./params";

export type ProductSearchResult = components["schemas"]["ProductSearchResult"];
export type ProductSummary = components["schemas"]["ProductSummary"];
export type ProductFacets = components["schemas"]["ProductFacets"];
export type CategoryResponse = components["schemas"]["CategoryResponse"];
export type ProductCard = components["schemas"]["ProductCard"];
export type VariantCard = components["schemas"]["VariantCard"];
export type ProductMediaView = components["schemas"]["ProductMediaView"];
export type CoverageCalcResult = components["schemas"]["CoverageCalcResult"];
export type VariantCoverage = components["schemas"]["VariantCoverage"];
export type ColorSwatch = components["schemas"]["ColorSwatchResponse"];
export type ProductRequestCreate = components["schemas"]["ProductRequestCreate"];
export type ProductRequestResponse = components["schemas"]["ProductRequestResponse"];

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

function requestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

interface GetOptions {
  locale: Locale;
  /** ISR: секунды кэша на сервере. На клиенте игнорируется (кэширует TanStack Query). */
  revalidate?: number;
  signal?: AbortSignal;
}

async function apiGet<T>(
  path: string,
  query: Record<string, string | number | boolean | undefined>,
  { locale, revalidate, signal }: GetOptions,
): Promise<T> {
  const url = new URL(API_BASE + path);
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== "") url.searchParams.set(key, String(value));
  }

  let res: Response;
  try {
    res = await fetch(url, {
      headers: { "Accept-Language": locale, "X-Request-Id": requestId() },
      signal,
      ...(revalidate !== undefined ? { next: { revalidate } } : {}),
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
  }

  if (!res.ok) {
    const body: unknown = await res.json().catch(() => undefined);
    if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as T;
}

/** Список каталога: страница товаров + фасеты. page — 0-based. */
export function getProducts(
  filters: CatalogFilters,
  page: number,
  options: GetOptions,
): Promise<ProductSearchResult> {
  const query = Object.fromEntries(filtersToSearchParams(filters));
  return apiGet<ProductSearchResult>(
    "/api/v1/products",
    { ...query, page, size: PAGE_SIZE },
    options,
  );
}

/** Дерево категорий (плоско, с локализованными именами и счётчиками товаров). */
export function getCategories(options: GetOptions): Promise<CategoryResponse[]> {
  return apiGet<CategoryResponse[]>("/api/v1/categories", {}, options);
}

/**
 * Карточка товара по slug. fetch следует за 301 бэкенда (устаревший slug), поэтому вернётся
 * карточка с актуальным slug — страница затем делает постоянный редирект на него (SEO, B-02).
 */
export function getProductBySlug(slug: string, options: GetOptions): Promise<ProductCard> {
  return apiGet<ProductCard>(`/api/v1/products/${encodeURIComponent(slug)}`, {}, options);
}

/** Палитра цветов. Фильтры: система (RAL/NCS/INTERNAL, в верхнем регистре), поиск по коду `q`. */
export function getColors(
  filter: { system?: string; q?: string; popular?: boolean },
  options: GetOptions,
): Promise<ColorSwatch[]> {
  return apiGet<ColorSwatch[]>(
    "/api/v1/colors",
    { system: filter.system, q: filter.q, popular: filter.popular },
    options,
  );
}

/**
 * Отправка заявки на товар (F-3.1). Тело — snake_case (customer_name, items[{variant_id, qty}], …).
 * Ошибки RFC 9457 → ApiError (с retryAfter из Retry-After для common.rate_limited).
 */
export async function submitProductRequest(
  body: ProductRequestCreate,
  options: GetOptions,
): Promise<ProductRequestResponse> {
  const url = `${API_BASE}/api/v1/product-requests`;
  let res: Response;
  try {
    res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": options.locale,
        "X-Request-Id": requestId(),
      },
      body: JSON.stringify(body),
      signal: options.signal,
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
  }
  if (!res.ok) {
    const problem: unknown = await res.json().catch(() => undefined);
    const retryHeader = res.headers.get("Retry-After");
    const retryAfter = retryHeader ? Number(retryHeader) : undefined;
    if (isProblemDetail(problem)) {
      throw ApiError.fromProblem(problem, res.status, Number.isFinite(retryAfter) ? retryAfter : undefined);
    }
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as ProductRequestResponse;
}

/** Расчёт расхода краски (B-04). Клиентский вызов — считается на сервере, здесь только запрос. */
export async function calcCoverage(
  slug: string,
  area: number,
  coats: number,
  options: GetOptions,
): Promise<CoverageCalcResult> {
  const url = `${API_BASE}/api/v1/products/${encodeURIComponent(slug)}/coverage-calc`;
  let res: Response;
  try {
    res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": options.locale,
        "X-Request-Id": requestId(),
      },
      body: JSON.stringify({ area, coats }),
      signal: options.signal,
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
  }
  if (!res.ok) {
    const body: unknown = await res.json().catch(() => undefined);
    if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as CoverageCalcResult;
}
