"use client";

import { useState } from "react";
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
  onSuccess: (res: ProductRequestResponse) => void;
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
      onSuccess(res);
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
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="rounded-card border border-line bg-surface p-5 shadow-card">
      <h2 className="text-h3 font-bold text-navy">{r.formTitle}</h2>

      <div className="mt-4 space-y-3">
        <TextField label={r.name} error={errors.name && r.required} {...register("name")} autoComplete="name" />
        <TextField
          label={r.phone}
          error={errors.phone && r.required}
          {...register("phone")}
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          placeholder="05X-XXXXXXX"
        />
        <TextField label={r.email} error={errors.email && r.required} {...register("email")} type="email" autoComplete="email" />
        <TextField label={r.contactTime} {...register("contactTime")} placeholder={r.contactTimePlaceholder} />

        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium text-ink-soft">{r.comment}</span>
          <textarea
            {...register("comment")}
            rows={3}
            className="rounded-control border border-line-strong bg-surface px-3 py-2 text-body outline-none focus:border-brass focus-visible:ring-2 focus-visible:ring-terracotta"
          />
        </label>

        {/* Honeypot: не display:none, а увод за экран; боты его заполняют, люди — нет. */}
        <div aria-hidden className="absolute -left-[9999px] top-auto h-0 w-0 overflow-hidden">
          <label>
            Website
            <input type="text" tabIndex={-1} autoComplete="off" {...register("website")} />
          </label>
        </div>

        <label className="flex items-start gap-2 text-sm text-ink-soft">
          <input type="checkbox" {...register("consent")} className="mt-1 accent-brass" />
          <span>
            {r.consentBefore}{" "}
            <Link href={`/${locale}/privacy`} className="text-brass underline">
              {r.policy}
            </Link>
            {errors.consent && <span className="block text-xs text-crit-fg">{r.required}</span>}
          </span>
        </label>

        {formError && (
          <div className="rounded-control border border-crit-fg/30 bg-crit-bg px-3 py-2 text-sm text-crit-fg">
            {formError}
          </div>
        )}

        <button
          type="submit"
          disabled={isSubmitting || !consent}
          className="w-full rounded-control bg-navy px-6 py-3 font-medium text-surface transition-colors hover:bg-navy-deep disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? r.submitting : r.submit}
        </button>
      </div>
    </form>
  );
}

const TextField = ({
  label,
  error,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string | false }) => (
  <label className="flex flex-col gap-1">
    <span className="text-sm font-medium text-ink-soft">{label}</span>
    <input
      aria-invalid={error ? true : undefined}
      className={`rounded-control border bg-surface px-3 py-2 text-body outline-none focus:border-brass focus-visible:ring-2 focus-visible:ring-terracotta ${
        error ? "border-crit-fg" : "border-line-strong"
      }`}
      {...props}
    />
    {error && <span className="text-xs text-crit-fg">{error}</span>}
  </label>
);
