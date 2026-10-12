import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { resolveErrorMessage } from "@/lib/api/messages";
import {
  receipt, writeOff, stocktake, transfer, variantMovements,
  listWarehouses, createWarehouse, setWarehouseActive,
  type InventoryListItem, type WarehouseResponse,
} from "./api";

type Row = InventoryListItem;

function useInventoryInvalidate() {
  const qc = useQueryClient();
  return () => { qc.invalidateQueries({ queryKey: ["inventory"] }); qc.invalidateQueries({ queryKey: ["low-stock"] }); };
}

// ── Приход (массовый) ────────────────────────────────────────────────────────
export function ReceiptDialog({ warehouses, onClose }: { warehouses: WarehouseResponse[]; onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.inventory;
  const invalidate = useInventoryInvalidate();
  const [warehouseId, setWarehouseId] = useState<string>(String(warehouses[0]?.id ?? ""));
  const [lines, setLines] = useState<{ sku: string; qty: string }[]>([{ sku: "", qty: "" }]);
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const m = useMutation({
    mutationFn: () => receipt({
      warehouse_id: Number(warehouseId),
      comment: comment || undefined,
      lines: lines.filter((l) => l.sku.trim() && l.qty).map((l) => ({ sku: l.sku.trim(), qty: Number(l.qty) })),
    }),
    onSuccess: () => { invalidate(); onClose(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });

  return (
    <Modal open onClose={onClose} title={t.receiptTitle}
      footer={<><Button variant="muted" onClick={onClose}>{t.cancel}</Button><Button variant="primary" disabled={m.isPending || !warehouseId} onClick={() => m.mutate()}>{m.isPending ? t.applying : t.apply}</Button></>}>
      <div className="space-y-3 text-sm">
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.receiptWarehouse}</span>
          <select value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} className="rounded-control border border-line-strong px-2 py-2">
            {warehouses.map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
          </select>
        </label>
        <div>
          <div className="mb-1 flex items-center justify-between"><span className="text-ink-soft">{t.receiptLines}</span><button type="button" onClick={() => setLines((l) => [...l, { sku: "", qty: "" }])} className="text-xs text-brass hover:underline">{t.addLine}</button></div>
          {lines.map((l, i) => (
            <div key={i} className="mb-1 flex gap-2">
              <input value={l.sku} onChange={(e) => setLines((ls) => ls.map((x, j) => j === i ? { ...x, sku: e.target.value } : x))} placeholder={t.sku} className="flex-1 rounded-control border border-line-strong px-2 py-1.5" />
              <input type="number" value={l.qty} onChange={(e) => setLines((ls) => ls.map((x, j) => j === i ? { ...x, qty: e.target.value } : x))} placeholder={t.qty} className="w-24 rounded-control border border-line-strong px-2 py-1.5" />
              {lines.length > 1 && <button type="button" onClick={() => setLines((ls) => ls.filter((_, j) => j !== i))} className="text-crit-fg">✕</button>}
            </div>
          ))}
        </div>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.comment}</span><input value={comment} onChange={(e) => setComment(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        {error && <p className="text-crit-fg">{error}</p>}
      </div>
    </Modal>
  );
}

// ── Списание ─────────────────────────────────────────────────────────────────
export function WriteOffDialog({ row, onClose }: { row: Row; onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.inventory;
  const invalidate = useInventoryInvalidate();
  const [qty, setQty] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const m = useMutation({
    mutationFn: () => writeOff({ variant_id: row.variant_id!, warehouse_id: row.warehouse_id!, qty: Number(qty), reason: reason.trim() }),
    onSuccess: () => { invalidate(); onClose(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  return (
    <Modal open onClose={onClose} title={`${t.writeOffTitle} · ${row.sku}`}
      footer={<><Button variant="muted" onClick={onClose}>{t.cancel}</Button><Button variant="primary" disabled={m.isPending || !qty || !reason.trim()} onClick={() => m.mutate()}>{m.isPending ? t.applying : t.apply}</Button></>}>
      <div className="space-y-3 text-sm">
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.qty}</span><input type="number" value={qty} onChange={(e) => setQty(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.reason}</span><input value={reason} onChange={(e) => setReason(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        {error && <p className="text-crit-fg">{error}</p>}
      </div>
    </Modal>
  );
}

// ── Инвентаризация (с предпросмотром расхождения) ────────────────────────────
export function StocktakeDialog({ row, onClose }: { row: Row; onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.inventory;
  const invalidate = useInventoryInvalidate();
  const current = row.qty ?? 0;
  const reserved = row.reserved_qty ?? 0;
  const [counted, setCounted] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const countedNum = counted === "" ? null : Number(counted);
  const discrepancy = countedNum == null ? null : countedNum - current;
  const belowReserved = countedNum != null && countedNum < reserved;
  const m = useMutation({
    mutationFn: () => stocktake({ variant_id: row.variant_id!, warehouse_id: row.warehouse_id!, counted_qty: Number(counted), reason: reason.trim() }),
    onSuccess: () => { invalidate(); onClose(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  return (
    <Modal open onClose={onClose} title={`${t.stocktakeTitle} · ${row.sku}`}
      footer={<><Button variant="muted" onClick={onClose}>{t.cancel}</Button><Button variant="primary" disabled={m.isPending || counted === "" || belowReserved} onClick={() => m.mutate()}>{m.isPending ? t.applying : t.apply}</Button></>}>
      <div className="space-y-3 text-sm">
        <div className="flex gap-6">
          <div><span className="text-ink-soft">{t.currentQty}</span><p className="numeric text-h3 font-bold text-navy">{current}</p></div>
          <div><span className="text-ink-soft">{t.colReserved}</span><p className="numeric text-h3 font-bold text-warn-fg">{reserved}</p></div>
        </div>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.countedQty}</span><input type="number" value={counted} onChange={(e) => setCounted(e.target.value)} className="numeric rounded-control border border-line-strong px-3 py-2" /></label>
        {discrepancy != null && (
          <p className="rounded-control bg-paper-warm px-3 py-2">
            {t.discrepancy}: <span className={`numeric font-bold ${discrepancy === 0 ? "text-ink" : discrepancy > 0 ? "text-ok-fg" : "text-crit-fg"}`}>{discrepancy > 0 ? "+" : ""}{discrepancy}</span>
          </p>
        )}
        {belowReserved && <p className="rounded-control bg-crit-bg px-3 py-2 text-crit-fg">{t.belowReserved.replace("{reserved}", String(reserved))}</p>}
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.reason}</span><input value={reason} onChange={(e) => setReason(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        {error && <p className="text-crit-fg">{error}</p>}
      </div>
    </Modal>
  );
}

// ── Перемещение ──────────────────────────────────────────────────────────────
export function TransferDialog({ row, warehouses, onClose }: { row: Row; warehouses: WarehouseResponse[]; onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.inventory;
  const invalidate = useInventoryInvalidate();
  const others = warehouses.filter((w) => w.id !== row.warehouse_id);
  const [toId, setToId] = useState<string>(String(others[0]?.id ?? ""));
  const [qty, setQty] = useState("");
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const m = useMutation({
    mutationFn: () => transfer({ variant_id: row.variant_id!, from_warehouse_id: row.warehouse_id!, to_warehouse_id: Number(toId), qty: Number(qty), comment: comment || undefined }),
    onSuccess: () => { invalidate(); onClose(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  return (
    <Modal open onClose={onClose} title={`${t.transferTitle} · ${row.sku}`}
      footer={<><Button variant="muted" onClick={onClose}>{t.cancel}</Button><Button variant="primary" disabled={m.isPending || !toId || !qty} onClick={() => m.mutate()}>{m.isPending ? t.applying : t.apply}</Button></>}>
      <div className="space-y-3 text-sm">
        <p className="text-ink-soft">{t.fromWarehouse}: <b className="numeric text-navy">{row.warehouse_code}</b></p>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.toWarehouse}</span>
          <select value={toId} onChange={(e) => setToId(e.target.value)} className="rounded-control border border-line-strong px-2 py-2">
            {others.map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.qty}</span><input type="number" value={qty} onChange={(e) => setQty(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.comment}</span><input value={comment} onChange={(e) => setComment(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        {error && <p className="text-crit-fg">{error}</p>}
      </div>
    </Modal>
  );
}

// ── Журнал движений ──────────────────────────────────────────────────────────
export function MovementsDialog({ row, onClose }: { row: Row; onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.inventory;
  const query = useQuery({ queryKey: ["movements", row.variant_id], queryFn: () => variantMovements(row.variant_id!) });
  const movements = query.data?.movements?.items ?? [];
  const reservations = query.data?.active_reservations ?? [];
  return (
    <Modal open onClose={onClose} title={`${t.movementsTitle} · ${row.sku}`}
      footer={<Button variant="muted" onClick={onClose}>{dict.common.close}</Button>}>
      {movements.length === 0 ? <p className="text-ink-faint">{t.noMovements}</p> : (
        <div className="max-h-64 overflow-y-auto rounded-control border border-line">
          <table className="w-full text-xs">
            <thead><tr className="bg-paper-warm text-ink-soft"><th className="px-2 py-1.5 text-start">{t.mType}</th><th className="px-2 py-1.5 text-end">{t.mDelta}</th><th className="px-2 py-1.5 text-end">{t.mAfter}</th><th className="px-2 py-1.5 text-start">{t.mRef}</th><th className="px-2 py-1.5 text-start">{t.mAuthor}</th></tr></thead>
            <tbody>
              {movements.map((mv) => (
                <tr key={mv.id} className="border-t border-line/60">
                  <td className="px-2 py-1.5">{mv.type}</td>
                  <td className="numeric px-2 py-1.5 text-end">{mv.qty_delta}</td>
                  <td className="numeric px-2 py-1.5 text-end">{mv.qty_after}</td>
                  <td className="px-2 py-1.5">{mv.reference_type} {mv.reference_id}</td>
                  <td className="numeric px-2 py-1.5">{mv.created_by}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {reservations.length > 0 && (
        <div className="mt-3">
          <p className="mb-1 text-sm font-semibold text-warn-fg">{t.activeReservations}</p>
          <ul className="text-xs text-ink-soft">
            {reservations.map((r, i) => <li key={i} className="numeric">{JSON.stringify(r)}</li>)}
          </ul>
        </div>
      )}
    </Modal>
  );
}

// ── Управление складами ──────────────────────────────────────────────────────
export function WarehousesDialog({ onClose }: { onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.inventory;
  const qc = useQueryClient();
  const query = useQuery({ queryKey: ["warehouses"], queryFn: listWarehouses });
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [error, setError] = useState<string | null>(null);
  const invalidate = () => { qc.invalidateQueries({ queryKey: ["warehouses"] }); qc.invalidateQueries({ queryKey: ["inventory"] }); };

  const createM = useMutation({
    mutationFn: () => createWarehouse({ code: code.trim(), name_translations: name.trim() ? { he: name.trim() } : undefined, address: address.trim() || undefined }),
    onSuccess: () => { setCode(""); setName(""); setAddress(""); invalidate(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  const activeM = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setWarehouseActive(id, active),
    onSuccess: invalidate,
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });

  return (
    <Modal open onClose={onClose} title={t.whTitle} footer={<Button variant="muted" onClick={onClose}>{dict.common.close}</Button>}>
      <div className="space-y-2">
        {(query.data ?? []).map((w) => (
          <div key={w.id} className="flex items-center justify-between rounded-control border border-line px-3 py-2 text-sm">
            <span><b className="numeric">{w.code}</b> {w.name_translations?.he ?? ""} {!w.active && <span className="text-ink-faint">(×)</span>}</span>
            <button type="button" onClick={() => w.id != null && activeM.mutate({ id: w.id, active: !w.active })} className="text-xs text-brass hover:underline">{w.active ? t.whDeactivate : t.whActive}</button>
          </div>
        ))}
      </div>
      <div className="mt-4 rounded-control bg-paper-warm p-3">
        <p className="mb-2 text-sm font-semibold text-navy">{t.whNew}</p>
        <div className="grid grid-cols-3 gap-2 text-sm">
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder={t.whCode} className="rounded-control border border-line-strong px-2 py-1.5" />
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t.whName} className="rounded-control border border-line-strong px-2 py-1.5" />
          <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder={t.whAddress} className="rounded-control border border-line-strong px-2 py-1.5" />
        </div>
        <div className="mt-2 flex justify-end"><Button variant="primary" disabled={createM.isPending || !code.trim()} onClick={() => createM.mutate()}>{t.save}</Button></div>
      </div>
      {error && <p className="mt-2 text-sm text-crit-fg">{error}</p>}
    </Modal>
  );
}
