import { useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { X } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { FilterPanel } from "@/components/ui/FilterPanel";
import { ACTIONS, ENTITY_TYPES, listAudit, type AuditEntry, type AuditFilters } from "./api";
import { ChangesView } from "./format";
import { actionLabel, entityLabel } from "./labels";

const PAGE_SIZE = 30;
const INPUT = "rounded-control border border-line px-3 py-2 text-ink";

/**
 * Журнал аудита (F-10.2, только ADMIN): кто, когда и что менял. Фильтры по типу объекта, действию,
 * периоду; по автору и конкретному объекту — щелчком по ячейке в таблице (списка сотрудников со
 * всеми ролями API не отдаёт, а щелчок по строке и так точнее).
 */
export function AuditPage() {
  const { user } = useAuth();
  const { dict, locale } = useI18n();
  const t = dict.audit;

  const [filters, setFilters] = useState<AuditFilters>({});
  const [actorName, setActorName] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const allowed = !!user && canAccessPath(user.role, "/audit");
  const q = useQuery({
    queryKey: ["admin-audit", filters, page],
    queryFn: () => listAudit(filters, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
    enabled: allowed,
  });

  if (!allowed) {
    return (
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1>
        <p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p>
      </div>
    );
  }

  function patch(p: Partial<AuditFilters>) {
    setFilters((prev) => ({ ...prev, ...p }));
    setPage(0);
  }
  function reset() {
    setFilters({});
    setActorName(null);
    setPage(0);
  }

  const dt = (iso?: string) =>
    iso ? new Date(iso).toLocaleString(locale === "he" ? "he-IL" : "ru-RU", { dateStyle: "short", timeStyle: "medium" }) : "—";
  const actions = ACTIONS.filter((a) => !filters.entity_type || a.entity === filters.entity_type);

  const columns: Column<AuditEntry>[] = [
    { key: "created_at", header: t.colWhen, render: (r) => <span className="numeric whitespace-nowrap">{dt(r.created_at)}</span> },
    {
      key: "actor",
      header: t.colActor,
      render: (r) =>
        r.actor_user_id == null ? (
          <span className="text-ink-faint">{t.system}</span>
        ) : (
          <div>
            <button
              type="button"
              title={t.filterByActor}
              onClick={() => {
                patch({ actor_id: r.actor_user_id });
                setActorName(r.actor_name ?? `#${r.actor_user_id}`);
              }}
              className="text-start font-medium text-navy hover:text-brass hover:underline"
            >
              <bdi>{r.actor_name ?? `#${r.actor_user_id}`}</bdi>
            </button>
            {r.actor_ip && <div className="numeric text-xs text-ink-faint" dir="ltr">{r.actor_ip}</div>}
          </div>
        ),
    },
    {
      key: "action",
      header: t.colAction,
      render: (r) => (
        <div>
          <div className="text-ink">{actionLabel(dict, r.action)}</div>
          <code className="text-[11px] text-ink-faint" dir="ltr">{r.action}</code>
        </div>
      ),
    },
    {
      key: "object",
      header: t.colObject,
      render: (r) => (
        <div className="whitespace-nowrap">
          <span className="text-ink-soft">{entityLabel(dict, r.entity_type)}</span>
          {r.entity_id != null && (
            <>
              {" "}
              <button
                type="button"
                title={t.filterByObject}
                onClick={() => patch({ entity_type: r.entity_type, entity_id: r.entity_id })}
                className="numeric text-brass hover:underline"
              >
                <bdi>#{r.entity_id}</bdi>
              </button>
            </>
          )}
        </div>
      ),
    },
    { key: "changes", header: t.colChanges, render: (r) => <ChangesView entry={r} /> },
  ];

  const data = q.data;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-h2 font-bold text-navy">{t.title}</h1>
        <p className="mt-1 text-sm text-ink-soft">{t.subtitle}</p>
      </div>

      <FilterPanel title={t.filters} onReset={reset} resetLabel={dict.common.reset}>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.entity}
          <select
            value={filters.entity_type ?? ""}
            onChange={(e) => {
              const v = e.target.value || undefined;
              const keepAction = filters.action && ACTIONS.some((a) => a.code === filters.action && a.entity === v);
              patch({ entity_type: v, entity_id: undefined, action: keepAction ? filters.action : undefined });
            }}
            className={INPUT}
          >
            <option value="">{t.entityAll}</option>
            {ENTITY_TYPES.map((e) => (
              <option key={e} value={e}>{entityLabel(dict, e)}</option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.entityId}
          <input
            type="number"
            min={1}
            inputMode="numeric"
            disabled={!filters.entity_type}
            value={filters.entity_id ?? ""}
            onChange={(e) => patch({ entity_id: e.target.value ? Number(e.target.value) : undefined })}
            className={`${INPUT} w-28 disabled:bg-paper-warm/60`}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.action}
          <select value={filters.action ?? ""} onChange={(e) => patch({ action: e.target.value || undefined })} className={INPUT}>
            <option value="">{t.actionAll}</option>
            {actions.map((a) => (
              <option key={a.code} value={a.code}>{actionLabel(dict, a.code)}</option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.from}
          <input type="date" value={filters.from ?? ""} onChange={(e) => patch({ from: e.target.value || undefined })} className={INPUT} />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.to}
          <input type="date" value={filters.to ?? ""} onChange={(e) => patch({ to: e.target.value || undefined })} className={INPUT} />
        </label>
        {filters.actor_id != null && (
          <span className="mb-1 inline-flex items-center gap-1 rounded-pill bg-navy/10 py-1 pe-1 ps-3 text-sm text-navy">
            {t.actor}: <bdi className="font-medium">{actorName ?? `#${filters.actor_id}`}</bdi>
            <button
              type="button"
              aria-label={t.clearActor}
              onClick={() => {
                patch({ actor_id: undefined });
                setActorName(null);
              }}
              className="grid h-6 w-6 place-items-center rounded-pill hover:bg-navy/10"
            >
              <X className="h-3.5 w-3.5" aria-hidden />
            </button>
          </span>
        )}
      </FilterPanel>

      {q.isError ? (
        <p className="rounded-card border border-line bg-surface p-6 text-crit-fg shadow-card">{t.loadError}</p>
      ) : (
        <DataTable
          columns={columns}
          rows={data?.items ?? []}
          getRowKey={(r) => r.id!}
          labels={{
            page: dict.table.page,
            of: dict.table.of,
            rowsTotal: dict.table.rowsTotal,
            prev: dict.table.prev,
            next: dict.table.next,
            empty: q.isLoading ? dict.common.loading : dict.common.empty,
          }}
          meta={
            data
              ? { page: data.page ?? 0, size: data.size ?? PAGE_SIZE, totalElements: data.total_elements ?? 0, totalPages: data.total_pages ?? 0 }
              : undefined
          }
          onPageChange={setPage}
        />
      )}
      <p className="text-xs text-ink-soft">{t.retentionHint}</p>
    </div>
  );
}
