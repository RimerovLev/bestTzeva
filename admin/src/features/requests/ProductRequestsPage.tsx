import { useState } from "react";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { FilterPanel } from "@/components/ui/FilterPanel";
import { AssigneeName } from "@/features/shared/assignees";
import { useAssignees } from "@/features/shared/useAssignees";
import { StatusBadge } from "./StatusBadge";
import { ProductRequestDetailModal } from "./ProductRequestDetailModal";
import {
  listProductRequests,
  getProductRequestMetrics,
  REQUEST_STATUSES,
  type ProductRequestFilters,
  type ProductRequestListItem,
} from "./api";

const PAGE_SIZE = 20;
const DAY_MS = 24 * 60 * 60 * 1000;

/** Строка без менеджера старше суток — требует внимания (плановое правило F-9.1). */
function isStale(row: ProductRequestListItem): boolean {
  if (row.assigned_manager_id != null) return false;
  if (!row.created_at) return false;
  return Date.now() - new Date(row.created_at).getTime() > DAY_MS;
}

export function ProductRequestsPage() {
  const { dict, locale } = useI18n();
  const t = dict.productRequests;

  const [filters, setFilters] = useState<ProductRequestFilters>({});
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const assignees = useAssignees();

  const effective = { ...filters, q: q.trim() || undefined };
  const listQuery = useQuery({
    queryKey: ["admin-product-requests", locale, effective, page],
    queryFn: () => listProductRequests(effective, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });
  const metricsQuery = useQuery({
    queryKey: ["admin-product-requests-metrics", locale],
    queryFn: getProductRequestMetrics,
  });

  function patch(p: Partial<ProductRequestFilters>) {
    setFilters((prev) => ({ ...prev, ...p }));
    setPage(0);
  }
  function reset() {
    setFilters({});
    setQ("");
    setPage(0);
  }

  function refresh() {
    listQuery.refetch();
    metricsQuery.refetch();
  }

  const dateFmt = (iso?: string) => (iso ? new Date(iso).toLocaleDateString(locale === "he" ? "he-IL" : "ru-RU") : "—");

  const columns: Column<ProductRequestListItem>[] = [
    {
      key: "request_number",
      header: t.colNumber,
      render: (r) => (
        <button type="button" onClick={() => setSelectedId(r.id!)} className="numeric font-medium text-brass hover:underline">
          {r.request_number}
        </button>
      ),
    },
    { key: "status", header: t.colStatus, render: (r) => <StatusBadge status={r.status} /> },
    { key: "customer_name", header: t.colCustomer, render: (r) => <span className="text-navy">{r.customer_name}</span> },
    { key: "customer_phone", header: t.colPhone, render: (r) => <span className="numeric">{r.customer_phone}</span> },
    { key: "item_count", header: t.colItems, numeric: true, render: (r) => <span className="numeric">{r.item_count}</span> },
    {
      key: "assigned_manager_id",
      header: t.colManager,
      render: (r) => <AssigneeName id={r.assigned_manager_id} emptyLabel={t.unassigned} />,
    },
    { key: "created_at", header: t.colCreated, render: (r) => <span className="numeric">{dateFmt(r.created_at)}</span> },
  ];

  const data = listQuery.data;
  const metrics = metricsQuery.data;

  return (
    <div className="space-y-5">
      <h1 className="text-h2 font-bold text-navy">{t.title}</h1>

      {/* Метрики воронки */}
      <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <MetricCard label={t.metricTotal} value={metrics ? String(metrics.total ?? 0) : "—"} />
        <MetricCard
          label={t.metricCompletion}
          value={metrics?.completion_rate != null ? `${Math.round(metrics.completion_rate * 100)}%` : "—"}
        />
        {metrics &&
          REQUEST_STATUSES.filter((s) => (metrics.by_status as Record<string, number> | undefined)?.[s]).map((s) => (
            <MetricCard
              key={s}
              label={dict.enums.request_status[s]}
              value={String((metrics.by_status as Record<string, number>)[s])}
            />
          ))}
      </section>

      <FilterPanel title={t.filters} onReset={reset} resetLabel={dict.common.reset}>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.colStatus}
          <select
            value={filters.status ?? ""}
            onChange={(e) => patch({ status: (e.target.value || undefined) as ProductRequestFilters["status"] })}
            className="rounded-control border border-line px-3 py-2 text-ink"
          >
            <option value="">{t.statusAll}</option>
            {REQUEST_STATUSES.map((s) => (
              <option key={s} value={s}>
                {dict.enums.request_status[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {dict.assignees.label}
          <select
            value={filters.manager_id ?? ""}
            onChange={(e) => patch({ manager_id: e.target.value ? Number(e.target.value) : undefined })}
            className="rounded-control border border-line px-3 py-2 text-ink"
          >
            <option value="">{dict.assignees.filterAll}</option>
            {assignees.list.map((a) => (
              <option key={a.id} value={a.id}>{a.name}</option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.from}
          <input
            type="date"
            value={filters.from ?? ""}
            onChange={(e) => patch({ from: e.target.value || undefined })}
            className="rounded-control border border-line px-3 py-2 text-ink"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.to}
          <input
            type="date"
            value={filters.to ?? ""}
            onChange={(e) => patch({ to: e.target.value || undefined })}
            className="rounded-control border border-line px-3 py-2 text-ink"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {dict.common.search}
          <input
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
            placeholder={t.searchPlaceholder}
            className="min-w-[16rem] rounded-control border border-line px-3 py-2 text-ink"
          />
        </label>
        <label className="flex items-center gap-2 pb-2 text-sm text-ink">
          <input
            type="checkbox"
            checked={filters.in_stock ?? false}
            onChange={(e) => patch({ in_stock: e.target.checked || undefined })}
          />
          {t.inStockOnly}
        </label>
      </FilterPanel>

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        getRowKey={(r) => r.id!}
        rowClassName={(r) => (isStale(r) ? "bg-terracotta/5" : "")}
        labels={{
          page: dict.table.page,
          of: dict.table.of,
          rowsTotal: dict.table.rowsTotal,
          prev: dict.table.prev,
          next: dict.table.next,
          empty: dict.common.empty,
        }}
        meta={
          data
            ? { page: data.page ?? 0, size: data.size ?? PAGE_SIZE, totalElements: data.total_elements ?? 0, totalPages: data.total_pages ?? 0 }
            : undefined
        }
        onPageChange={setPage}
      />
      <p className="text-xs text-ink-soft">{t.staleHint}</p>

      {selectedId != null && (
        <ProductRequestDetailModal id={selectedId} onClose={() => setSelectedId(null)} onChanged={refresh} />
      )}
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-control border border-line bg-surface p-3">
      <div className="text-xs text-ink-soft">{label}</div>
      <div className="mt-1 text-h3 font-bold text-navy">{value}</div>
    </div>
  );
}
