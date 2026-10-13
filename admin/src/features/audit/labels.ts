import type { useI18n } from "@/lib/i18n/I18nProvider";

type Dict = ReturnType<typeof useI18n>["dict"];

export function actionLabel(dict: Dict, code?: string): string {
  if (!code) return "—";
  return (dict.audit.actions as Record<string, string>)[code] ?? code;
}

export function entityLabel(dict: Dict, type?: string): string {
  if (!type) return "—";
  return (dict.audit.entities as Record<string, string>)[type] ?? type;
}
