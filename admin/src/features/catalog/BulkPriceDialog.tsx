import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { resolveErrorMessage } from "@/lib/api/messages";
import { listProducts, listCategories, bulkPrice, PAINT_TYPES, type ProductFilters, type BulkPriceRequest } from "./api";

export function BulkPriceDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { dict } = useI18n();
  const c = dict.catalog;
  const qc = useQueryClient();
  const categoriesQuery = useQuery({ queryKey: ["categories"], queryFn: listCategories, enabled: open });

  const [filters, setFilters] = useState<ProductFilters>({});
  const [percent, setPercent] = useState("");
  const [affected, setAffected] = useState<number | null>(null);
  const [applied, setApplied] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reset() {
    setFilters({});
    setPercent("");
    setAffected(null);
    setApplied(null);
    setError(null);
  }

  // Отдельного dry-run у бэкенда нет — предпросмотр считает подходящие товары через список.
  async function preview() {
    setError(null);
    setApplied(null);
    try {
      const page = await listProducts(filters, 0, 1);
      setAffected(page.total_elements ?? 0);
    } catch (err) {
      setError(resolveErrorMessage(dict.errors, err).text);
    }
  }

  async function apply() {
    setBusy(true);
    setError(null);
    try {
      const body: BulkPriceRequest = {
        brand_id: filters.brand_id,
        category_id: filters.category_id,
        paint_type: filters.paint_type,
        percent: Number(percent),
      };
      const count = await bulkPrice(body);
      setApplied(count);
      setAffected(null);
      qc.invalidateQueries({ queryKey: ["admin-products"] });
    } catch (err) {
      setError(resolveErrorMessage(dict.errors, err).text);
    } finally {
      setBusy(false);
    }
  }

  const canApply = percent !== "" && Number.isFinite(Number(percent)) && affected != null;

  return (
    <Modal
      open={open}
      onClose={() => { reset(); onClose(); }}
      title={c.bulkTitle}
      footer={
        <>
          <Button variant="muted" onClick={() => { reset(); onClose(); }}>{c.cancel}</Button>
          <Button variant="outline" onClick={preview}>{c.bulkPreview}</Button>
          <Button variant="primary" onClick={apply} disabled={!canApply || busy}>{busy ? c.bulkApplying : c.bulkApply}</Button>
        </>
      }
    >
      <p className="text-sm text-ink-soft">{c.bulkHint}</p>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-ink-soft">{c.category}</span>
          <select value={filters.category_id ?? ""} onChange={(e) => { setFilters((f) => ({ ...f, category_id: e.target.value ? Number(e.target.value) : undefined })); setAffected(null); }} className="rounded-control border border-line-strong px-2 py-1.5">
            <option value="">{c.anyCategory}</option>
            {(categoriesQuery.data ?? []).map((cat) => <option key={cat.id} value={cat.id}>{cat.name ?? cat.slug}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-ink-soft">{c.paintType}</span>
          <select value={filters.paint_type ?? ""} onChange={(e) => { setFilters((f) => ({ ...f, paint_type: (e.target.value || undefined) as ProductFilters["paint_type"] })); setAffected(null); }} className="rounded-control border border-line-strong px-2 py-1.5">
            <option value="">{c.anyType}</option>
            {PAINT_TYPES.map((t) => <option key={t} value={t}>{dict.enums.paint_type[t]}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-ink-soft">{c.brand}</span>
          <input type="number" value={filters.brand_id ?? ""} onChange={(e) => { setFilters((f) => ({ ...f, brand_id: e.target.value ? Number(e.target.value) : undefined })); setAffected(null); }} className="rounded-control border border-line-strong px-2 py-1.5" />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-ink-soft">{c.bulkPercent}</span>
          <input type="number" step="0.1" value={percent} onChange={(e) => setPercent(e.target.value)} className="numeric rounded-control border border-line-strong px-2 py-1.5" />
        </label>
      </div>

      {affected != null && (
        <p className="mt-4 rounded-control bg-warn-bg px-3 py-2 text-sm text-warn-fg">
          {c.bulkAffected.replace("{count}", String(affected))}
        </p>
      )}
      {applied != null && (
        <p className="mt-4 rounded-control bg-ok-bg px-3 py-2 text-sm text-ok-fg">
          {c.bulkApplied.replace("{count}", String(applied))}
        </p>
      )}
      {error && <p className="mt-3 text-sm text-crit-fg">{error}</p>}
    </Modal>
  );
}
