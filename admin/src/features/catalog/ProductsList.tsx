import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/ui/Button";
import { DataTable, type Column, type SortState } from "@/components/ui/DataTable";
import { FilterPanel } from "@/components/ui/FilterPanel";
import { Modal } from "@/components/ui/Modal";
import { resolveErrorMessage } from "@/lib/api/messages";
import {
  listProducts,
  listCategories,
  setProductActive,
  deleteProduct,
  PAINT_TYPES,
  type ProductFilters,
  type ProductListItem,
} from "./api";

const PAGE_SIZE = 20;

export function ProductsList({ onNew, onBulk, onImport }: { onNew: () => void; onBulk: () => void; onImport: () => void }) {
  const { dict, locale } = useI18n();
  const c = dict.catalog;
  const qc = useQueryClient();

  const [filters, setFilters] = useState<ProductFilters>({});
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<SortState>({ field: "created_at", dir: "desc" });
  const [toDelete, setToDelete] = useState<ProductListItem | null>(null);

  const categoriesQuery = useQuery({ queryKey: ["categories", locale], queryFn: listCategories });
  const categoryName = useMemo(() => {
    const map = new Map<number, string>();
    for (const cat of categoriesQuery.data ?? []) if (cat.id != null) map.set(cat.id, cat.name ?? cat.slug ?? String(cat.id));
    return map;
  }, [categoriesQuery.data]);

  const effectiveFilters = { ...filters, q: q.trim() || undefined };
  const productsQuery = useQuery({
    queryKey: ["admin-products", locale, effectiveFilters, page],
    queryFn: () => listProducts(effectiveFilters, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  const activeMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setProductActive(id, active),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-products"] }),
  });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteProduct(id, true),
    onSuccess: () => {
      setToDelete(null);
      qc.invalidateQueries({ queryKey: ["admin-products"] });
    },
  });

  function patchFilter(patch: Partial<ProductFilters>) {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPage(0);
  }

  const columns: Column<ProductListItem>[] = [
    { key: "sku", header: c.colSku, render: (p) => <span className="numeric">{p.sku}</span> },
    { key: "name", header: c.colName, render: (p) => <span className="font-medium text-navy">{p.name}</span> },
    { key: "category", header: c.colCategory, render: (p) => categoryName.get(p.category_id ?? -1) ?? p.category_id },
    { key: "brand", header: c.colBrand, numeric: true, render: (p) => <span className="numeric">{p.brand_id}</span> },
    {
      key: "price",
      header: c.colPrice,
      numeric: true,
      render: (p) => <span className="numeric"><bdi>₪{p.min_price}–₪{p.max_price}</bdi></span>,
    },
    {
      key: "stock",
      header: c.colStock,
      render: (p) => (
        <span className={`rounded-pill px-2 py-0.5 text-xs ${p.has_stock ? "bg-ok-bg text-ok-fg" : "bg-paper-warm text-ink-soft"}`}>
          {p.has_stock ? c.stockYes : c.stockNo}
        </span>
      ),
    },
    {
      key: "active",
      header: c.colActive,
      render: (p) => (
        <span className={`rounded-pill px-2 py-0.5 text-xs ${p.active ? "bg-info-bg text-info-fg" : "bg-paper-warm text-ink-soft"}`}>
          {p.active ? c.active : c.inactive}
        </span>
      ),
    },
    {
      key: "actions",
      header: c.colActions,
      render: (p) => (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => p.id != null && activeMutation.mutate({ id: p.id, active: !p.active })}
            className="text-xs text-brass hover:underline"
          >
            {p.active ? c.deactivate : c.activate}
          </button>
          <button type="button" onClick={() => setToDelete(p)} className="text-xs text-crit-fg hover:underline">
            {c.delete}
          </button>
        </div>
      ),
    },
  ];

  const meta = productsQuery.data
    ? {
        page: productsQuery.data.page ?? 0,
        size: productsQuery.data.size ?? PAGE_SIZE,
        totalElements: productsQuery.data.total_elements ?? 0,
        totalPages: productsQuery.data.total_pages ?? 0,
      }
    : undefined;

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-h1 font-bold text-navy">{c.title}</h1>
        <div className="flex gap-2">
          <Button variant="outline" onClick={onImport}>{c.import}</Button>
          <Button variant="outline" onClick={onBulk}>{c.bulkTitle}</Button>
          <Button variant="primary" onClick={onNew}>{c.new}</Button>
        </div>
      </div>

      <div className="mb-4">
        <FilterPanel title={c.filters} onReset={() => { setFilters({}); setQ(""); setPage(0); }} resetLabel={dict.common.reset}>
          <input
            value={q}
            onChange={(e) => { setQ(e.target.value); setPage(0); }}
            placeholder={c.searchPlaceholder}
            className="w-64 rounded-control border border-line-strong bg-surface px-3 py-2 text-sm focus:border-brass focus:outline-none"
          />
          <select
            value={filters.category_id ?? ""}
            onChange={(e) => patchFilter({ category_id: e.target.value ? Number(e.target.value) : undefined })}
            className="rounded-control border border-line-strong bg-surface px-2 py-2 text-sm"
          >
            <option value="">{c.anyCategory}</option>
            {(categoriesQuery.data ?? []).map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name ?? cat.slug}</option>
            ))}
          </select>
          <select
            value={filters.paint_type ?? ""}
            onChange={(e) => patchFilter({ paint_type: (e.target.value || undefined) as ProductFilters["paint_type"] })}
            className="rounded-control border border-line-strong bg-surface px-2 py-2 text-sm"
          >
            <option value="">{c.anyType}</option>
            {PAINT_TYPES.map((t) => (
              <option key={t} value={t}>{dict.enums.paint_type[t]}</option>
            ))}
          </select>
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={filters.active ?? false} onChange={(e) => patchFilter({ active: e.target.checked || undefined })} className="accent-brass" />
            {c.activeOnly}
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={filters.in_stock ?? false} onChange={(e) => patchFilter({ in_stock: e.target.checked || undefined })} className="accent-brass" />
            {c.inStockOnly}
          </label>
        </FilterPanel>
      </div>

      <DataTable
        columns={columns}
        rows={productsQuery.data?.items ?? []}
        getRowKey={(p) => p.id ?? p.sku ?? ""}
        labels={{ page: dict.table.page, of: dict.table.of, rowsTotal: dict.table.rowsTotal, prev: dict.table.prev, next: dict.table.next, empty: dict.common.empty }}
        meta={meta}
        sort={sort}
        onSortChange={setSort}
        onPageChange={setPage}
      />

      <Modal
        open={toDelete != null}
        onClose={() => setToDelete(null)}
        title={c.deleteTitle}
        footer={
          <>
            <Button variant="muted" onClick={() => setToDelete(null)}>{c.cancel}</Button>
            <Button variant="primary" onClick={() => toDelete?.id != null && deleteMutation.mutate(toDelete.id)} disabled={deleteMutation.isPending}>
              {c.deleteHard}
            </Button>
          </>
        }
      >
        <p>{c.deleteBody}</p>
        {deleteMutation.isError && (
          <p className="mt-2 text-sm text-crit-fg">{resolveErrorMessage(dict.errors, deleteMutation.error).text}</p>
        )}
      </Modal>
    </section>
  );
}
