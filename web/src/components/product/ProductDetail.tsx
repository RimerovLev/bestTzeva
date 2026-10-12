"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ProductCard, VariantCard } from "@/lib/catalog/api";
import { formatPrice } from "@/lib/format";
import { useRequest } from "@/lib/request/RequestProvider";
import { Gallery } from "./Gallery";
import { CoverageCalculator } from "./CoverageCalculator";

type EnumKey<T extends keyof Dictionary["enums"]> = keyof Dictionary["enums"][T];

export function ProductDetail({
  card,
  locale,
  dict,
}: {
  card: ProductCard;
  locale: Locale;
  dict: Dictionary;
}) {
  const p = dict.product;
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const request = useRequest();

  const variants = card.variants ?? [];
  const paramVariant = Number(searchParams.get("variant"));
  const initial =
    variants.find((v) => v.id === paramVariant) ?? variants[0] ?? null;
  const [selectedId, setSelectedId] = useState<number | null>(initial?.id ?? null);
  const selected = variants.find((v) => v.id === selectedId) ?? initial;

  function selectVariant(id: number | undefined) {
    if (id == null) return;
    setSelectedId(id);
    const sp = new URLSearchParams(searchParams);
    sp.set("variant", String(id));
    router.replace(`${pathname}?${sp.toString()}`, { scroll: false });
  }

  // Дименсии выбора: уникальные цвета и объёмы.
  const colors = uniqueBy(variants, (v) => v.color_code ?? "");
  const volumesForColor = (colorCode: string | null) =>
    uniqueBy(
      variants.filter((v) => (v.color_code ?? "") === (colorCode ?? "")),
      (v) => String(v.volume_liters ?? ""),
    );

  function pickColor(colorCode: string | null) {
    const sameVolume = variants.find(
      (v) => (v.color_code ?? "") === (colorCode ?? "") && v.volume_liters === selected?.volume_liters,
    );
    const target = sameVolume ?? variants.find((v) => (v.color_code ?? "") === (colorCode ?? ""));
    if (target) selectVariant(target.id);
  }

  function pickVolume(volume: string) {
    const target = variants.find(
      (v) => (v.color_code ?? "") === (selected?.color_code ?? "") && String(v.volume_liters ?? "") === volume,
    );
    if (target) selectVariant(target.id);
  }

  const media = selected?.media?.length ? selected.media : card.media ?? [];
  const inRequest = selected?.id != null ? request.has(selected.id) : false;

  function addToRequest() {
    if (!selected || selected.id == null) return;
    request.add({
      productId: card.id ?? 0,
      slug: card.slug ?? "",
      variantId: selected.id,
      name: card.name ?? card.sku ?? "",
      colorCode: selected.color_code ?? null,
      colorHex: selected.color_hex ?? null,
      volumeLiters: selected.volume_liters ?? null,
      price: selected.price ?? null,
    });
  }

  return (
    <div className="space-y-10">
      <div className="grid gap-8 lg:grid-cols-2">
        <Gallery media={media} alt={card.name ?? card.sku ?? ""} />

        <div className="flex flex-col gap-4">
          <div>
            <h1 className="display text-h1 font-bold text-navy-deep">{card.name}</h1>
            <p className="mt-1 text-sm text-ink-faint">
              <bdi className="numeric">{card.sku}</bdi>
            </p>
          </div>

          {card.short_description && <p className="text-ink-soft">{card.short_description}</p>}

          {colors.length > 1 && (
            <Field label={p.color}>
              <div className="flex flex-wrap gap-2">
                {colors.map((v) => {
                  const isActive = (selected?.color_code ?? "") === (v.color_code ?? "");
                  return (
                    <button
                      key={v.color_code ?? v.id}
                      type="button"
                      onClick={() => pickColor(v.color_code ?? null)}
                      aria-pressed={isActive}
                      title={v.color_code ?? undefined}
                      className={`h-9 w-9 rounded-pill border-2 transition-transform ${
                        isActive ? "border-brass scale-110" : "border-line hover:border-line-strong"
                      }`}
                      style={{ backgroundColor: v.color_hex ?? "var(--line)" }}
                    />
                  );
                })}
              </div>
            </Field>
          )}

          {selected && volumesForColor(selected.color_code ?? null).length > 1 && (
            <Field label={p.volume}>
              <div className="flex flex-wrap gap-2">
                {volumesForColor(selected.color_code ?? null).map((v) => {
                  const isActive = selected.volume_liters === v.volume_liters;
                  return (
                    <button
                      key={v.id}
                      type="button"
                      onClick={() => pickVolume(String(v.volume_liters ?? ""))}
                      aria-pressed={isActive}
                      className={`rounded-control border px-3 py-1.5 text-sm transition-colors ${
                        isActive ? "border-brass bg-brass/10 font-medium text-navy" : "border-line-strong text-ink-soft hover:bg-paper-warm"
                      }`}
                    >
                      <bdi className="numeric">{String(v.volume_liters)}</bdi> {p.liters}
                    </button>
                  );
                })}
              </div>
            </Field>
          )}

          {selected && (
            <div className="flex items-center gap-4">
              <span className="text-display font-bold text-navy">
                <bdi className="numeric">{formatPrice(locale, selected.price ?? 0)}</bdi>
              </span>
              {selected.old_price != null && (
                <span className="text-h3 text-ink-faint line-through">
                  <bdi className="numeric">{formatPrice(locale, selected.old_price)}</bdi>
                </span>
              )}
              <AvailabilityBadge availability={selected.availability} dict={dict} />
            </div>
          )}

          <button
            type="button"
            onClick={addToRequest}
            disabled={!selected || !selected.can_add_to_request || inRequest}
            className="mt-2 w-full rounded-control bg-brass px-6 py-3 font-medium text-surface transition-colors hover:bg-brass-light disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
          >
            {inRequest ? p.inRequest : p.addToRequest}
          </button>

          <Characteristics card={card} dict={dict} />
        </div>
      </div>

      <CoverageCalculator slug={card.slug ?? ""} locale={locale} dict={dict} selectedVariantId={selected?.id ?? null} />

      {card.description && (
        <section className="max-w-3xl">
          <h2 className="text-h2 font-bold text-navy">{card.name}</h2>
          <p className="mt-3 whitespace-pre-line text-ink-soft">{card.description}</p>
        </section>
      )}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="mb-1 text-sm font-medium text-ink-soft">{label}</p>
      {children}
    </div>
  );
}

function AvailabilityBadge({ availability, dict }: { availability?: string; dict: Dictionary }) {
  const key = (availability ?? "out_of_stock") as keyof Dictionary["product"]["availability"];
  const isOk = availability === "many";
  const isFew = availability === "few";
  return (
    <span
      className={`rounded-pill px-3 py-1 text-xs ${
        isOk ? "bg-ok-bg text-ok-fg" : isFew ? "bg-warn-bg text-warn-fg" : "bg-paper-warm text-ink-soft"
      }`}
    >
      {dict.product.availability[key] ?? dict.product.availability.out_of_stock}
    </span>
  );
}

function Characteristics({ card, dict }: { card: ProductCard; dict: Dictionary }) {
  const p = dict.product;
  const rows: { label: string; value: string }[] = [];
  const paint = card.paint_type?.toUpperCase() as EnumKey<"paint_type"> | undefined;
  const usage = card.usage_area?.toUpperCase() as EnumKey<"usage_area"> | undefined;
  const finish = card.finish?.toUpperCase() as EnumKey<"finish"> | undefined;
  const base = card.base_type?.toUpperCase() as EnumKey<"base_type"> | undefined;

  if (paint) rows.push({ label: p.paintType, value: dict.enums.paint_type[paint] });
  if (usage) rows.push({ label: p.usageArea, value: dict.enums.usage_area[usage] });
  if (finish) rows.push({ label: p.finish, value: dict.enums.finish[finish] });
  if (base) rows.push({ label: p.baseType, value: dict.enums.base_type[base] });
  if (card.drying_time_hours != null) {
    rows.push({ label: p.dryingTime, value: `${card.drying_time_hours} ${p.hours}` });
  }
  if (card.coverage_sqm_per_liter != null) {
    rows.push({ label: p.coverageLabel, value: `${card.coverage_sqm_per_liter} ${p.coverageUnit}` });
  }

  if (rows.length === 0) return null;

  return (
    <dl className="mt-2 grid grid-cols-1 gap-x-6 gap-y-2 rounded-card border border-line bg-surface p-4 sm:grid-cols-2">
      {rows.map((r) => (
        <div key={r.label} className="flex justify-between gap-3 border-b border-line/60 py-1 last:border-0">
          <dt className="text-sm text-ink-soft">{r.label}</dt>
          <dd className="text-sm font-medium text-ink">{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Уникальные варианты по ключу (первый встреченный). */
function uniqueBy(variants: VariantCard[], key: (v: VariantCard) => string): VariantCard[] {
  const seen = new Set<string>();
  const out: VariantCard[] = [];
  for (const v of variants) {
    const k = key(v);
    if (!seen.has(k)) {
      seen.add(k);
      out.push(v);
    }
  }
  return out;
}
