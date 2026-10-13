import { useI18n } from "@/lib/i18n/I18nProvider";
import type { AuditEntry } from "./api";

type Dict = ReturnType<typeof useI18n>["dict"];

const ISO_DT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

/** Статусы заявки на покраску в переходах — подписью из словаря, как в карточке. */
function statusOrRaw(dict: Dict, entry: AuditEntry, v: unknown): string {
  const s = String(v ?? "").toUpperCase();
  if (entry.entity_type === "service_request") {
    return (dict.enums.service_status as Record<string, string>)[s] ?? String(v);
  }
  return String(v);
}

/**
 * Изменения записи аудита в читаемом виде. Бэкенд пишет их в нескольких формах: diff
 * {поле: {old, new}}, переход {from, to}, или просто снимок значений. Неизвестное — как есть,
 * секреты и полные контакты сервер в журнал не пишет (телефон уже маскирован до последних 4 цифр).
 */
export function ChangesView({ entry }: { entry: AuditEntry }) {
  const { dict, locale } = useI18n();
  const t = dict.audit;
  const changes = (entry.changes ?? {}) as Record<string, unknown>;
  const keys = Object.keys(changes);
  if (keys.length === 0) return <span className="text-ink-faint">—</span>;

  const fmt = (v: unknown): string => {
    if (v == null || v === "null" || v === "") return "—";
    if (typeof v === "boolean") return v ? t.yes : t.no;
    if (typeof v === "string" && ISO_DT.test(v)) {
      const d = new Date(v);
      if (!Number.isNaN(d.getTime())) return d.toLocaleString(locale === "he" ? "he-IL" : "ru-RU");
    }
    if (typeof v === "object") return JSON.stringify(v);
    return String(v);
  };
  const field = (k: string) => (t.fields as Record<string, string>)[k] ?? k;
  const transition = "from" in changes && "to" in changes;

  return (
    <ul className="space-y-0.5 text-xs">
      {transition && (
        <li className="text-ink">
          <bdi>{statusOrRaw(dict, entry, changes.from)}</bdi> <span aria-hidden>→</span>{" "}
          <bdi className="font-medium">{statusOrRaw(dict, entry, changes.to)}</bdi>
        </li>
      )}
      {keys
        .filter((k) => !(transition && (k === "from" || k === "to")))
        .map((k) => {
          const v = changes[k];
          const diff = v && typeof v === "object" && "new" in (v as object);
          return (
            <li key={k} className="text-ink-soft">
              {field(k)}:{" "}
              {diff ? (
                <>
                  <bdi className="text-ink-faint line-through">{fmt((v as { old?: unknown }).old)}</bdi>{" "}
                  <span aria-hidden>→</span> <bdi className="text-ink">{fmt((v as { new?: unknown }).new)}</bdi>
                </>
              ) : (
                <bdi className="text-ink">{fmt(v)}</bdi>
              )}
            </li>
          );
        })}
    </ul>
  );
}
