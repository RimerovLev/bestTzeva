import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { resolveErrorMessage } from "@/lib/api/messages";
import { listColors, createColor, updateColor, deleteColor, COLOR_SYSTEMS, type ColorSwatch, type ColorSwatchSaveRequest } from "./refApi";

type System = (typeof COLOR_SYSTEMS)[number];

export function ColorsAdminPage() {
  const { dict } = useI18n();
  const t = dict.colorsAdmin;
  const qc = useQueryClient();
  const [system, setSystem] = useState<System | "">("");
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<{ mode: "new" | "edit"; color?: ColorSwatch } | null>(null);
  const [toDelete, setToDelete] = useState<ColorSwatch | null>(null);

  const colorsQuery = useQuery({
    queryKey: ["admin-colors", system, q],
    queryFn: () => listColors({ system: system || undefined, q: q.trim() || undefined }),
  });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteColor(id),
    onSuccess: () => { setToDelete(null); qc.invalidateQueries({ queryKey: ["admin-colors"] }); },
  });

  return (
    <section>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-h1 font-bold text-navy">{t.title}</h1>
        <Button variant="primary" onClick={() => setEditing({ mode: "new" })}>{t.new}</Button>
      </div>

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="flex gap-1">
          <SysBtn active={system === ""} onClick={() => setSystem("")} label={t.allSystems} />
          {COLOR_SYSTEMS.map((s) => <SysBtn key={s} active={system === s} onClick={() => setSystem(s)} label={t.systems[s]} />)}
        </div>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t.searchCode} className="rounded-control border border-line-strong px-3 py-2 text-sm" />
      </div>

      {(colorsQuery.data ?? []).length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-12 text-center text-ink-faint shadow-card">{t.empty}</p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
          {(colorsQuery.data ?? []).map((col) => (
            <div key={col.id} className="overflow-hidden rounded-card border border-line bg-surface shadow-card">
              <div className="aspect-[4/3]" style={{ backgroundColor: col.hex ?? "var(--line)" }} />
              <div className="p-2">
                <p className="numeric text-sm font-medium text-navy">{col.code}</p>
                <p className="truncate text-xs text-ink-faint">{col.collection}</p>
                <div className="mt-1 flex gap-2">
                  <button type="button" onClick={() => setEditing({ mode: "edit", color: col })} className="text-xs text-brass hover:underline">{t.edit}</button>
                  <button type="button" onClick={() => setToDelete(col)} className="text-xs text-crit-fg hover:underline">{t.delete}</button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && <ColorForm mode={editing.mode} color={editing.color} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); qc.invalidateQueries({ queryKey: ["admin-colors"] }); }} />}

      <Modal open={toDelete != null} onClose={() => setToDelete(null)} title={t.delete}
        footer={<><Button variant="muted" onClick={() => setToDelete(null)}>{t.cancel}</Button><Button variant="primary" disabled={deleteMutation.isPending} onClick={() => toDelete?.id != null && deleteMutation.mutate(toDelete.id)}>{t.delete}</Button></>}>
        <p>{t.deleteConfirm}</p>
      </Modal>
    </section>
  );
}

function SysBtn({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return <button type="button" onClick={onClick} aria-pressed={active} className={`rounded-control border px-3 py-1.5 text-sm ${active ? "border-brass bg-brass/10 font-medium text-navy" : "border-line-strong text-ink-soft hover:bg-paper-warm"}`}>{label}</button>;
}

function ColorForm({ mode, color, onClose, onSaved }: { mode: "new" | "edit"; color?: ColorSwatch; onClose: () => void; onSaved: () => void }) {
  const { dict } = useI18n();
  const t = dict.colorsAdmin;
  const [code, setCode] = useState(color?.code ?? "");
  const [sys, setSys] = useState<System>((color?.system?.toUpperCase() as System) ?? "RAL");
  const [hex, setHex] = useState(color?.hex ?? "#cccccc");
  const [name, setName] = useState(color?.name ?? "");
  const [collection, setCollection] = useState(color?.collection ?? "");
  const [popular, setPopular] = useState(color?.popular ?? false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true); setError(null);
    const body: ColorSwatchSaveRequest = { code: code.trim(), system: sys, hex, collection: collection.trim() || undefined, popular, name_translations: name.trim() ? { he: name.trim() } : undefined };
    try {
      if (mode === "edit" && color?.id != null) await updateColor(color.id, body);
      else await createColor(body);
      onSaved();
    } catch (e) { setError(resolveErrorMessage(dict.errors, e).text); } finally { setBusy(false); }
  }

  return (
    <Modal open onClose={onClose} title={mode === "new" ? t.formNew : t.formEdit}
      footer={<><Button variant="muted" onClick={onClose}>{t.cancel}</Button><Button variant="primary" disabled={busy || !code.trim()} onClick={save}>{t.save}</Button></>}>
      <div className="grid grid-cols-2 gap-3 text-sm">
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.code}</span><input value={code} onChange={(e) => setCode(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.system}</span>
          <select value={sys} onChange={(e) => setSys(e.target.value as System)} className="rounded-control border border-line-strong px-2 py-2">{COLOR_SYSTEMS.map((s) => <option key={s} value={s}>{t.systems[s]}</option>)}</select>
        </label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.hex}</span><div className="flex gap-2"><input type="color" value={hex} onChange={(e) => setHex(e.target.value)} className="h-9 w-12 rounded border border-line-strong" /><input value={hex} onChange={(e) => setHex(e.target.value)} className="numeric flex-1 rounded-control border border-line-strong px-2 py-2" /></div></label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.name}</span><input value={name} onChange={(e) => setName(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex flex-col gap-1"><span className="text-ink-soft">{t.collection}</span><input value={collection} onChange={(e) => setCollection(e.target.value)} className="rounded-control border border-line-strong px-3 py-2" /></label>
        <label className="flex items-center gap-2 self-end text-ink-soft"><input type="checkbox" checked={popular} onChange={(e) => setPopular(e.target.checked)} className="accent-brass" />{t.popular}</label>
      </div>
      {error && <p className="mt-2 text-sm text-crit-fg">{error}</p>}
    </Modal>
  );
}
