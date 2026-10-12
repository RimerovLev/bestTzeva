import { useState } from "react";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import type { Locale } from "@/lib/i18n/config";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api/problem";
import { resolveErrorMessage } from "@/lib/api/messages";
import {
  createProduct,
  listCategories,
  PAINT_TYPES,
  USAGE_AREAS,
  FINISHES,
  BASE_TYPES,
  type ProductSaveRequest,
} from "./api";

const trOptional = z.object({
  name: z.string().optional(),
  short_description: z.string().optional(),
  description: z.string().optional(),
  usage_instructions: z.string().optional(),
  meta_title: z.string().optional(),
  meta_description: z.string().optional(),
});

// Значения формы — строки (из input); числа проверяются refine'ом и конвертируются на отправке.
const posNum = z.string().refine((s) => s.trim() !== "" && Number(s) > 0, { message: "required" });
const optNum = z.string().optional();
const intId = z.string().refine((s) => s.trim() !== "" && Number.isInteger(Number(s)) && Number(s) > 0, { message: "required" });

const schema = z.object({
  sku: z.string().trim().min(1),
  slug: z.string().optional(),
  category_id: intId,
  brand_id: intId,
  paint_type: z.enum(PAINT_TYPES),
  usage_area: z.enum(USAGE_AREAS),
  finish: z.enum(FINISHES),
  base_type: z.enum(BASE_TYPES),
  coverage_sqm_per_liter: optNum,
  drying_time_hours: optNum,
  featured: z.boolean(),
  translations: z.object({
    he: trOptional.extend({ name: z.string().trim().min(1) }),
    ru: trOptional,
    en: trOptional,
  }),
  attributes: z.array(z.object({ code: z.string(), value: z.string() })),
  variants: z
    .array(
      z.object({
        sku: z.string().trim().min(1),
        color_code: z.string().trim().min(1),
        color_hex: z.string().optional(),
        volume_liters: posNum,
        price: posNum,
        old_price: optNum,
        active: z.boolean(),
      }),
    )
    .min(1),
});

type FormValues = z.infer<typeof schema>;

const emptyVariant = { sku: "", color_code: "", color_hex: "#cccccc", volume_liters: "", price: "", old_price: "", active: true };

export function ProductForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const { dict, locale } = useI18n();
  const c = dict.catalog;
  const qc = useQueryClient();
  const categoriesQuery = useQuery({ queryKey: ["categories", locale], queryFn: listCategories });

  const [tab, setTab] = useState<Locale | "en">("he");
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      sku: "", slug: "", category_id: "", brand_id: "", featured: false,
      paint_type: "INTERIOR", usage_area: "INDOOR", finish: "MATTE", base_type: "WATER",
      coverage_sqm_per_liter: "", drying_time_hours: "",
      translations: { he: { name: "" }, ru: {}, en: {} },
      attributes: [],
      variants: [{ ...emptyVariant }],
    },
  });

  const variants = useFieldArray({ control, name: "variants" });
  const attributes = useFieldArray({ control, name: "attributes" });

  async function onSubmit(values: FormValues) {
    setFormError(null);
    const body = toSaveRequest(values);
    try {
      await createProduct(body);
      qc.invalidateQueries({ queryKey: ["admin-products"] });
      onDone();
    } catch (err) {
      if (err instanceof ApiError && err.errors) {
        // Java-пути (variants[0].price) → rhf-пути (variants.0.price).
        for (const [field, msgs] of Object.entries(err.errors)) {
          const path = field.replace(/\[(\d+)\]/g, ".$1") as keyof FormValues;
          if (msgs[0]) setError(path, { message: msgs[0] });
        }
      }
      setFormError(resolveErrorMessage(dict.errors, err).text);
    }
  }

  const localesTabs: (Locale | "en")[] = ["he", "ru", "en"];

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-h1 font-bold text-navy">{c.formCreateTitle}</h1>
        <Button variant="muted" type="button" onClick={onCancel}>{c.back}</Button>
      </div>

      {/* Основные поля */}
      <fieldset className="grid grid-cols-1 gap-4 rounded-card border border-line bg-surface p-5 sm:grid-cols-2 lg:grid-cols-3">
        <F label={c.sku} error={errors.sku && dict.common.retry ? errmsg(errors.sku) : undefined}><input {...register("sku")} className={inputCls(!!errors.sku)} /></F>
        <F label={c.slug} hint={c.slugAuto}><input {...register("slug")} className={inputCls(false)} /></F>
        <F label={c.category} error={errmsg(errors.category_id)}>
          <select {...register("category_id")} className={inputCls(!!errors.category_id)}>
            <option value="">—</option>
            {(categoriesQuery.data ?? []).map((cat) => <option key={cat.id} value={cat.id}>{cat.name ?? cat.slug}</option>)}
          </select>
        </F>
        <F label={c.brand} error={errmsg(errors.brand_id)} hint={c.brandHint}><input type="number" {...register("brand_id")} className={inputCls(!!errors.brand_id)} /></F>
        <EnumF label={c.paintType} name="paint_type" options={PAINT_TYPES} labels={dict.enums.paint_type} register={register} />
        <EnumF label={c.usageArea} name="usage_area" options={USAGE_AREAS} labels={dict.enums.usage_area} register={register} />
        <EnumF label={c.finish} name="finish" options={FINISHES} labels={dict.enums.finish} register={register} />
        <EnumF label={c.baseType} name="base_type" options={BASE_TYPES} labels={dict.enums.base_type} register={register} />
        <F label={c.coverage}><input type="number" step="0.1" {...register("coverage_sqm_per_liter")} className={inputCls(false)} /></F>
        <F label={c.dryingTime}><input type="number" step="0.5" {...register("drying_time_hours")} className={inputCls(false)} /></F>
        <label className="flex items-center gap-2 self-end text-sm text-ink-soft"><input type="checkbox" {...register("featured")} className="accent-brass" />{c.featured}</label>
      </fieldset>

      {/* Переводы */}
      <fieldset className="rounded-card border border-line bg-surface p-5">
        <div className="mb-3 flex items-center gap-3">
          <span className="font-semibold text-navy">{c.translations}</span>
          <span className="rounded-pill bg-warn-bg px-2 py-0.5 text-xs text-warn-fg">{c.heRequired}</span>
          <span className="text-xs text-ink-faint">{c.fallbackNote}</span>
        </div>
        <div className="mb-4 flex gap-1">
          {localesTabs.map((t) => (
            <button key={t} type="button" onClick={() => setTab(t)} className={`rounded-control px-3 py-1.5 text-sm ${tab === t ? "bg-brass/10 font-medium text-navy" : "text-ink-soft hover:bg-paper-warm"}`}>
              {t.toUpperCase()}
              {t === "he" && errors.translations?.he?.name && <span className="ms-1 text-crit-fg">•</span>}
            </button>
          ))}
        </div>
        {localesTabs.map((t) => (
          <div key={t} hidden={tab !== t} className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <F label={c.trName} error={t === "he" ? errmsg(errors.translations?.he?.name) : undefined}>
              <input {...register(`translations.${t}.name` as const)} className={inputCls(t === "he" && !!errors.translations?.he?.name)} />
            </F>
            <F label={c.trShort}><input {...register(`translations.${t}.short_description` as const)} className={inputCls(false)} /></F>
            <F label={c.trDescription}><textarea rows={2} {...register(`translations.${t}.description` as const)} className={inputCls(false)} /></F>
            <F label={c.trUsage}><textarea rows={2} {...register(`translations.${t}.usage_instructions` as const)} className={inputCls(false)} /></F>
            <F label={c.trMetaTitle}><input {...register(`translations.${t}.meta_title` as const)} className={inputCls(false)} /></F>
            <F label={c.trMetaDescription}><input {...register(`translations.${t}.meta_description` as const)} className={inputCls(false)} /></F>
          </div>
        ))}
      </fieldset>

      {/* Варианты */}
      <fieldset className="rounded-card border border-line bg-surface p-5">
        <div className="mb-3 flex items-center justify-between">
          <span className="font-semibold text-navy">{c.variants}</span>
          <Button variant="outline" type="button" onClick={() => variants.append({ ...emptyVariant })}>{c.addVariant}</Button>
        </div>
        {typeof errors.variants?.message === "string" && <p className="mb-2 text-sm text-crit-fg">{c.atLeastOneVariant}</p>}
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line text-ink-soft">
                <th className="px-2 py-2 text-start">{c.vColorHex}</th>
                <th className="px-2 py-2 text-start">{c.vColorCode}</th>
                <th className="px-2 py-2 text-start">{c.vVolume}</th>
                <th className="px-2 py-2 text-start">{c.vPrice}</th>
                <th className="px-2 py-2 text-start">{c.vOldPrice}</th>
                <th className="px-2 py-2 text-start">{c.vSku}</th>
                <th className="px-2 py-2 text-start">{c.vActive}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {variants.fields.map((field, i) => (
                <tr key={field.id} className="border-b border-line/60">
                  <td className="px-2 py-2"><input type="color" {...register(`variants.${i}.color_hex` as const)} className="h-9 w-12 rounded border border-line-strong" /></td>
                  <td className="px-2 py-2"><input {...register(`variants.${i}.color_code` as const)} className={`w-24 ${inputCls(!!errors.variants?.[i]?.color_code)}`} placeholder="RAL…" /></td>
                  <td className="px-2 py-2"><input type="number" step="0.1" {...register(`variants.${i}.volume_liters` as const)} className={`w-20 ${inputCls(!!errors.variants?.[i]?.volume_liters)}`} /></td>
                  <td className="px-2 py-2"><input type="number" step="0.01" {...register(`variants.${i}.price` as const)} className={`w-24 ${inputCls(!!errors.variants?.[i]?.price)}`} /></td>
                  <td className="px-2 py-2"><input type="number" step="0.01" {...register(`variants.${i}.old_price` as const)} className="w-24 rounded-control border border-line-strong px-2 py-1.5" /></td>
                  <td className="px-2 py-2"><input {...register(`variants.${i}.sku` as const)} placeholder="SKU" className={`w-32 ${inputCls(!!errors.variants?.[i]?.sku)}`} /></td>
                  <td className="px-2 py-2 text-center"><input type="checkbox" {...register(`variants.${i}.active` as const)} className="accent-brass" /></td>
                  <td className="px-2 py-2">{variants.fields.length > 1 && <button type="button" onClick={() => variants.remove(i)} className="text-crit-fg">✕</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </fieldset>

      {/* Характеристики */}
      <fieldset className="rounded-card border border-line bg-surface p-5">
        <div className="mb-3 flex items-center justify-between">
          <span className="font-semibold text-navy">{c.attributes}</span>
          <Button variant="outline" type="button" onClick={() => attributes.append({ code: "", value: "" })}>{c.addAttribute}</Button>
        </div>
        <div className="space-y-2">
          {attributes.fields.map((field, i) => (
            <div key={field.id} className="flex gap-2">
              <input {...register(`attributes.${i}.code` as const)} placeholder={c.attrCode} className="w-40 rounded-control border border-line-strong px-2 py-1.5 text-sm" />
              <input {...register(`attributes.${i}.value` as const)} placeholder={c.attrValue} className="flex-1 rounded-control border border-line-strong px-2 py-1.5 text-sm" />
              <button type="button" onClick={() => attributes.remove(i)} className="text-crit-fg">✕</button>
            </div>
          ))}
        </div>
      </fieldset>

      {formError && <div className="rounded-control border border-crit-fg/30 bg-crit-bg px-3 py-2 text-sm text-crit-fg">{formError}</div>}

      <div className="flex justify-end gap-3">
        <Button variant="muted" type="button" onClick={onCancel}>{c.cancel}</Button>
        <Button variant="primary" type="submit" disabled={isSubmitting}>{isSubmitting ? c.saving : c.save}</Button>
      </div>
    </form>
  );
}

// ── helpers ────────────────────────────────────────────────────────────────

function inputCls(error: boolean): string {
  return `rounded-control border bg-surface px-3 py-2 text-sm outline-none focus:border-brass ${error ? "border-crit-fg" : "border-line-strong"}`;
}

function errmsg(err: unknown): string | undefined {
  return err && typeof err === "object" && "message" in err ? String((err as { message?: unknown }).message ?? "") || undefined : undefined;
}

function F({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1">
      <span className="text-sm font-medium text-ink-soft">{label}</span>
      {children}
      {hint && !error && <span className="text-xs text-ink-faint">{hint}</span>}
      {error && <span className="text-xs text-crit-fg">{error}</span>}
    </label>
  );
}

function EnumF<T extends string>({ label, name, options, labels, register }: {
  label: string; name: string; options: readonly T[]; labels: Record<string, string>;
  register: ReturnType<typeof useForm<FormValues>>["register"];
}) {
  return (
    <F label={label}>
      <select {...register(name as keyof FormValues)} className={inputCls(false)}>
        {options.map((o) => <option key={o} value={o}>{labels[o] ?? o}</option>)}
      </select>
    </F>
  );
}

function toSaveRequest(v: FormValues): ProductSaveRequest {
  const num = (x: unknown) => (x === "" || x == null ? undefined : Number(x));
  const tr = (t: FormValues["translations"]["ru"]) =>
    t.name && t.name.trim() ? t : undefined;
  return {
    sku: v.sku,
    slug: v.slug || undefined,
    category_id: Number(v.category_id),
    brand_id: Number(v.brand_id),
    paint_type: v.paint_type,
    usage_area: v.usage_area,
    finish: v.finish,
    base_type: v.base_type,
    coverage_sqm_per_liter: num(v.coverage_sqm_per_liter),
    drying_time_hours: num(v.drying_time_hours),
    featured: v.featured,
    active: true,
    translations: { he: v.translations.he, ru: tr(v.translations.ru), en: tr(v.translations.en) },
    attributes: v.attributes.filter((a) => a.code.trim() && a.value.trim()),
    variants: v.variants.map((vr) => ({
      sku: vr.sku,
      color_code: vr.color_code,
      color_hex: vr.color_hex || undefined,
      volume_liters: Number(vr.volume_liters),
      price: Number(vr.price),
      old_price: num(vr.old_price),
      active: vr.active,
    })),
  } as ProductSaveRequest;
}
