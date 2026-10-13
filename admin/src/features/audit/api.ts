import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type AuditEntry = components["schemas"]["AuditEntryView"];

export interface AuditFilters {
  actor_id?: number;
  entity_type?: string;
  entity_id?: number;
  action?: string;
  from?: string;
  to?: string;
}

/**
 * Типы сущностей и действия, которые пишет бэкенд (audit.record в сервисах). Нужны для фильтров и
 * подписей; неизвестный код показывается как есть — журнал не должен терять записи из-за словаря.
 */
export const ENTITY_TYPES = [
  "product",
  "product_variant",
  "product_media",
  "category",
  "warehouse",
  "service_request",
  "service_request_media",
  "service_pricing_rule",
  "portfolio_project",
  "block_list",
  "user",
  "subject",
] as const;

export const ACTIONS: { code: string; entity: (typeof ENTITY_TYPES)[number] }[] = [
  { code: "PRODUCT_CREATE", entity: "product" },
  { code: "PRODUCT_UPDATE", entity: "product" },
  { code: "PRODUCT_ACTIVE", entity: "product" },
  { code: "VARIANT_PRICE", entity: "product" },
  { code: "PRODUCT_BULK_PRICE", entity: "product" },
  { code: "PRODUCT_DELETE_SOFT", entity: "product" },
  { code: "PRODUCT_DELETE_HARD", entity: "product" },
  { code: "product_media.upload", entity: "product_media" },
  { code: "product_media.delete", entity: "product_media" },
  { code: "category.create", entity: "category" },
  { code: "category.update", entity: "category" },
  { code: "category.delete", entity: "category" },
  { code: "inventory.receipt", entity: "warehouse" },
  { code: "inventory.stocktake", entity: "product_variant" },
  { code: "inventory.transfer", entity: "product_variant" },
  { code: "inventory.write_off", entity: "product_variant" },
  { code: "warehouse.create", entity: "warehouse" },
  { code: "warehouse.update", entity: "warehouse" },
  { code: "service_request.transition", entity: "service_request" },
  { code: "service_request.assign", entity: "service_request" },
  { code: "service_request.measurement", entity: "service_request" },
  { code: "service_request.quote", entity: "service_request" },
  { code: "service_request.media_view", entity: "service_request" },
  { code: "pricing_rule.update", entity: "service_pricing_rule" },
  { code: "portfolio.create", entity: "portfolio_project" },
  { code: "portfolio.create_from_request", entity: "portfolio_project" },
  { code: "portfolio.update", entity: "portfolio_project" },
  { code: "portfolio.media_upload", entity: "portfolio_project" },
  { code: "block_list.add", entity: "block_list" },
  { code: "block_list.remove", entity: "block_list" },
  { code: "user.role.changed", entity: "user" },
  { code: "privacy.export", entity: "subject" },
  { code: "privacy.anonymize", entity: "subject" },
  { code: "privacy.photo_purge", entity: "service_request_media" },
];

export async function listAudit(f: AuditFilters, page: number, size: number) {
  return unwrap(
    await api.GET("/api/v1/admin/audit-log", {
      params: { query: { ...f, page: { page, size } } },
    }),
  );
}

export async function entityHistory(type: string, id: number) {
  return unwrap(await api.GET("/api/v1/admin/audit-log/entity/{type}/{id}", { params: { path: { type, id } } }));
}
