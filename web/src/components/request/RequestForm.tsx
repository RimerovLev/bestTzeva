"use client";

import { useRef, useState, type InputHTMLAttributes } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { RequestItem } from "@/lib/request/RequestProvider";
import {
  submitProductRequest,
  getProductBySlug,
  type ProductRequestResponse,
} from "@/lib/catalog/api";
import { ApiError } from "@/lib/api/problem";
import { resolveErrorMessage } from "@/lib/api/messages";

const schema = z.object({
  name: z.string().trim().min(1),
  phone: z.string().trim().min(1),
  email: z.union([z.string().trim().email(), z.literal("")]).optional(),
  contactTime: z.string().optional(),
  comment: z.string().optional(),
  consent: z.boolean().refine((v) => v === true),
  website: z.string().optional(), // honeypot
});

type FormValues = z.infer<typeof schema>;

export function RequestForm({
  items,
  locale,
  dict,
  onSuccess,
  onUnavailable,
}: {
  items: RequestItem[];
  locale: Locale;
  dict: Dictionary;
  /** at — центр кнопки отправки, откуда летят брызги. */
  onSuccess: (res: ProductRequestResponse, at: { x: number; y: number } | null) => void;
  onUnavailable: (ids: number[]) => void;
}) {
  const r = dict.request;
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { consent: false, website: "" },
  });
  const [formError, setFormError] = useState<string | null>(null);
  const consent = watch("consent");
  const submitAt = useRef<{ x: number; y: number } | null>(null);

  /** На blanket-ошибке variant_unavailable сами находим недоступные позиции (бэкенд их не называет). */
  async function detectUnavailable(): Promise<number[]> {
    const slugs = [...new Set(items.map((i) => i.slug).filter(Boolean))];
    const available = new Set<number>();
    await Promise.all(
      slugs.map(async (slug) => {
        try {
          const card = await getProductBySlug(slug, { locale });
          for (const v of card.variants ?? []) if (v.id != null) available.add(v.id);
        } catch {
          // не удалось проверить — оставляем позицию как есть
        }
      }),
    );
    return items.filter((i) => !available.has(i.variantId)).map((i) => i.variantId);
  }

  async function onSubmit(values: FormValues) {
    setFormError(null);
    onUnavailable([]);
    try {
      const res = await submitProductRequest(
        {
          customer_name: values.name,
          customer_phone: values.phone, // как ввёл пользователь — нормализует бэкенд
          customer_email: values.email || undefined,
          comment: values.comment || undefined,
          preferred_contact_time: values.contactTime || undefined,
          consent: values.consent,
          website: values.website || "",
          items: items.map((i) => ({ variant_id: i.variantId, qty: i.qty })),
        },
        { locale },
      );
      onSuccess(res, submitAt.current);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "request.consent_required") {
          setError("consent", { message: "required" });
          return;
        }
        if (err.code === "request.variant_unavailable") {
          onUnavailable(await detectUnavailable());
          setFormError(resolveErrorMessage(dict.errors, err).text);
          return;
        }
        if (err.code === "common.rate_limited" && err.retryAfter) {
          setFormError(r.retryAfter.replace("{seconds}", String(err.retryAfter)));
          return;
        }
      }
      setFormError(resolveErrorMessage(dict.errors, err).text);
    }
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      onSubmitCapture={(e) => {
        const b = (e.currentTarget.querySelector("button[type=submit]") as HTMLElement | null)?.getBoundingClientRect();
        submitAt.current = b ? { x: b.left + b.width / 2, y: b.top + b.height / 2 } : null;
      }}
      noValidate
      className="card form-card tb-form"
    >
      <h2>{r.formTitle}</h2>

      <div className="form-grid">
        <TextField id="rq-name" label={r.name} error={errors.name && r.required} {...register("name")} autoComplete="name" />
        <TextField
          id="rq-phone"
          label={r.phone}
          error={errors.phone && r.required}
          {...register("phone")}
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          dir="ltr"
          placeholder="05X-XXXXXXX"
        />
      </div>
      <div className="form-grid">
        <TextField id="rq-email" label={r.email} error={errors.email && r.required} {...register("email")} type="email" autoComplete="email" dir="ltr" />
        <TextField id="rq-time" label={r.contactTime} {...register("contactTime")} placeholder={r.contactTimePlaceholder} />
      </div>

      <div className="field">
        <label htmlFor="rq-comment">{r.comment}</label>
        <textarea id="rq-comment" {...register("comment")} rows={3} />
      </div>

      {/* Honeypot: не display:none, а увод за экран; боты его заполняют, люди — нет. */}
      <div aria-hidden className="hp">
        <label>
          Website
          <input type="text" tabIndex={-1} autoComplete="off" {...register("website")} />
        </label>
      </div>

      <label className="consent">
        <input type="checkbox" {...register("consent")} />
        <span>
          {r.consentBefore} <Link href={`/${locale}/privacy`}>{r.policy}</Link>
          {errors.consent && <span className="field"><span className="err">{r.required}</span></span>}
        </span>
      </label>

      {formError && <p className="form-err" role="alert">{formError}</p>}

      <button type="submit" disabled={isSubmitting || !consent} className="btn btn-red">
        {isSubmitting ? r.submitting : r.submit}
      </button>
    </form>
  );
}

const TextField = ({
  label,
  error,
  id,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string | false; id: string }) => (
  <div className="field">
    <label htmlFor={id}>{label}</label>
    <input id={id} aria-invalid={error ? true : undefined} {...props} />
    {error && <span className="err">{error}</span>}
  </div>
);
