import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { Skeleton } from "@/components/ui/Skeleton";
import { entityHistory } from "./api";
import { ChangesView } from "./format";
import { actionLabel } from "./labels";

/**
 * История изменений объекта из журнала аудита — раскрывающийся блок в карточке. Журнал доступен
 * только ADMIN, поэтому остальным блок не показывается вовсе; запрос уходит при первом раскрытии.
 */
export function EntityHistory({ type, id }: { type: string; id: number }) {
  const { user } = useAuth();
  const allowed = !!user && canAccessPath(user.role, "/audit");
  const [open, setOpen] = useState(false);
  if (!allowed) return null;
  return (
    <details className="group rounded-control border border-line" onToggle={(e) => setOpen(e.currentTarget.open)}>
      <HistorySummary />
      {open && (
        <div className="border-t border-line p-3">
          <HistoryList type={type} id={id} />
        </div>
      )}
    </details>
  );
}

function HistorySummary() {
  const { dict } = useI18n();
  return (
    <summary className="flex cursor-pointer list-none items-center justify-between gap-2 px-3 py-2 text-sm font-semibold text-navy">
      {dict.audit.historyTitle}
      <span aria-hidden className="text-ink-faint transition-transform group-open:rotate-180">▾</span>
    </summary>
  );
}

export function HistoryList({ type, id }: { type: string; id: number }) {
  const { dict, locale } = useI18n();
  const t = dict.audit;
  const q = useQuery({ queryKey: ["admin-audit-entity", type, id], queryFn: () => entityHistory(type, id) });
  const dt = (iso?: string) =>
    iso ? new Date(iso).toLocaleString(locale === "he" ? "he-IL" : "ru-RU", { dateStyle: "short", timeStyle: "short" }) : "—";

  if (q.isLoading) return <div className="space-y-2"><Skeleton className="h-4 w-2/3" /><Skeleton className="h-4 w-1/2" /></div>;
  if (q.isError) return <p className="text-sm text-crit-fg">{t.loadError}</p>;
  const items = q.data ?? [];
  if (items.length === 0) return <p className="text-sm text-ink-soft">{t.historyEmpty}</p>;

  return (
    <ol className="space-y-3">
      {items.map((e) => (
        <li key={e.id} className="border-s-2 border-line ps-3">
          <div className="flex flex-wrap items-baseline gap-x-2 text-sm">
            <span className="font-medium text-ink">{actionLabel(dict, e.action)}</span>
            <span className="numeric text-xs text-ink-faint">{dt(e.created_at)}</span>
            <span className="text-xs text-ink-soft">
              · <bdi>{e.actor_user_id == null ? t.system : e.actor_name ?? `#${e.actor_user_id}`}</bdi>
            </span>
          </div>
          <div className="mt-1"><ChangesView entry={e} /></div>
        </li>
      ))}
    </ol>
  );
}
