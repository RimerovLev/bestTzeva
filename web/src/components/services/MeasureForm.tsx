"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
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
      <div className="card ok done" role="status">
        <span className="ok-ico">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#121212" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M20 6 9 17l-5-5" /></svg>
        </span>
        <h3>{m.successTitle}</h3>
        <p>{m.successNumber}: <bdi className="num-big">{success.request_number}</bdi></p>
        <p>{m.successBody}</p>
        <div className="row">
          <Link href={`/${locale}/services`} className="btn btn-yellow btn-sm">{m.newRequest}</Link>
        </div>
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
    <div className="ms">
      <ol className="stp-bar">
        {steps.map((label, i) => (
          <li key={i} className={`stp${i === step ? " cur" : i < step ? " done" : ""}`} aria-current={i === step ? "step" : undefined}>
            <i>{i < step ? "✓" : <bdi>{i + 1}</bdi>}</i>
            {label}
          </li>
        ))}
      </ol>

      <div className="card ms-card">
        {step === 0 && (
          <div className="form-grid">
            <Field label={dict.services.propertyType}>
              <select value={form.property_type} onChange={(e) => set("property_type", e.target.value as PropertyType)}>
                {PROPERTY_TYPES.map((p) => <option key={p} value={p}>{dict.enums.property_type[p]}</option>)}
              </select>
            </Field>
            <Field label={dict.services.area}><input type="number" dir="ltr" value={form.area_sqm} onChange={(e) => set("area_sqm", e.target.value)} /></Field>
            <Field label={dict.services.ceiling}><input type="number" step="0.1" dir="ltr" value={form.ceiling_height_m} onChange={(e) => set("ceiling_height_m", e.target.value)} /></Field>
            <Field label={dict.services.rooms}><input type="number" dir="ltr" value={form.rooms_count} onChange={(e) => set("rooms_count", e.target.value)} /></Field>
          </div>
        )}

        {step === 1 && (
          <div>
            <p className="lbl">{dict.services.workTypes}</p>
            <div className="opt-list" role="group" aria-label={dict.services.workTypes}>
              {WORK_TYPES.map((w) => (
                <button key={w} type="button" className="opt" onClick={() => toggleWork(w)} aria-pressed={form.work_types.includes(w)}>
                  {dict.enums.work_type[w]}
                </button>
              ))}
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="form-grid">
            <Field label={dict.request.name}><input value={form.name} onChange={(e) => set("name", e.target.value)} autoComplete="name" /></Field>
            <Field label={dict.request.phone}><input type="tel" inputMode="tel" dir="ltr" autoComplete="tel" value={form.phone} onChange={(e) => set("phone", e.target.value)} placeholder="05X-XXXXXXX" /></Field>
            <Field label={dict.request.email}><input type="email" dir="ltr" autoComplete="email" value={form.email} onChange={(e) => set("email", e.target.value)} /></Field>
            <Field label={m.city}><input value={form.city} onChange={(e) => set("city", e.target.value)} autoComplete="address-level2" /></Field>
            <Field label={m.address} wide><input value={form.address} onChange={(e) => set("address", e.target.value)} autoComplete="street-address" /></Field>
            <Field label={m.dateFrom}><input type="date" value={form.date_from} onChange={(e) => set("date_from", e.target.value)} /></Field>
            <Field label={m.dateTo}><input type="date" value={form.date_to} onChange={(e) => set("date_to", e.target.value)} /></Field>
            <Field label={m.comment} wide><textarea rows={3} value={form.comment} onChange={(e) => set("comment", e.target.value)} /></Field>
          </div>
        )}

        {step === 3 && (
          <>
            <div>
              <p className="lbl">{m.photosTitle}</p>
              <p className="small" style={{ marginBottom: 10 }}>{m.photoHint}</p>
              <div className="ph-grid">
                {photos.map((p) => (
                  <div key={p.id} className="ph">
                    {/* eslint-disable-next-line @next/next/no-img-element -- blob-превью пользовательского фото, next/image неприменим */}
                    {p.previewUrl ? <img src={p.previewUrl} alt="" /> : <div className="sk-line" style={{ height: "100%", borderRadius: 0 }} />}
                    {p.status === "working" && <div className="ph-prog"><bdi>{p.progress}%</bdi></div>}
                    {p.status === "error" && <div className="ph-err" aria-label={dict.errors["client.unknown"]}>!</div>}
                    <div className="ph-acts">
                      <button type="button" onClick={() => rotatePhoto(p.id)} aria-label={m.rotate}>⟳</button>
                      <button type="button" onClick={() => removePhoto(p.id)} aria-label={m.remove}>✕</button>
                    </div>
                  </div>
                ))}
                {photos.length < MAX_PHOTOS && (
                  <button type="button" className="ph-add" onClick={() => fileRef.current?.click()}>+ {m.addPhotos}</button>
                )}
              </div>
              <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={(e) => addFiles(e.target.files)} />
            </div>

            {/* honeypot: не display:none — часть ботов такие поля пропускает */}
            <div aria-hidden className="hp">
              <label>Website<input type="text" tabIndex={-1} autoComplete="off" value={website} onChange={(e) => setWebsite(e.target.value)} /></label>
            </div>

            <label className="consent">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
              <span>{m.consentText} <Link href={`/${locale}/privacy`}>{m.policy}</Link></span>
            </label>

            {error && <p className="form-err" role="alert">{error}</p>}
          </>
        )}

        <div className="ms-nav">
          <button type="button" className="btn btn-sm" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0}>{m.back}</button>
          {step < 3 ? (
            <button type="button" className="btn btn-blue btn-sm" onClick={() => setStep((s) => s + 1)} disabled={!canNext}>{m.next}</button>
          ) : (
            <button type="button" className="btn btn-red btn-sm" onClick={onSubmit} disabled={submitting || uploading || !consent}>{submitting ? m.submitting : m.submit}</button>
          )}
        </div>
      </div>
    </div>
  );
}

function Field({ label, children, wide = false }: { label: string; children: ReactNode; wide?: boolean }) {
  return <label className={`field${wide ? " span2" : ""}`}><span className="field-l">{label}</span>{children}</label>;
}
