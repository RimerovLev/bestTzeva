import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type ProductRequestListItem = components["schemas"]["ProductRequestListItem"];
export type ProductRequestDetail = components["schemas"]["ProductRequestDetail"];
export type ProductRequestItem = components["schemas"]["Item"];
export type ProductRequestMetrics = components["schemas"]["ProductRequestMetrics"];
export type HistoryEntry = components["schemas"]["HistoryEntry"];
export type CommentEntry = components["schemas"]["CommentEntry"];

export type RequestStatus = "NEW" | "CONTACTED" | "RESERVED" | "COMPLETED" | "CANCELLED" | "LOST";
export const REQUEST_STATUSES: RequestStatus[] = ["NEW", "CONTACTED", "RESERVED", "COMPLETED", "CANCELLED", "LOST"];

/**
 * Разрешённые переходы статусов — зеркалит машину состояний бэкенда (ProductRequestAdminService.ALLOWED).
 * Сервер — единственный источник истины (шлёт 409 request.transition_forbidden), но список переходов
 * не отдаётся отдельным контрактом, поэтому недоступные кнопки прячем по этой же карте. Терминальные
 * статусы (COMPLETED/CANCELLED/LOST) переходов не имеют.
 */
export const ALLOWED_TRANSITIONS: Record<RequestStatus, RequestStatus[]> = {
  NEW: ["CONTACTED", "CANCELLED", "LOST"],
  CONTACTED: ["RESERVED", "CANCELLED", "LOST"],
  RESERVED: ["COMPLETED", "CANCELLED", "LOST"],
  COMPLETED: [],
  CANCELLED: [],
  LOST: [],
};

/** Статусы на проводе приходят в нижнем регистре (см. api-wire-casing) — нормализуем к enum. */
export function normStatus(s: string | undefined | null): RequestStatus {
  return (s ?? "NEW").toUpperCase() as RequestStatus;
}

export interface ProductRequestFilters {
  status?: RequestStatus;
  manager_id?: number;
  from?: string;
  to?: string;
  in_stock?: boolean;
  q?: string;
}

export async function listProductRequests(filters: ProductRequestFilters, page: number, size: number) {
  return unwrap(
    await api.GET("/api/v1/admin/product-requests", {
      params: { query: { ...filters, page: { page, size } } },
    }),
  );
}

export async function getProductRequest(id: number): Promise<ProductRequestDetail> {
  return unwrap(await api.GET("/api/v1/admin/product-requests/{id}", { params: { path: { id } } }));
}

export async function getProductRequestMetrics(): Promise<ProductRequestMetrics> {
  return unwrap(await api.GET("/api/v1/admin/product-requests/metrics"));
}

export async function transitionProductRequest(
  id: number,
  to: RequestStatus,
  comment?: string,
): Promise<ProductRequestDetail> {
  return unwrap(
    await api.POST("/api/v1/admin/product-requests/{id}/transitions", {
      params: { path: { id } },
      body: { to, comment },
    }),
  );
}

export async function assignProductRequest(id: number, managerId: number | null): Promise<ProductRequestDetail> {
  // manager_id опущен → сервер трактует как null (снятие назначения); задан → назначение.
  return unwrap(
    await api.PATCH("/api/v1/admin/product-requests/{id}/assignee", {
      params: { path: { id } },
      body: { manager_id: managerId ?? undefined },
    }),
  );
}

/** POST /comments отвечает 201 без тела — деталь надо перечитать после успеха. */
export async function addProductRequestComment(id: number, text: string): Promise<void> {
  unwrap(
    await api.POST("/api/v1/admin/product-requests/{id}/comments", {
      params: { path: { id } },
      body: { text },
    }),
  );
}
