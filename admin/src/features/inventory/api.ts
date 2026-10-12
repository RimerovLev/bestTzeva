import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type InventoryListItem = components["schemas"]["InventoryListItem"];
export type LowStockItem = components["schemas"]["LowStockItem"];
export type VariantMovements = components["schemas"]["VariantMovements"];
export type WarehouseResponse = components["schemas"]["WarehouseResponse"];
export type WarehouseCreateRequest = components["schemas"]["WarehouseCreateRequest"];
export type ReceiptRequest = components["schemas"]["ReceiptRequest"];
export type WriteOffRequest = components["schemas"]["WriteOffRequest"];
export type StocktakeRequest = components["schemas"]["StocktakeRequest"];
export type TransferRequest = components["schemas"]["TransferRequest"];

export const INVENTORY_STATUSES = ["IN_STOCK", "LOW", "OUT"] as const;
export type InventoryStatus = (typeof INVENTORY_STATUSES)[number];

export interface InventoryFilters {
  warehouse_id?: number;
  category_id?: number;
  brand_id?: number;
  status?: InventoryStatus;
  q?: string;
}

export async function listInventory(filters: InventoryFilters, page: number, size: number) {
  return unwrap(await api.GET("/api/v1/admin/inventory", { params: { query: { ...filters, page: { page, size } } } }));
}
export async function lowStock(): Promise<LowStockItem[]> {
  return unwrap(await api.GET("/api/v1/admin/inventory/low-stock"));
}
export async function variantMovements(variantId: number): Promise<VariantMovements> {
  return unwrap(await api.GET("/api/v1/admin/inventory/{variantId}/movements", { params: { path: { variantId }, query: { page: { page: 0, size: 50 } } } }));
}
export async function receipt(body: ReceiptRequest) {
  return unwrap(await api.POST("/api/v1/admin/inventory/receipt", { body }));
}
export async function writeOff(body: WriteOffRequest): Promise<void> {
  const res = await api.POST("/api/v1/admin/inventory/write-off", { body });
  if (!res.response.ok) unwrap(res);
}
export async function stocktake(body: StocktakeRequest): Promise<void> {
  const res = await api.POST("/api/v1/admin/inventory/stocktake", { body });
  if (!res.response.ok) unwrap(res);
}
export async function transfer(body: TransferRequest): Promise<void> {
  const res = await api.POST("/api/v1/admin/inventory/transfer", { body });
  if (!res.response.ok) unwrap(res);
}

export async function listWarehouses(): Promise<WarehouseResponse[]> {
  return unwrap(await api.GET("/api/v1/admin/warehouses"));
}
export async function createWarehouse(body: WarehouseCreateRequest) {
  return unwrap(await api.POST("/api/v1/admin/warehouses", { body }));
}
export async function setWarehouseActive(id: number, active: boolean) {
  return unwrap(await api.PATCH("/api/v1/admin/warehouses/{id}/active", { params: { path: { id } }, body: { active } }));
}

/** Экспорт остатков в XLSX. Возвращает Blob для скачивания. */
export async function exportInventory(filters: InventoryFilters): Promise<Blob> {
  const res = await api.GET("/api/v1/admin/inventory/export", { params: { query: filters }, parseAs: "blob" });
  if (!res.response.ok) unwrap(res);
  return res.data as Blob;
}
