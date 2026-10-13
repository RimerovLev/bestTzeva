import { useMemo, useState } from "react";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { Button } from "@/components/ui/Button";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { FilterPanel } from "@/components/ui/FilterPanel";
import { listCategories } from "@/features/catalog/api";
import {
  listInventory, lowStock, listWarehouses, exportInventory,
  INVENTORY_STATUSES, type InventoryFilters, type InventoryListItem, type LowStockItem,
} from "./api";
import { ReceiptDialog, WriteOffDialog, StocktakeDialog, TransferDialog, MovementsDialog, WarehousesDialog } from "./dialogs";

const PAGE_SIZE = 20;
type Op = "receipt" | "warehouses" | { kind: "writeoff" | "stocktake" | "transfer" | "movements"; row: InventoryListItem } | null;

export function InventoryPage() {
  const { user } = useAuth();
  const { dict, locale } = useI18n();
  const t = dict.inventory;
  const [tab, setTab] = useState<"stock" | "low">("stock");
  const [filters, setFilters] = useState<InventoryFilters>({});
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [op, setOp] = useState<Op>(null);

  const warehousesQuery = useQuery({ queryKey: ["warehouses"], queryFn: listWarehouses });
  const categoriesQuery = useQuery({ queryKey: ["categories", locale], queryFn: listCategories });
  const effective = { ...filters, q: q.trim() || undefined };
  const inventoryQuery = useQuery({ queryKey: ["inventory", effective, page], queryFn: () => listInventory(effective, page, PAGE_SIZE), placeholderData: keepPreviousData, enabled: tab === "stock" });
  const lowQuery = useQuery({ queryKey: ["low-stock"], queryFn: lowStock, enabled: tab === "low" });
  const lowRows = useMemo(() => [...(lowQuery.data ?? [])].sort((a, b) => (b.criticality ?? 0) - (a.criticality ?? 0)), [lowQuery.data]);

  if (!user || !canAccessPath(user.role, "/warehouse")) {
    return <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card"><h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1><p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p></div>;
  }

  const warehouses = warehousesQuery.data ?? [];
  const statusBadge = (s?: string) => (
    <span className={`rounded-pill px-2 py-0.5 text-xs ${s === "IN_STOCK" ? "bg-ok-bg text-ok-fg" : s === "LOW" ? "bg-warn-bg text-warn-fg" : "bg-crit-bg text-crit-fg"}`}>
      {t.statuses[(s ?? "OUT") as keyof typeof t.statuses]}
    </span>
  );

  const columns: Column<InventoryListItem>[] = [
    { key: "sku", header: t.colSku, render: (r) => <span className="numeric">{r.sku}</span> },
    { key: "product", header: t.colProduct, render: (r) => <span className="font-medium text-navy">{r.product_name}</span> },
    { key: "warehouse", header: t.colWarehouse, render: (r) => <span className="numeric">{r.warehouse_code}</span> },
    { key: "qty", header: t.colPhysical, numeric: true, render: (r) => <span className="numeric">{r.qty}</span> },
    { key: "reserved", header: t.colReserved, numeric: true, render: (r) => <span className="numeric text-warn-fg">{r.reserved_qty}</span> },
    { key: "available", header: t.colAvailable, numeric: true, render: (r) => <span className="numeric font-bold">{r.available}</span> },
    { key: "threshold", header: t.colThreshold, numeric: true, render: (r) => <span className="numeric text-ink-faint">{r.low_stock_threshold}</span> },
    { key: "status", header: t.colStatus, render: (r) => statusBadge(r.status) },
    {
      key: "actions", header: t.colActions, render: (r) => (
        <div className="flex gap-2 text-xs">
          <button type="button" onClick={() => setOp({ kind: "writeoff", row: r })} className="text-brass hover:underline">{t.writeOff}</button>
          <button type="button" onClick={() => setOp({ kind: "stocktake", row: r })} className="text-brass hover:underline">{t.stocktake}</button>
          <button type="button" onClick={() => setOp({ kind: "transfer", row: r })} className="text-brass hover:underline">{t.transfer}</button>
          <button type="button" onClick={() => setOp({ kind: "movements", row: r })} className="text-ink-soft hover:underline">{t.movements}</button>
        </div>
      ),
    },
  ];

  const lowColumns: Column<LowStockItem>[] = [
    { key: "sku", header: t.colSku, render: (r) => <span className="numeric">{r.sku}</span> },
    { key: "product", header: t.colProduct, render: (r) => <span className="font-medium text-navy">{r.product_name}</span> },
    { key: "warehouse", header: t.colWarehouse, render: (r) => <span className="numeric">{r.warehouse_code}</span> },
    { key: "available", header: t.colAvailable, numeric: true, render: (r) => <span className="numeric font-bold">{r.available}</span> },
    { key: "threshold", header: t.colThreshold, numeric: true, render: (r) => <span className="numeric text-ink-faint">{r.low_stock_threshold}</span> },
    { key: "crit", header: t.colCrit, numeric: true, render: (r) => <span className="numeric font-bold text-crit-fg">{r.criticality}</span> },
  ];

  const tableLabels = { page: dict.table.page, of: dict.table.of, rowsTotal: dict.table.rowsTotal, prev: dict.table.prev, next: dict.table.next, empty: t.empty };

  async function doExport() {
    const blob = await exportInventory(effective);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = "inventory.xlsx"; a.click();
    URL.revokeObjectURL(url);
  }

  const inv = inventoryQuery.data;

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-h1 font-bold text-navy">{t.title}</h1>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setOp("warehouses")}>{t.warehouses}</Button>
          <Button variant="outline" onClick={doExport}>{t.export}</Button>
          <Button variant="primary" disabled={warehouses.length === 0} onClick={() => setOp("receipt")}>{t.receipt}</Button>
        </div>
      </div>

      {warehouses.length === 0 && <p className="mb-4 rounded-control bg-warn-bg px-3 py-2 text-sm text-warn-fg">{t.needWarehouse}</p>}

      <div className="mb-4 flex gap-1">
        <TabBtn active={tab === "stock"} onClick={() => setTab("stock")} label={t.tabStock} />
        <TabBtn active={tab === "low"} onClick={() => setTab("low")} label={t.tabLow} />
      </div>

      {tab === "stock" ? (
        <>
          <div className="mb-4">
            <FilterPanel title={dict.catalog.filters} onReset={() => { setFilters({}); setQ(""); setPage(0); }} resetLabel={dict.common.reset}>
              <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder={t.search} aria-label={t.search} className="w-56 rounded-control border border-line-strong px-3 py-2 text-sm" />
              <select aria-label={t.colWarehouse} value={filters.warehouse_id ?? ""} onChange={(e) => { setFilters((f) => ({ ...f, warehouse_id: e.target.value ? Number(e.target.value) : undefined })); setPage(0); }} className="rounded-control border border-line-strong px-2 py-2 text-sm">
                <option value="">{t.allWarehouses}</option>
                {warehouses.map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
              </select>
              <select aria-label={dict.catalog.colCategory} value={filters.category_id ?? ""} onChange={(e) => { setFilters((f) => ({ ...f, category_id: e.target.value ? Number(e.target.value) : undefined })); setPage(0); }} className="rounded-control border border-line-strong px-2 py-2 text-sm">
                <option value="">{dict.catalog.anyCategory}</option>
                {(categoriesQuery.data ?? []).map((c) => <option key={c.id} value={c.id}>{c.name ?? c.slug}</option>)}
              </select>
              <select aria-label={t.colStatus} value={filters.status ?? ""} onChange={(e) => { setFilters((f) => ({ ...f, status: (e.target.value || undefined) as InventoryFilters["status"] })); setPage(0); }} className="rounded-control border border-line-strong px-2 py-2 text-sm">
                <option value="">{t.allStatuses}</option>
                {INVENTORY_STATUSES.map((s) => <option key={s} value={s}>{t.statuses[s]}</option>)}
              </select>
            </FilterPanel>
          </div>
          <DataTable
            columns={columns}
            rows={inv?.items ?? []}
            getRowKey={(r) => `${r.variant_id}-${r.warehouse_id}`}
            labels={tableLabels}
            meta={inv ? { page: inv.page ?? 0, size: inv.size ?? PAGE_SIZE, totalElements: inv.total_elements ?? 0, totalPages: inv.total_pages ?? 0 } : undefined}
            onPageChange={setPage}
            rowClassName={(r) => (r.status === "OUT" ? "bg-crit-bg/30" : r.status === "LOW" ? "bg-warn-bg/30" : "")}
          />
        </>
      ) : (
        <DataTable columns={lowColumns} rows={lowRows} getRowKey={(r) => `${r.variant_id}-${r.warehouse_id}`} labels={tableLabels} rowClassName={() => "bg-warn-bg/20"} />
      )}

      {op === "receipt" && <ReceiptDialog warehouses={warehouses} onClose={() => setOp(null)} />}
      {op === "warehouses" && <WarehousesDialog onClose={() => setOp(null)} />}
      {op && typeof op === "object" && op.kind === "writeoff" && <WriteOffDialog row={op.row} onClose={() => setOp(null)} />}
      {op && typeof op === "object" && op.kind === "stocktake" && <StocktakeDialog row={op.row} onClose={() => setOp(null)} />}
      {op && typeof op === "object" && op.kind === "transfer" && <TransferDialog row={op.row} warehouses={warehouses} onClose={() => setOp(null)} />}
      {op && typeof op === "object" && op.kind === "movements" && <MovementsDialog row={op.row} onClose={() => setOp(null)} />}
    </section>
  );
}

function TabBtn({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return <button type="button" onClick={onClick} className={`rounded-control px-4 py-2 text-sm ${active ? "bg-brass/10 font-medium text-navy" : "text-ink-soft hover:bg-paper-warm"}`}>{label}</button>;
}
