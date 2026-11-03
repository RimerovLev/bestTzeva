import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { resolveErrorMessage } from "@/lib/api/messages";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  MEDIA_KINDS,
  PROPERTY_TYPES,
  WORK_TYPES,
  createPortfolio,
  deletePortfolio,
  deletePortfolioMedia,
  getPortfolio,
  listPortfolio,
  setPortfolioPublished,
  updatePortfolio,
  uploadPortfolioMedia,
  type MediaKind,
  type PortfolioAdminView,
  type PortfolioSave,
  type PropertyType,
  type WorkType,
} from "./api";

type PortfolioDict = typeof import("@/dictionaries/he.json")["portfolio"];

/** Портфолио (MANAGER/ADMIN): список проектов и редактор с фото «до/после». */
export function PortfolioPage() {
  const { user } = useAuth();
  const { dict } = useI18n();
  const t = dict.portfolio;
  const allowed = !!user && canAccessPath(user.role, "/portfolio");
  const [editing, setEditing] = useState<number | "new" | null>(null);

  if (editing !== null) {
    return (
      <PortfolioEditor
        key={String(editing)}
        id={editing}
        t={t}
        errors={dict.errors}
        onClose={() => setEditing(null)}
        onCreated={(id) => setEditing(id)}
      />
    );
  }
  return <PortfolioList t={t} errors={dict.errors} allowed={allowed} onEdit={setEditing} onNew={() => setEditing("new")} />;
}

function PortfolioList({
  t,
  errors,
  allowed,
  onEdit,
  onNew,
}: {
  t: PortfolioDict;
  errors: Record<string, string>;
  allowed: boolean;
  onEdit: (id: number) => void;
  onNew: () => void;
}) {
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const q = useQuery({ queryKey: ["admin-portfolio"], queryFn: listPortfolio, enabled: allowed });
  const delM = useMutation({
    mutationFn: (id: number) => deletePortfolio(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-portfolio"] }),
    onError: (e) => setError(resolveErrorMessage(errors, e).text),
  });

  return (
    <div className="space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">{t.title}</h1>
        <Button onClick={onNew}>{t.new}</Button>
      </header>
      {error && <p className="text-sm text-crit-fg">{error}</p>}
      {q.isLoading ? (
        <Skeleton className="h-40" />
      ) : q.isError ? (
        <p className="text-sm text-crit-fg">{t.loadError}</p>
      ) : (q.data ?? []).length === 0 ? (
        <p className="text-sm text-ink-soft">{t.empty}</p>
      ) : (
        <div className="overflow-hidden rounded-card border border-line">
          <table className="w-full text-sm">
            <tbody>
              {(q.data ?? []).map((p) => (
                <tr key={p.id} className="border-b border-line last:border-0">
                  <td className="px-3 py-2 font-medium">{p.title ?? p.slug}</td>
                  <td className="px-3 py-2 text-ink-soft">{p.city ?? ""}</td>
                  <td className="px-3 py-2">
                    <span
                      className={`rounded-control px-2 py-0.5 text-xs ${
                        p.published ? "bg-mint/30 text-ink" : "bg-line/50 text-ink-soft"
                      }`}
                    >
                      {p.published ? t.published : t.draft}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-ink-soft">{p.media_count}</td>
                  <td className="px-3 py-2">
                    <div className="flex justify-end gap-2">
                      <Button variant="outline" onClick={() => onEdit(p.id)}>
                        {t.edit}
                      </Button>
                      <Button
                        variant="muted"
                        onClick={() => {
                          if (window.confirm(t.confirmDelete)) delM.mutate(p.id);
                        }}
                      >
                        {t.delete}
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

interface FormState {
  slug: string;
  propertyType: PropertyType | "";
  city: string;
  areaSqm: string;
  durationDays: string;
  completedAt: string;
  workTypes: Set<WorkType>;
  heTitle: string;
  heDescription: string;
  ruTitle: string;
  ruDescription: string;
}

function emptyForm(): FormState {
  return {
    slug: "",
    propertyType: "",
    city: "",
    areaSqm: "",
    durationDays: "",
    completedAt: "",
    workTypes: new Set(),
    heTitle: "",
    heDescription: "",
    ruTitle: "",
    ruDescription: "",
  };
}

function fromView(v: PortfolioAdminView): FormState {
  const he = v.translations.find((x) => x.locale === "he");
  const ru = v.translations.find((x) => x.locale === "ru");
  return {
    slug: v.slug,
    propertyType: v.property_type ?? "",
    city: v.city ?? "",
    areaSqm: v.area_sqm != null ? String(v.area_sqm) : "",
    durationDays: v.duration_days != null ? String(v.duration_days) : "",
    completedAt: v.completed_at ?? "",
    workTypes: new Set(v.work_types),
    heTitle: he?.title ?? "",
    heDescription: he?.description ?? "",
    ruTitle: ru?.title ?? "",
    ruDescription: ru?.description ?? "",
  };
}

function toSave(f: FormState): PortfolioSave {
  const translations = [
    { locale: "he", title: f.heTitle.trim(), description: f.heDescription.trim() || null, meta_title: null, meta_description: null },
  ];
  if (f.ruTitle.trim()) {
    translations.push({
      locale: "ru",
      title: f.ruTitle.trim(),
      description: f.ruDescription.trim() || null,
      meta_title: null,
      meta_description: null,
    });
  }
  return {
    slug: f.slug.trim(),
    property_type: f.propertyType || null,
    city: f.city.trim() || null,
    area_sqm: f.areaSqm ? Number(f.areaSqm) : null,
    duration_days: f.durationDays ? Number(f.durationDays) : null,
    completed_at: f.completedAt || null,
    work_types: [...f.workTypes],
    translations,
  };
}

const INPUT = "rounded-control border border-line-strong bg-surface px-3 py-2 text-body text-ink";
const LABEL = "flex flex-col gap-1 text-sm text-ink-soft";

function PortfolioEditor({
  id,
  t,
  errors,
  onClose,
  onCreated,
}: {
  id: number | "new";
  t: PortfolioDict;
  errors: Record<string, string>;
  onClose: () => void;
  onCreated: (id: number) => void;
}) {
  const qc = useQueryClient();
  const isNew = id === "new";
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [hydrated, setHydrated] = useState(isNew);

  const detailQ = useQuery({
    queryKey: ["admin-portfolio", id],
    queryFn: () => getPortfolio(id as number),
    enabled: !isNew,
  });

  useEffect(() => {
    if (!isNew && detailQ.data && !hydrated) {
      setForm(fromView(detailQ.data));
      setHydrated(true);
    }
  }, [detailQ.data, isNew, hydrated]);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) => setForm((f) => ({ ...f, [key]: value }));
  const toggleWork = (wt: WorkType) =>
    setForm((f) => {
      const next = new Set(f.workTypes);
      if (next.has(wt)) next.delete(wt);
      else next.add(wt);
      return { ...f, workTypes: next };
    });

  const saveM = useMutation({
    mutationFn: async (): Promise<{ id: number }> => {
      if (isNew) return createPortfolio(toSave(form));
      await updatePortfolio(id as number, toSave(form));
      return { id: id as number };
    },
    onSuccess: (res) => {
      setError(null);
      qc.invalidateQueries({ queryKey: ["admin-portfolio"] });
      if (isNew) onCreated(res.id);
      else qc.invalidateQueries({ queryKey: ["admin-portfolio", id] });
    },
    onError: (e) => setError(resolveErrorMessage(errors, e).text),
  });

  const pubM = useMutation({
    mutationFn: (value: boolean) => setPortfolioPublished(id as number, value),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-portfolio", id] }),
    onError: (e) => setError(resolveErrorMessage(errors, e).text),
  });

  const published = detailQ.data?.published ?? false;

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center gap-2">
        <Button variant="outline" onClick={onClose}>
          ← {t.back}
        </Button>
        <h1 className="text-xl font-semibold">{form.heTitle || t.new}</h1>
        <div className="ms-auto flex gap-2">
          {!isNew && (
            <Button variant={published ? "muted" : "brass"} disabled={pubM.isPending} onClick={() => pubM.mutate(!published)}>
              {published ? t.unpublish : t.publish}
            </Button>
          )}
          <Button disabled={saveM.isPending} onClick={() => saveM.mutate()}>
            {saveM.isPending ? t.saving : t.save}
          </Button>
        </div>
      </header>

      {error && <p className="rounded-control bg-crit-bg px-3 py-2 text-sm text-crit-fg">{error}</p>}

      <section className="grid gap-4 sm:grid-cols-2">
        <Field label={t.fields.slug} value={form.slug} onChange={(e) => set("slug", e.target.value)} />
        <Field label={t.fields.city} value={form.city} onChange={(e) => set("city", e.target.value)} />
        <label className={LABEL}>
          <span>{t.fields.propertyType}</span>
          <select className={INPUT} value={form.propertyType} onChange={(e) => set("propertyType", e.target.value as PropertyType | "")}>
            <option value="">—</option>
            {PROPERTY_TYPES.map((pt) => (
              <option key={pt} value={pt}>
                {t.propertyTypes[pt]}
              </option>
            ))}
          </select>
        </label>
        <Field
          label={t.fields.areaSqm}
          type="number"
          inputMode="decimal"
          value={form.areaSqm}
          onChange={(e) => set("areaSqm", e.target.value)}
        />
        <Field
          label={t.fields.durationDays}
          type="number"
          value={form.durationDays}
          onChange={(e) => set("durationDays", e.target.value)}
        />
        <Field label={t.fields.completedAt} type="date" value={form.completedAt} onChange={(e) => set("completedAt", e.target.value)} />
      </section>

      <section>
        <p className="mb-2 text-sm font-medium text-ink-soft">{t.fields.workTypes}</p>
        <div className="flex flex-wrap gap-2">
          {WORK_TYPES.map((wt) => (
            <label key={wt} className="flex items-center gap-1.5 rounded-control border border-line px-3 py-1.5 text-sm">
              <input type="checkbox" checked={form.workTypes.has(wt)} onChange={() => toggleWork(wt)} />
              {t.workTypes[wt]}
            </label>
          ))}
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2 rounded-card border border-line p-3">
          <p className="text-sm font-medium">{t.texts.he}</p>
          <label className={LABEL}>
            <span>{t.texts.projectTitle}</span>
            <input className={INPUT} dir="rtl" value={form.heTitle} onChange={(e) => set("heTitle", e.target.value)} />
          </label>
          <label className={LABEL}>
            <span>{t.texts.description}</span>
            <textarea className={INPUT} dir="rtl" rows={4} value={form.heDescription} onChange={(e) => set("heDescription", e.target.value)} />
          </label>
        </div>
        <div className="space-y-2 rounded-card border border-line p-3">
          <p className="text-sm font-medium">{t.texts.ru}</p>
          <label className={LABEL}>
            <span>{t.texts.projectTitle}</span>
            <input className={INPUT} value={form.ruTitle} onChange={(e) => set("ruTitle", e.target.value)} />
          </label>
          <label className={LABEL}>
            <span>{t.texts.description}</span>
            <textarea className={INPUT} rows={4} value={form.ruDescription} onChange={(e) => set("ruDescription", e.target.value)} />
          </label>
        </div>
      </section>

      <MediaSection id={isNew ? null : (id as number)} view={detailQ.data} t={t} errors={errors} onError={setError} />
    </div>
  );
}

function MediaSection({
  id,
  view,
  t,
  errors,
  onError,
}: {
  id: number | null;
  view: PortfolioAdminView | undefined;
  t: PortfolioDict;
  errors: Record<string, string>;
  onError: (msg: string) => void;
}) {
  const qc = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [kind, setKind] = useState<MediaKind>("PROCESS");
  const [beforeFile, setBeforeFile] = useState<File | null>(null);
  const [afterFile, setAfterFile] = useState<File | null>(null);

  // Пара «до/после»: оба фото одной меткой (pair_group), чтобы витрина связала их в слайдер.
  const pairM = useMutation({
    mutationFn: async () => {
      const group = `p${Date.now().toString(36)}`;
      if (beforeFile) await uploadPortfolioMedia(id as number, beforeFile, "BEFORE", group);
      if (afterFile) await uploadPortfolioMedia(id as number, afterFile, "AFTER", group);
    },
    onSuccess: () => {
      setBeforeFile(null);
      setAfterFile(null);
      qc.invalidateQueries({ queryKey: ["admin-portfolio", id] });
    },
    onError: (e) => onError(resolveErrorMessage(errors, e).text),
  });
  const uploadM = useMutation({
    mutationFn: (file: File) => uploadPortfolioMedia(id as number, file, kind),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-portfolio", id] }),
    onError: (e) => onError(resolveErrorMessage(errors, e).text),
  });
  const delM = useMutation({
    mutationFn: (mediaId: number) => deletePortfolioMedia(mediaId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-portfolio", id] }),
    onError: (e) => onError(resolveErrorMessage(errors, e).text),
  });

  return (
    <section className="space-y-3 rounded-card border border-line p-3">
      <p className="text-sm font-medium">{t.media.title}</p>
      {id == null ? (
        <p className="text-sm text-ink-soft">{t.saveFirst}</p>
      ) : (
        <>
          <p className="text-xs text-ink-soft">{t.media.hint}</p>

          <div className="space-y-2 rounded-control border border-line p-3">
            <p className="text-sm font-medium">{t.media.pairTitle}</p>
            <div className="flex flex-wrap items-center gap-2">
              <FilePick label={t.media.before} file={beforeFile} onPick={setBeforeFile} />
              <FilePick label={t.media.after} file={afterFile} onPick={setAfterFile} />
              <Button disabled={!beforeFile || !afterFile || pairM.isPending} onClick={() => pairM.mutate()}>
                {pairM.isPending ? t.media.uploading : t.media.uploadPair}
              </Button>
            </div>
          </div>

          <details className="rounded-control border border-line p-3">
            <summary className="cursor-pointer text-sm text-ink-soft">{t.media.single}</summary>
            <div className="mt-2 flex flex-wrap items-end gap-2">
              <label className={LABEL}>
                <span>{t.media.kind}</span>
                <select className={INPUT} value={kind} onChange={(e) => setKind(e.target.value as MediaKind)}>
                  {MEDIA_KINDS.map((k) => (
                    <option key={k} value={k}>
                      {t.mediaKinds[k]}
                    </option>
                  ))}
                </select>
              </label>
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) uploadM.mutate(file);
                  e.target.value = "";
                }}
              />
              <Button disabled={uploadM.isPending} onClick={() => fileRef.current?.click()}>
                {uploadM.isPending ? t.media.uploading : t.media.upload}
              </Button>
            </div>
          </details>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {(view?.media ?? []).map((m) => (
              <div key={m.id} className="space-y-1 rounded-control border border-line p-2">
                <img src={m.url} alt="" className="h-24 w-full rounded-control object-cover" />
                <div className="flex items-center justify-between text-xs text-ink-soft">
                  <span>
                    {t.mediaKinds[m.kind]}
                    {m.pair_group ? ` · ${m.pair_group}` : ""}
                  </span>
                  <button
                    type="button"
                    className="text-crit-fg hover:underline"
                    onClick={() => {
                      if (window.confirm(t.media.deleteConfirm)) delM.mutate(m.id);
                    }}
                  >
                    ✕
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

/** Выбор одного файла с показом имени: кнопка открывает диалог, после выбора — галочка и имя. */
function FilePick({ label, file, onPick }: { label: string; file: File | null; onPick: (f: File | null) => void }) {
  const ref = useRef<HTMLInputElement>(null);
  return (
    <div className="flex flex-col gap-1">
      <input
        ref={ref}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(e) => onPick(e.target.files?.[0] ?? null)}
      />
      <Button variant="outline" type="button" onClick={() => ref.current?.click()}>
        {label}
        {file ? " ✓" : ""}
      </Button>
      {file && <span className="max-w-[150px] truncate text-xs text-ink-soft">{file.name}</span>}
    </div>
  );
}
