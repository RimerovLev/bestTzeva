import { useState } from "react";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { FilterPanel } from "@/components/ui/FilterPanel";
import { StatusBadge } from "./StatusBadge";
import { ServiceRequestDetailModal } from "./ServiceRequestDetailModal";
import { PricingRulesPanel } from "./PricingRulesPanel";
import { AssigneeName } from "@/features/shared/assignees";
import { useAssignees } from "@/features/shared/useAssignees";
import {
  listServiceRequests,
  getServiceRequestMetrics,
  SERVICE_STATUSES,
  PROPERTY_TYPES,
  upper,
  type ServiceRequestFilters,
  type ServiceRequestListItem,
} from "./api";

const PAGE_SIZE = 20;

export function ServiceRequestsPage() {
  const { dict, locale } = useI18n();
  const t = dict.serviceRequests;
  const [tab, setTab] = useState<"requests" | "pricing">("requests");

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 border-b border-line">
        <TabButton active={tab === "requests"} onClick={() => setTab("requests")}>
          {t.tabRequests}
        </TabButton>
        <TabButton active={tab === "pricing"} onClick={() => setTab("pricing")}>
          {t.tabPricing}
        </TabButton>
      </div>
      {tab === "requests" ? <RequestsTab dict={dict} locale={locale} /> : <PricingRulesPanel />}
    </div>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
        active ? "border-brass text-navy" : "border-transparent text-ink-soft hover:text-ink"
      }`}
    >
      {children}
    </button>
  );
}

function RequestsTab({ dict, locale }: { dict: ReturnType<typeof useI18n>["dict"]; locale: string }) {
  const t = dict.serviceRequests;
  const [filters, setFilters] = useState<ServiceRequestFilters>({});
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const assignees = useAssignees();

  const effective = { ...filters, q: q.trim() || undefined };
  const listQuery = useQuery({
    queryKey: ["admin-service-requests", locale, effective, page],
    queryFn: () => listServiceRequests(effective, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });
  const metricsQuery = useQuery({ queryKey: ["admin-service-requests-metrics", locale], queryFn: getServiceRequestMetrics });

  function patch(p: Partial<ServiceRequestFilters>) {
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

  const columns: Column<ServiceRequestListItem>[] = [
    {
      key: "request_number",
      header: t.colNumber,
      render: (r) => (
        <button type="button" onClick={() => setSelectedId(r.id!)} className="numeric font-medium text-brass hover:underline">
          {r.request_number}
        </button>
      ),
    },
    { key: "customer_name", header: t.colCustomer, render: (r) => <span className="text-navy">{r.customer_name}</span> },
    {
      key: "property_type",
      header: t.colProperty,
      render: (r) => dict.enums.property_type[upper(r.property_type) as keyof typeof dict.enums.property_type] ?? r.property_type,
    },
    { key: "city", header: t.colCity, render: (r) => r.city ?? "—" },
    { key: "area_sqm", header: t.colArea, numeric: true, render: (r) => <span className="numeric">{r.area_sqm ?? "—"}</span> },
    { key: "status", header: t.colStatus, render: (r) => <StatusBadge status={r.status} /> },
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

      <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <MetricCard label={t.metricTotal} value={metrics ? String(metrics.total ?? 0) : "—"} />
        {metrics &&
          SERVICE_STATUSES.filter((s) => (metrics.by_status as Record<string, number> | undefined)?.[s]).map((s) => (
            <MetricCard key={s} label={dict.enums.service_status[s]} value={String((metrics.by_status as Record<string, number>)[s])} />
          ))}
      </section>

      <FilterPanel title={t.filters} onReset={reset} resetLabel={dict.common.reset}>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.colStatus}
          <select
            value={filters.status ?? ""}
            onChange={(e) => patch({ status: (e.target.value || undefined) as ServiceRequestFilters["status"] })}
            className="rounded-control border border-line px-3 py-2 text-ink"
          >
            <option value="">{t.statusAll}</option>
            {SERVICE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {dict.enums.service_status[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.propertyType}
          <select
            value={filters.property_type ?? ""}
            onChange={(e) => patch({ property_type: (e.target.value || undefined) as ServiceRequestFilters["property_type"] })}
            className="rounded-control border border-line px-3 py-2 text-ink"
          >
            <option value="">{t.propertyAll}</option>
            {PROPERTY_TYPES.map((p) => (
              <option key={p} value={p}>
                {dict.enums.property_type[p]}
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
          {t.city}
          <input value={filters.city ?? ""} onChange={(e) => patch({ city: e.target.value || undefined })} className="rounded-control border border-line px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.areaMin}
          <input type="number" value={filters.area_min ?? ""} onChange={(e) => patch({ area_min: e.target.value ? Number(e.target.value) : undefined })} className="w-24 rounded-control border border-line px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.areaMax}
          <input type="number" value={filters.area_max ?? ""} onChange={(e) => patch({ area_max: e.target.value ? Number(e.target.value) : undefined })} className="w-24 rounded-control border border-line px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.from}
          <input type="date" value={filters.from ?? ""} onChange={(e) => patch({ from: e.target.value || undefined })} className="rounded-control border border-line px-3 py-2 text-ink" />
        </label>
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.to}
          <input type="date" value={filters.to ?? ""} onChange={(e) => patch({ to: e.target.value || undefined })} className="rounded-control border border-line px-3 py-2 text-ink" />
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
            className="min-w-[14rem] rounded-control border border-line px-3 py-2 text-ink"
          />
        </label>
      </FilterPanel>

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
          empty: dict.common.empty,
        }}
        meta={data ? { page: data.page ?? 0, size: data.size ?? PAGE_SIZE, totalElements: data.total_elements ?? 0, totalPages: data.total_pages ?? 0 } : undefined}
        onPageChange={setPage}
      />

      {selectedId != null && <ServiceRequestDetailModal id={selectedId} onClose={() => setSelectedId(null)} onChanged={refresh} />}
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
