import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type ServiceRequestListItem = components["schemas"]["ServiceRequestListItem"];
export type ServiceRequestDetail = components["schemas"]["ServiceRequestDetail"];
export type ServiceRequestMetrics = components["schemas"]["ServiceRequestMetrics"];
export type HistoryView = components["schemas"]["HistoryView"];
export type CommentView = components["schemas"]["CommentView"];
export type PricingRuleView = components["schemas"]["PricingRuleView"];
export type PricingRuleUpdate = components["schemas"]["PricingRuleUpdate"];
export type QuoteRequest = components["schemas"]["QuoteRequest"];
export type ServiceRequestMedia = components["schemas"]["ServiceRequestMediaView"];

export type ServiceStatus =
  | "NEW"
  | "CONTACTED"
  | "MEASUREMENT_SCHEDULED"
  | "MEASURED"
  | "QUOTED"
  | "ACCEPTED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "REJECTED"
  | "LOST";

export const SERVICE_STATUSES: ServiceStatus[] = [
  "NEW", "CONTACTED", "MEASUREMENT_SCHEDULED", "MEASURED", "QUOTED",
  "ACCEPTED", "IN_PROGRESS", "COMPLETED", "REJECTED", "LOST",
];

export const PROPERTY_TYPES = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL", "OTHER"] as const;
export const WORK_TYPES = ["WALLS", "CEILING", "FACADE", "WOOD", "METAL", "DECORATIVE"] as const;

/**
 * Разрешённые переходы — зеркалит ServiceRequestAdminService.ALLOWED. Переходы в
 * MEASUREMENT_SCHEDULED и QUOTED идут через специализированные эндпоинты (/measurement с датой,
 * /quote с ценой), поэтому из общих кнопок статуса их исключаем (см. genericTransitions).
 */
export const ALLOWED_TRANSITIONS: Record<ServiceStatus, ServiceStatus[]> = {
  NEW: ["CONTACTED", "REJECTED", "LOST"],
  CONTACTED: ["MEASUREMENT_SCHEDULED", "REJECTED", "LOST"],
  MEASUREMENT_SCHEDULED: ["MEASURED", "REJECTED", "LOST"],
  MEASURED: ["QUOTED", "REJECTED", "LOST"],
  QUOTED: ["ACCEPTED", "REJECTED", "LOST"],
  ACCEPTED: ["IN_PROGRESS", "REJECTED", "LOST"],
  IN_PROGRESS: ["COMPLETED", "REJECTED", "LOST"],
  COMPLETED: [],
  REJECTED: [],
  LOST: [],
};

/** Кнопки прямого перехода (без спец-эндпоинтов). */
export function genericTransitions(status: ServiceStatus): ServiceStatus[] {
  return ALLOWED_TRANSITIONS[status].filter((s) => s !== "MEASUREMENT_SCHEDULED" && s !== "QUOTED");
}
export function canScheduleMeasurement(status: ServiceStatus): boolean {
  return ALLOWED_TRANSITIONS[status].includes("MEASUREMENT_SCHEDULED");
}
export function canQuote(status: ServiceStatus): boolean {
  return ALLOWED_TRANSITIONS[status].includes("QUOTED");
}

/** Статусы/типы на проводе — нижний регистр (api-wire-casing); нормализуем к enum. */
export function normStatus(s: string | undefined | null): ServiceStatus {
  return (s ?? "NEW").toUpperCase() as ServiceStatus;
}
export function upper(s: string | undefined | null): string {
  return (s ?? "").toUpperCase();
}

export interface ServiceRequestFilters {
  status?: ServiceStatus;
  property_type?: (typeof PROPERTY_TYPES)[number];
  city?: string;
  manager_id?: number;
  from?: string;
  to?: string;
  area_min?: number;
  area_max?: number;
  source?: string;
  q?: string;
}

export async function listServiceRequests(filters: ServiceRequestFilters, page: number, size: number) {
  return unwrap(
    await api.GET("/api/v1/admin/service-requests", { params: { query: { ...filters, page: { page, size } } } }),
  );
}

export async function getServiceRequest(id: number): Promise<ServiceRequestDetail> {
  return unwrap(await api.GET("/api/v1/admin/service-requests/{id}", { params: { path: { id } } }));
}

export async function getServiceRequestMetrics(): Promise<ServiceRequestMetrics> {
  return unwrap(await api.GET("/api/v1/admin/service-requests/metrics"));
}

export async function transitionServiceRequest(id: number, to: ServiceStatus, comment?: string): Promise<ServiceRequestDetail> {
  return unwrap(
    await api.POST("/api/v1/admin/service-requests/{id}/transitions", {
      params: { path: { id } },
      body: { to, comment },
    }),
  );
}

export async function assignServiceRequest(id: number, managerId: number | null): Promise<ServiceRequestDetail> {
  return unwrap(
    await api.PATCH("/api/v1/admin/service-requests/{id}/assignee", {
      params: { path: { id } },
      body: { manager_id: managerId ?? undefined },
    }),
  );
}

export async function autoAssignServiceRequest(id: number): Promise<ServiceRequestDetail> {
  return unwrap(await api.POST("/api/v1/admin/service-requests/{id}/auto-assign", { params: { path: { id } } }));
}

/** POST /comments отвечает 201 без тела — деталь надо перечитать. Поле ввода — text, в ленте — body. */
export async function addServiceComment(id: number, text: string): Promise<void> {
  unwrap(
    await api.POST("/api/v1/admin/service-requests/{id}/comments", {
      params: { path: { id } },
      body: { text },
    }),
  );
}

export async function scheduleMeasurement(id: number, atIso: string): Promise<ServiceRequestDetail> {
  return unwrap(
    await api.POST("/api/v1/admin/service-requests/{id}/measurement", {
      params: { path: { id } },
      body: { at: atIso },
    }),
  );
}

export async function createQuote(id: number, body: QuoteRequest): Promise<ServiceRequestDetail> {
  return unwrap(await api.POST("/api/v1/admin/service-requests/{id}/quote", { params: { path: { id } }, body }));
}

/**
 * Фото клиента: presigned GET из приватного бакета, ссылки живут ~5 минут. Каждый вызов пишется
 * в аудит на бэкенде (доступ к персональным данным) — не дёргать без надобности.
 */
export async function listServiceRequestMedia(id: number): Promise<ServiceRequestMedia[]> {
  return unwrap(await api.GET("/api/v1/admin/service-requests/{id}/media", { params: { path: { id } } }));
}

/** GET /quote-pdf → { url: presigned }. Возвращаем ссылку на сгенерированный PDF. */
export async function getQuotePdfUrl(id: number): Promise<string | undefined> {
  const map = unwrap(await api.GET("/api/v1/admin/service-requests/{id}/quote-pdf", { params: { path: { id } } }));
  return (map as Record<string, string>)?.url;
}

export async function listPricingRules(): Promise<PricingRuleView[]> {
  return unwrap(await api.GET("/api/v1/admin/pricing-rules"));
}

export async function updatePricingRule(id: number, body: PricingRuleUpdate): Promise<PricingRuleView> {
  return unwrap(await api.PUT("/api/v1/admin/pricing-rules/{id}", { params: { path: { id } }, body }));
}

/** Создать проект портфолио из завершённой заявки. Фото клиента НЕ переносятся (нужно отдельное согласие). */
export async function createPortfolioFromRequest(requestId: number): Promise<number | undefined> {
  const map = unwrap(
    await api.POST("/api/v1/admin/portfolio/from-request/{requestId}", { params: { path: { requestId } } }),
  );
  const values = Object.values((map as Record<string, number>) ?? {});
  return values[0];
}
