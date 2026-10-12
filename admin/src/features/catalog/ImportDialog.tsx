import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { resolveErrorMessage } from "@/lib/api/messages";
import { importProducts, type ImportResult } from "./refApi";

const TEMPLATE_HEADERS = "product_sku,category_slug,brand_slug,paint_type,usage_area,finish,base_type,name,variant_sku,color_code,volume_liters,price";
const TEMPLATE_SAMPLE = "TAM-INT-99,interior-walls,tambour,INTERIOR,INDOOR,MATTE,WATER,צבע פנים,TAM-INT-99-W,RAL9010,2.5,89.90";

export function ImportDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { dict } = useI18n();
  const t = dict.importDlg;
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [busy, setBusy] = useState<"dry" | "apply" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [applied, setApplied] = useState(false);

  function close() {
    setFile(null); setResult(null); setError(null); setApplied(false);
    onClose();
  }

  function downloadTemplate() {
    const blob = new Blob([`${TEMPLATE_HEADERS}\n${TEMPLATE_SAMPLE}\n`], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = "catalog-import-template.csv"; a.click();
    URL.revokeObjectURL(url);
  }

  async function run(dryRun: boolean) {
    if (!file) return;
    setBusy(dryRun ? "dry" : "apply"); setError(null);
    try {
      const res = await importProducts(file, dryRun);
      setResult(res);
      if (!dryRun) { setApplied(true); qc.invalidateQueries({ queryKey: ["admin-products"] }); }
    } catch (e) {
      setError(resolveErrorMessage(dict.errors, e).text);
    } finally { setBusy(null); }
  }

  const dryRunOk = result?.dry_run === true && (result.errors?.length ?? 0) === 0;

  return (
    <Modal open={open} onClose={close} title={t.title}
      footer={
        <>
          <Button variant="muted" onClick={close}>{dict.catalog.cancel}</Button>
          <Button variant="outline" disabled={!file || busy != null} onClick={() => run(true)}>{busy === "dry" ? t.dryRunning : t.dryRun}</Button>
          <Button variant="primary" disabled={!dryRunOk || busy != null || applied} onClick={() => run(false)}>{busy === "apply" ? t.applying : t.apply}</Button>
        </>
      }>
      <p className="text-sm text-ink-soft">{t.note}</p>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <input
          ref={fileRef}
          type="file"
          accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          onChange={(e) => { setFile(e.target.files?.[0] ?? null); setResult(null); setApplied(false); }}
          className="text-sm"
        />
        <button type="button" onClick={downloadTemplate} className="text-sm text-brass hover:underline">{t.template}</button>
      </div>

      {error && <p className="mt-3 text-sm text-crit-fg">{error}</p>}

      {result && (
        <div className="mt-4">
          <div className="flex gap-4 text-sm">
            <Stat label={t.created} value={result.created ?? 0} className="text-ok-fg" />
            <Stat label={t.updated} value={result.updated ?? 0} className="text-info-fg" />
            <Stat label={t.skipped} value={result.skipped ?? 0} className="text-ink-soft" />
          </div>

          {(result.errors?.length ?? 0) > 0 && (
            <div className="mt-3">
              <p className="mb-1 text-sm font-semibold text-crit-fg">{t.errorsTitle}</p>
              <div className="max-h-48 overflow-y-auto rounded-control border border-line">
                <table className="w-full text-sm">
                  <thead><tr className="bg-paper-warm text-ink-soft"><th className="px-3 py-1.5 text-start">{t.row}</th><th className="px-3 py-1.5 text-start">{t.reason}</th></tr></thead>
                  <tbody>
                    {(result.errors ?? []).map((er, i) => (
                      <tr key={i} className="border-t border-line/60">
                        <td className="numeric px-3 py-1.5">{er.row}</td>
                        <td className="px-3 py-1.5 text-crit-fg">{er.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {dryRunOk && !applied && <p className="mt-3 rounded-control bg-ok-bg px-3 py-2 text-sm text-ok-fg">{t.dryRunOk}</p>}
          {applied && <p className="mt-3 rounded-control bg-ok-bg px-3 py-2 text-sm text-ok-fg">{t.appliedOk}</p>}
        </div>
      )}
    </Modal>
  );
}

function Stat({ label, value, className }: { label: string; value: number; className: string }) {
  return (
    <div className="rounded-control bg-paper-warm px-3 py-2 text-center">
      <p className={`numeric text-h3 font-bold ${className}`}>{value}</p>
      <p className="text-xs text-ink-soft">{label}</p>
    </div>
  );
}
