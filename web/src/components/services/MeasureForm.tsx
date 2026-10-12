"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { ApiError } from "@/lib/api/problem";
import { resolveErrorMessage } from "@/lib/api/messages";
import {
  getFormToken, presignPhotos, uploadToStorage, submitServiceRequest,
  MEASURE_PREFILL_KEY, PROPERTY_TYPES, WORK_TYPES,
  type CalculateRequest, type ServiceRequestCreate, type ServiceRequestResponse,
} from "@/lib/services/api";
import { compressImage } from "@/lib/services/image";

type PropertyType = (typeof PROPERTY_TYPES)[number];
type WorkType = (typeof WORK_TYPES)[number];
const PROGRESS_KEY = "tb.measure.progress";
const MAX_PHOTOS = 10;

interface FormState {
  property_type: PropertyType;
  area_sqm: string; ceiling_height_m: string; rooms_count: string;
  work_types: WorkType[];
  name: string; phone: string; email: string; city: string; address: string;
  date_from: string; date_to: string; comment: string;
}
interface Photo {
  id: string; file: File; previewUrl: string; rotate: number;
  status: "working" | "done" | "error"; progress: number; objectKey?: string;
}

const EMPTY: FormState = {
  property_type: "APARTMENT", area_sqm: "", ceiling_height_m: "", rooms_count: "",
  work_types: [], name: "", phone: "", email: "", city: "", address: "", date_from: "", date_to: "", comment: "",
};

export function MeasureForm({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const m = dict.measure;
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [consent, setConsent] = useState(false);
  const [website, setWebsite] = useState(""); // honeypot
  const [formToken, setFormToken] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<ServiceRequestResponse | null>(null);
  const started = useRef(false);
  const fileRef = useRef<HTMLInputElement>(null);

  // Инициализация: токен формы + перенос из калькулятора + восстановление прогресса.
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    getFormToken(locale).then(setFormToken).catch(() => {});
    try {
      const saved = sessionStorage.getItem(PROGRESS_KEY);
      const prefillRaw = sessionStorage.getItem(MEASURE_PREFILL_KEY);
      if (saved) {
        setForm(JSON.parse(saved) as FormState);
      } else if (prefillRaw) {
        const p = JSON.parse(prefillRaw) as CalculateRequest;
        setForm((f) => ({
          ...f,
          property_type: (p.property_type as PropertyType) ?? f.property_type,
          area_sqm: p.area_sqm != null ? String(p.area_sqm) : "",
          ceiling_height_m: p.ceiling_height_m != null ? String(p.ceiling_height_m) : "",
          rooms_count: p.rooms_count != null ? String(p.rooms_count) : "",
          work_types: (p.work_types as WorkType[]) ?? [],
        }));
      }
    } catch {
      // приватный режим — начинаем с пустой формы
    }
  }, [locale]);

  // Прогресс формы (без фото — они не сериализуются) переживает перезагрузку.
  useEffect(() => {
    try { sessionStorage.setItem(PROGRESS_KEY, JSON.stringify(form)); } catch { /* приватный режим */ }
  }, [form]);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }
  function toggleWork(w: WorkType) {
    setForm((f) => ({ ...f, work_types: f.work_types.includes(w) ? f.work_types.filter((x) => x !== w) : [...f.work_types, w] }));
  }

  async function uploadOne(file: File, id: string, rotate: number) {
    setPhotos((ps) => ps.map((p) => (p.id === id ? { ...p, status: "working", progress: 0, rotate } : p)));
    try {
      const blob = await compressImage(file, rotate);
      const { uploads } = await presignPhotos([{ filename: file.name, content_type: "image/jpeg" }], locale);
      const up = uploads?.[0];
      if (!up?.upload_url || !up.object_key) throw new ApiError({ code: "client.unknown", status: 0 });
      await uploadToStorage(up.upload_url, blob, (pct) => setPhotos((ps) => ps.map((p) => (p.id === id ? { ...p, progress: pct } : p))));
      const preview = URL.createObjectURL(blob);
      setPhotos((ps) => ps.map((p) => (p.id === id ? { ...p, status: "done", objectKey: up.object_key ?? undefined, previewUrl: preview } : p)));
    } catch {
      setPhotos((ps) => ps.map((p) => (p.id === id ? { ...p, status: "error" } : p)));
    }
  }

  function addFiles(files: FileList | null) {
    if (!files) return;
    const room = MAX_PHOTOS - photos.length;
    for (const file of Array.from(files).slice(0, room)) {
      if (!file.type.startsWith("image/")) continue;
      const id = crypto.randomUUID();
      setPhotos((ps) => [...ps, { id, file, previewUrl: "", rotate: 0, status: "working", progress: 0 }]);
      void uploadOne(file, id, 0);
    }
    if (fileRef.current) fileRef.current.value = "";
  }
  function rotatePhoto(id: string) {
    const photo = photos.find((p) => p.id === id);
    if (!photo) return;
    void uploadOne(photo.file, id, (photo.rotate + 90) % 360);
  }
  function removePhoto(id: string) {
    setPhotos((ps) => ps.filter((p) => p.id !== id));
  }

  async function onSubmit() {
    setSubmitting(true);
    setError(null);
    try {
      const body: ServiceRequestCreate = {
        customer_name: form.name, customer_phone: form.phone, customer_email: form.email || undefined,
        property_type: form.property_type,
        area_sqm: form.area_sqm ? Number(form.area_sqm) : undefined,
        ceiling_height_m: form.ceiling_height_m ? Number(form.ceiling_height_m) : undefined,
        rooms_count: form.rooms_count ? Number(form.rooms_count) : undefined,
        work_types: form.work_types,
        city: form.city || undefined, address: form.address || undefined,
        preferred_date_from: form.date_from || undefined, preferred_date_to: form.date_to || undefined,
        comment: form.comment || undefined,
        consent, website, form_token: formToken,
        photo_keys: photos.filter((p) => p.status === "done" && p.objectKey).map((p) => p.objectKey!),
      };
      const res = await submitServiceRequest(body, locale);
      try { sessionStorage.removeItem(PROGRESS_KEY); sessionStorage.removeItem(MEASURE_PREFILL_KEY); } catch { /* ignore */ }
      setSuccess(res);
    } catch (err) {
      setError(resolveErrorMessage(dict.errors, err).text);
    } finally {
      setSubmitting(false);
    }
  }

  if (success) {
    return (
      <div className="mx-auto max-w-md rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <p className="text-h2 font-bold text-navy">{m.successTitle}</p>
        <p className="mt-4 text-sm text-ink-soft">{m.successNumber}</p>
        <p className="numeric text-h1 font-bold text-brass"><bdi>{success.request_number}</bdi></p>
        <p className="mt-2 text-ink-soft">{m.successBody}</p>
        <Link href={`/${locale}/services`} className="mt-6 inline-block rounded-control border border-brass px-4 py-2 text-sm text-brass hover:bg-brass hover:text-surface">{m.newRequest}</Link>
      </div>
    );
  }

  const steps = [m.stepObject, m.stepWork, m.stepContact, m.stepPhotos];
  const canNext =
    (step === 0 && form.area_sqm !== "") ||
    (step === 1 && form.work_types.length > 0) ||
    (step === 2 && form.name.trim() !== "" && form.phone.trim() !== "") ||
    step === 3;
  const uploading = photos.some((p) => p.status === "working");

  return (
    <div className="mx-auto max-w-2xl">
      <ol className="mb-6 flex gap-2">
        {steps.map((label, i) => (
          <li key={i} className={`flex-1 rounded-control px-3 py-2 text-center text-sm ${i === step ? "bg-navy text-surface" : i < step ? "bg-brass/20 text-navy" : "bg-paper-warm text-ink-faint"}`}>
            <bdi className="numeric">{i + 1}</bdi> {label}
          </li>
        ))}
      </ol>

      <div className="rounded-card border border-line bg-surface p-6 shadow-card">
        {step === 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={dict.services.propertyType}>
              <select value={form.property_type} onChange={(e) => set("property_type", e.target.value as PropertyType)} className={inputCls}>
                {PROPERTY_TYPES.map((p) => <option key={p} value={p}>{dict.enums.property_type[p]}</option>)}
              </select>
            </Field>
            <Field label={dict.services.area}><input type="number" value={form.area_sqm} onChange={(e) => set("area_sqm", e.target.value)} className={`numeric ${inputCls}`} /></Field>
            <Field label={dict.services.ceiling}><input type="number" step="0.1" value={form.ceiling_height_m} onChange={(e) => set("ceiling_height_m", e.target.value)} className={`numeric ${inputCls}`} /></Field>
            <Field label={dict.services.rooms}><input type="number" value={form.rooms_count} onChange={(e) => set("rooms_count", e.target.value)} className={`numeric ${inputCls}`} /></Field>
          </div>
        )}

        {step === 1 && (
          <div className="flex flex-wrap gap-2">
            {WORK_TYPES.map((w) => {
              const active = form.work_types.includes(w);
              return <button key={w} type="button" onClick={() => toggleWork(w)} aria-pressed={active} className={`rounded-control border px-3 py-1.5 text-sm ${active ? "border-brass bg-brass/10 font-medium text-navy" : "border-line-strong text-ink-soft hover:bg-paper-warm"}`}>{dict.enums.work_type[w]}</button>;
            })}
          </div>
        )}

        {step === 2 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label={dict.request.name}><input value={form.name} onChange={(e) => set("name", e.target.value)} autoComplete="name" className={inputCls} /></Field>
            <Field label={dict.request.phone}><input type="tel" value={form.phone} onChange={(e) => set("phone", e.target.value)} placeholder="05X-XXXXXXX" className={inputCls} /></Field>
            <Field label={dict.request.email}><input type="email" value={form.email} onChange={(e) => set("email", e.target.value)} className={inputCls} /></Field>
            <Field label={m.city}><input value={form.city} onChange={(e) => set("city", e.target.value)} className={inputCls} /></Field>
            <Field label={m.address}><input value={form.address} onChange={(e) => set("address", e.target.value)} className={inputCls} /></Field>
            <div className="flex gap-2">
              <Field label={m.dateFrom}><input type="date" value={form.date_from} onChange={(e) => set("date_from", e.target.value)} className={inputCls} /></Field>
              <Field label={m.dateTo}><input type="date" value={form.date_to} onChange={(e) => set("date_to", e.target.value)} className={inputCls} /></Field>
            </div>
            <Field label={m.comment}><textarea rows={2} value={form.comment} onChange={(e) => set("comment", e.target.value)} className={`sm:col-span-2 ${inputCls}`} /></Field>
          </div>
        )}

        {step === 3 && (
          <div className="space-y-4">
            <div>
              <p className="text-sm font-medium text-ink-soft">{m.photosTitle}</p>
              <p className="text-xs text-ink-faint">{m.photoHint}</p>
              <div className="mt-2 grid grid-cols-3 gap-3 sm:grid-cols-4">
                {photos.map((p) => (
                  <div key={p.id} className="relative overflow-hidden rounded-control border border-line">
                    {/* eslint-disable-next-line @next/next/no-img-element -- blob-превью пользовательского фото, next/image неприменим */}
                    {p.previewUrl ? <img src={p.previewUrl} alt="" className="aspect-square w-full object-cover" /> : <div className="aspect-square w-full animate-pulse bg-line/50" />}
                    {p.status === "working" && <div className="absolute inset-x-0 bottom-0 bg-navy/70 text-center text-xs text-surface"><bdi className="numeric">{p.progress}%</bdi></div>}
                    {p.status === "error" && <div className="absolute inset-0 grid place-items-center bg-crit-bg/80 text-xs text-crit-fg">!</div>}
                    <div className="absolute inset-inline-start-1 top-1 flex gap-1">
                      <button type="button" onClick={() => rotatePhoto(p.id)} className="rounded bg-surface/90 px-1 text-xs" aria-label={m.rotate}>⟳</button>
                      <button type="button" onClick={() => removePhoto(p.id)} className="rounded bg-surface/90 px-1 text-xs text-crit-fg" aria-label={m.remove}>✕</button>
                    </div>
                  </div>
                ))}
                {photos.length < MAX_PHOTOS && (
                  <button type="button" onClick={() => fileRef.current?.click()} className="grid aspect-square place-items-center rounded-control border-2 border-dashed border-line-strong text-sm text-ink-soft hover:bg-paper-warm">
                    + {m.addPhotos}
                  </button>
                )}
              </div>
              <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={(e) => addFiles(e.target.files)} />
            </div>

            {/* honeypot */}
            <div aria-hidden className="absolute -left-[9999px] h-0 w-0 overflow-hidden">
              <label>Website<input type="text" tabIndex={-1} autoComplete="off" value={website} onChange={(e) => setWebsite(e.target.value)} /></label>
            </div>

            <label className="flex items-start gap-2 text-sm text-ink-soft">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} className="mt-1 accent-brass" />
              <span>{m.consentText}{" "}<Link href={`/${locale}/privacy`} className="text-brass underline">{m.policy}</Link></span>
            </label>

            {error && <div className="rounded-control border border-crit-fg/30 bg-crit-bg px-3 py-2 text-sm text-crit-fg">{error}</div>}
          </div>
        )}

        <div className="mt-6 flex justify-between">
          <button type="button" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0} className="rounded-control border border-line-strong px-4 py-2 text-sm text-ink-soft disabled:opacity-40">{m.back}</button>
          {step < 3 ? (
            <button type="button" onClick={() => setStep((s) => s + 1)} disabled={!canNext} className="rounded-control bg-navy px-6 py-2 text-sm font-medium text-surface disabled:opacity-50">{m.next}</button>
          ) : (
            <button type="button" onClick={onSubmit} disabled={submitting || uploading || !consent} className="rounded-control bg-brass px-6 py-2 text-sm font-medium text-surface disabled:opacity-50">{submitting ? m.submitting : m.submit}</button>
          )}
        </div>
      </div>
    </div>
  );
}

const inputCls = "rounded-control border border-line-strong bg-surface px-3 py-2 text-body outline-none focus:border-brass focus-visible:ring-2 focus-visible:ring-terracotta";
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="flex flex-1 flex-col gap-1"><span className="text-sm font-medium text-ink-soft">{label}</span>{children}</label>;
}
