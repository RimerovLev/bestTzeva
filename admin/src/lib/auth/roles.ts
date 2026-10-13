import type { Dictionary } from "@/lib/i18n/dictionaries";

/** Роли из GET /auth/me. Совпадают с enum бэкенда. */
export type Role = "CUSTOMER" | "SUPPORT" | "WAREHOUSE" | "MANAGER" | "ADMIN";

/** Служебные роли — допуск в админ-зону (роль не ниже SUPPORT). CUSTOMER не допускается. */
export const STAFF_ROLES: Role[] = ["SUPPORT", "WAREHOUSE", "MANAGER", "ADMIN"];

export function isStaff(role: Role): boolean {
  return STAFF_ROLES.includes(role);
}

/** Ключ раздела совпадает с ключом в словаре (dict.nav). */
export type SectionKey = keyof Dictionary["nav"];

export interface Section {
  key: SectionKey;
  path: string;
  roles: Role[];
}

/**
 * Разделы админки и роли, которым они видны. Скрытие — удобство, НЕ защита: права проверяет
 * сервер (403 — штатный ответ). Основано на docs/roles.md и объёме задач F-7…F-10:
 * склад — WAREHOUSE+; каталог/цвета/антиспам — MANAGER+; аудит, приватность и пользователи — только ADMIN.
 */
export const SECTIONS: Section[] = [
  { key: "dashboard", path: "/", roles: [...STAFF_ROLES] },
  { key: "productRequests", path: "/product-requests", roles: ["SUPPORT", "WAREHOUSE", "MANAGER", "ADMIN"] },
  { key: "serviceRequests", path: "/service-requests", roles: ["SUPPORT", "MANAGER", "ADMIN"] },
  { key: "warehouse", path: "/warehouse", roles: ["WAREHOUSE", "MANAGER", "ADMIN"] },
  { key: "catalog", path: "/catalog", roles: ["MANAGER", "ADMIN"] },
  { key: "categories", path: "/categories", roles: ["MANAGER", "ADMIN"] },
  { key: "colors", path: "/colors", roles: ["MANAGER", "ADMIN"] },
  { key: "spam", path: "/spam", roles: ["MANAGER", "ADMIN"] },
  { key: "audit", path: "/audit", roles: ["ADMIN"] },
  { key: "privacy", path: "/privacy", roles: ["ADMIN"] },
  { key: "users", path: "/users", roles: ["ADMIN"] },
];

/** Разделы, видимые роли, в порядке меню. */
export function visibleSections(role: Role): Section[] {
  return SECTIONS.filter((section) => section.roles.includes(role));
}

/** Доступен ли раздел с таким путём этой роли (для гварда маршрута). */
export function canAccessPath(role: Role, path: string): boolean {
  const section = SECTIONS.find((s) => s.path === path);
  return section ? section.roles.includes(role) : false;
}
