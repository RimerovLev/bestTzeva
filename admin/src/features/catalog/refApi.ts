import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";
import type { components as PublicComponents } from "@/lib/api/schema.public";

export type CategorySaveRequest = components["schemas"]["CategorySaveRequest"];
export type CategoryReorderRequest = components["schemas"]["CategoryReorderRequest"];
export type ColorSwatchSaveRequest = components["schemas"]["ColorSwatchSaveRequest"];
export type ColorSwatch = PublicComponents["schemas"]["ColorSwatchResponse"];
export type ImportResult = components["schemas"]["ImportResult"];

export const COLOR_SYSTEMS = ["RAL", "NCS", "INTERNAL"] as const;

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
