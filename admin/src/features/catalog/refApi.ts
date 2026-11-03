import { api, API_BASE, authFetch, unwrap } from "@/lib/api/http";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "@/lib/api/problem";
import type { components } from "@/lib/api/schema.admin";
import type { components as PublicComponents } from "@/lib/api/schema.public";

export type CategorySaveRequest = components["schemas"]["CategorySaveRequest"];
export type CategoryReorderRequest = components["schemas"]["CategoryReorderRequest"];
export type ColorSwatchSaveRequest = components["schemas"]["ColorSwatchSaveRequest"];
export type ColorSwatch = PublicComponents["schemas"]["ColorSwatchResponse"];
export type ImportResult = components["schemas"]["ImportResult"];

export const COLOR_SYSTEMS = ["RAL", "NCS", "INTERNAL"] as const;

// ── Бренды (справочник для выпадающего списка) ────────────────────────────────

export interface BrandRef {
  id: number;
  name: string;
  slug: string;
}

export async function listBrands(): Promise<BrandRef[]> {
  const res = await authFetch(`${API_BASE}/api/v1/admin/brands`);
  if (!res.ok) return [];
  return (await res.json()) as BrandRef[];
}

export async function createBrand(name: string): Promise<BrandRef> {
  const res = await authFetch(`${API_BASE}/api/v1/admin/brands`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
  if (!res.ok) {
    const body: unknown = await res.json().catch(() => undefined);
    if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as BrandRef;
}

export async function deleteBrand(id: number): Promise<void> {
  const res = await authFetch(`${API_BASE}/api/v1/admin/brands/${id}`, { method: "DELETE" });
  if (!res.ok && res.status !== 204) {
    const body: unknown = await res.json().catch(() => undefined);
    if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
}

// ── Категории ────────────────────────────────────────────────────────────────

export async function createCategory(body: CategorySaveRequest) {
  return unwrap(await api.POST("/api/v1/admin/categories", { body }));
}
export async function updateCategory(id: number, body: CategorySaveRequest) {
  return unwrap(await api.PUT("/api/v1/admin/categories/{id}", { params: { path: { id } }, body }));
}
export async function reorderCategories(body: CategoryReorderRequest): Promise<void> {
  const res = await api.PUT("/api/v1/admin/categories/reorder", { body });
  if (!res.response.ok) unwrap(res);
}
export async function deleteCategory(id: number): Promise<void> {
  const res = await api.DELETE("/api/v1/admin/categories/{id}", { params: { path: { id } } });
  if (!res.response.ok) unwrap(res);
}

// ── Цвета ────────────────────────────────────────────────────────────────────

export async function listColors(filter: { system?: string; q?: string }): Promise<ColorSwatch[]> {
  return unwrap(
    await api.GET("/api/v1/colors", {
      params: { query: { system: filter.system as "RAL" | "NCS" | "INTERNAL" | undefined, q: filter.q } },
    }),
  );
}
export async function createColor(body: ColorSwatchSaveRequest) {
  return unwrap(await api.POST("/api/v1/admin/colors", { body }));
}
export async function updateColor(id: number, body: ColorSwatchSaveRequest) {
  return unwrap(await api.PUT("/api/v1/admin/colors/{id}", { params: { path: { id } }, body }));
}
export async function deleteColor(id: number): Promise<void> {
  const res = await api.DELETE("/api/v1/admin/colors/{id}", { params: { path: { id } } });
  if (!res.response.ok) unwrap(res);
}

// ── Импорт ───────────────────────────────────────────────────────────────────

/** Импорт каталога (CSV/XLSX). dryRun=true — только валидация и отчёт; false — применить. */
export async function importProducts(file: File, dryRun: boolean): Promise<ImportResult> {
  const form = new FormData();
  form.append("file", file);
  return unwrap(
    await api.POST("/api/v1/admin/products/import", {
      params: { query: { dry_run: dryRun } },
      body: form as unknown as { file: string },
      bodySerializer: (b: unknown) => b as BodyInit,
    }),
  );
}
