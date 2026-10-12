"use client";

import Link from "next/link";
import { useState, type CSSProperties, type MouseEvent, type ReactNode } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ProductCard, VariantCard } from "@/lib/catalog/api";
import { formatPrice } from "@/lib/format";
import { useRequest } from "@/lib/request/RequestProvider";
import { Gallery } from "./Gallery";
import { CoverageCalculator } from "./CoverageCalculator";
import { useMotion } from "@/components/motion/MotionProvider";

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
  const { burst, say } = useMotion();

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

  function onAdd(e: MouseEvent<HTMLButtonElement>) {
    addToRequest();
    const r = e.currentTarget.getBoundingClientRect();
    burst(r.left + r.width / 2, r.top + r.height / 2, 26, [selected?.color_hex ?? "#e8322b", "#ffc933", "#121212"], 0.8);
    say(p.addedToast.replace("{name}", card.name ?? card.sku ?? ""));
  }

  return (
    <div>
      <div className="pd">
        <Gallery media={media} alt={card.name ?? card.sku ?? ""} color={selected?.color_hex ?? null} />

        <div className="card buy">
          {colors.length > 1 && (
            <Field label={p.color}>
              <div className="buy-swatches">
                {colors.map((v) => {
                  const isActive = (selected?.color_code ?? "") === (v.color_code ?? "");
                  return (
                    <button
                      key={v.color_code ?? v.id}
                      type="button"
                      className="sw"
                      onClick={() => pickColor(v.color_code ?? null)}
                      aria-pressed={isActive}
                      aria-label={v.color_code ?? undefined}
                      title={v.color_code ?? undefined}
                      style={{ "--c": v.color_hex ?? "var(--paper)" } as CSSProperties}
                    />
                  );
                })}
              </div>
            </Field>
          )}

          {selected && volumesForColor(selected.color_code ?? null).length > 1 && (
            <Field label={p.volume}>
              <div className="opt-list">
                {volumesForColor(selected.color_code ?? null).map((v) => (
                  <button
                    key={v.id}
                    type="button"
                    className="opt"
                    onClick={() => pickVolume(String(v.volume_liters ?? ""))}
                    aria-pressed={selected.volume_liters === v.volume_liters}
                  >
                    <bdi>{String(v.volume_liters)}</bdi> {p.liters}
                  </button>
                ))}
              </div>
            </Field>
          )}

          {selected && (
            <div className="buy-price">
              <span className="now"><bdi>{formatPrice(locale, selected.price ?? 0)}</bdi></span>
              {selected.old_price != null && (
                <span className="old"><bdi>{formatPrice(locale, selected.old_price)}</bdi></span>
              )}
              <AvailabilityBadge availability={selected.availability} dict={dict} />
            </div>
          )}

          <div style={{ display: "grid", gap: 10 }}>
            <button
              type="button"
              onClick={onAdd}
              disabled={!selected || !selected.can_add_to_request || inRequest}
              className="btn btn-red"
            >
              {inRequest ? p.inRequest : p.addToRequest}
            </button>
            {inRequest && (
              <Link href={`/${locale}/request`} className="btn btn-yellow">{p.toRequest}</Link>
            )}
          </div>

          <Characteristics card={card} dict={dict} />
        </div>
      </div>

      <div className="pd-more">
        <CoverageCalculator slug={card.slug ?? ""} locale={locale} dict={dict} selectedVariantId={selected?.id ?? null} />
        {card.description && (
          <section className="card pd-desc">
            <h2>{p.description}</h2>
            <p>{card.description}</p>
          </section>
        )}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="lbl">{label}</p>
      {children}
    </div>
  );
}

function AvailabilityBadge({ availability, dict }: { availability?: string; dict: Dictionary }) {
  const key = (availability ?? "out_of_stock") as keyof Dictionary["product"]["availability"];
  const isOk = availability === "many";
  const isFew = availability === "few";
  return (
    <span className={`avail${isOk ? "" : isFew ? " few" : " out"}`}>
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
    <dl className="specs">
      {rows.map((r) => (
        <div key={r.label}>
          <dt>{r.label}</dt>
          <dd>{r.value}</dd>
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
