"use client";

import { useEffect, useState } from "react";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { CategoryResponse, ProductFacets } from "@/lib/catalog/api";
import {
  USAGE_AREAS,
  FINISHES,
  BASE_TYPES,
  type CatalogFilters,
  type PaintType,
} from "@/lib/catalog/params";

type FilterPatch = Partial<CatalogFilters>;

interface FilterSidebarProps {
  filters: CatalogFilters;
  facets: ProductFacets;
  categories: CategoryResponse[];
  dict: Dictionary;
  locale: Locale;
  onChange: (patch: FilterPatch) => void;
  onReset: () => void;
  showReset: boolean;
}

export function FilterSidebar({
  filters,
  facets,
  categories,
  dict,
  onChange,
  onReset,
  showReset,
}: FilterSidebarProps) {
  const c = dict.catalog;
  const paintTypeCounts = (facets.paint_type ?? {}) as Record<string, number>;

  return (
    <div className="panel space-y-6 rounded-card p-5">
      {/* Категории */}
      <FilterGroup title={c.category}>
        <ul className="space-y-1">
          <li>
            <OptionButton
              active={filters.categoryId === undefined}
              onClick={() => onChange({ categoryId: undefined })}
              label={c.allCategories}
            />
          </li>
          {categories.map((cat) => (
            <li key={cat.id}>
              <OptionButton
                active={filters.categoryId === cat.id}
                onClick={() =>
                  onChange({ categoryId: filters.categoryId === cat.id ? undefined : cat.id })
                }
                label={cat.name ?? cat.slug ?? String(cat.id)}
                count={cat.product_count}
              />
            </li>
          ))}
        </ul>
      </FilterGroup>

      {/* Тип краски — из фасетов, со счётчиками */}
      {Object.keys(paintTypeCounts).length > 0 && (
        <FilterGroup title={c.paintType}>
          <ul className="space-y-1">
            {Object.entries(paintTypeCounts).map(([key, count]) => {
              const typedKey = key as PaintType;
              const label = dict.enums.paint_type[key as keyof Dictionary["enums"]["paint_type"]] ?? key;
              return (
                <li key={key}>
                  <OptionButton
                    active={filters.paintType === typedKey}
                    onClick={() =>
                      onChange({ paintType: filters.paintType === typedKey ? undefined : typedKey })
                    }
                    label={label}
                    count={count}
                  />
                </li>
              );
            })}
          </ul>
        </FilterGroup>
      )}

      {/* Внутри/снаружи, гимур, основа — серверные фильтры без счётчиков */}
      <FilterGroup title={c.usageArea}>
        <EnumSelect
          value={filters.usageArea}
          options={USAGE_AREAS}
          labels={dict.enums.usage_area}
          anyLabel={c.any}
          onChange={(v) => onChange({ usageArea: v })}
        />
      </FilterGroup>
      <FilterGroup title={c.finish}>
        <EnumSelect
          value={filters.finish}
          options={FINISHES}
          labels={dict.enums.finish}
          anyLabel={c.any}
          onChange={(v) => onChange({ finish: v })}
        />
      </FilterGroup>
      <FilterGroup title={c.baseType}>
        <EnumSelect
          value={filters.baseType}
          options={BASE_TYPES}
          labels={dict.enums.base_type}
          anyLabel={c.any}
          onChange={(v) => onChange({ baseType: v })}
        />
      </FilterGroup>

      {/* Цена */}
      <FilterGroup title={c.price}>
        <PriceRange
          min={filters.priceMin}
          max={filters.priceMax}
          bounds={{ min: facets.price_min, max: facets.price_max }}
          fromLabel={c.priceFrom}
          toLabel={c.priceTo}
          onChange={(next) => onChange(next)}
        />
      </FilterGroup>

      {/* Наличие */}
      <label className="flex items-center gap-2 text-sm text-ink-soft">
        <input
          type="checkbox"
          checked={filters.inStock ?? false}
          onChange={(e) => onChange({ inStock: e.target.checked ? true : undefined })}
          className="accent-brass"
        />
        {c.inStockOnly}
      </label>

      {showReset && (
        <button
          type="button"
          onClick={onReset}
          className="text-sm text-brass hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
        >
          {c.reset}
        </button>
      )}
    </div>
  );
}

function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold text-navy">{title}</h3>
      {children}
    </div>
  );
}

function OptionButton({
  active,
  onClick,
  label,
  count,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  count?: number;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`flex w-full items-center justify-between gap-2 rounded-control px-2 py-1 text-start text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta ${
        active ? "bg-brass/10 font-medium text-navy" : "text-ink-soft hover:bg-paper-warm"
      }`}
    >
      <span>{label}</span>
      {count !== undefined && <span className="numeric text-xs text-ink-faint">{count}</span>}
    </button>
  );
}

function EnumSelect<T extends string>({
  value,
  options,
  labels,
  anyLabel,
  onChange,
}: {
  value: T | undefined;
  options: readonly T[];
  labels: Record<string, string>;
  anyLabel: string;
  onChange: (value: T | undefined) => void;
}) {
  return (
    <select
      value={value ?? ""}
      onChange={(e) => onChange((e.target.value || undefined) as T | undefined)}
      className="w-full rounded-control border border-line-strong bg-surface px-2 py-1.5 text-sm text-ink focus:border-brass focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
    >
      <option value="">{anyLabel}</option>
      {options.map((opt) => (
        <option key={opt} value={opt}>
          {labels[opt] ?? opt}
        </option>
      ))}
    </select>
  );
}

function PriceRange({
  min,
  max,
  bounds,
  fromLabel,
  toLabel,
  onChange,
}: {
  min?: number;
  max?: number;
  bounds: { min?: number; max?: number };
  fromLabel: string;
  toLabel: string;
  onChange: (patch: FilterPatch) => void;
}) {
  // Локальное состояние, чтобы не слать запрос на каждый набор цифры; коммит по blur/Enter.
  const [localMin, setLocalMin] = useState(min?.toString() ?? "");
  const [localMax, setLocalMax] = useState(max?.toString() ?? "");

  useEffect(() => setLocalMin(min?.toString() ?? ""), [min]);
  useEffect(() => setLocalMax(max?.toString() ?? ""), [max]);

  function commit() {
    const nextMin = localMin === "" ? undefined : Number(localMin);
    const nextMax = localMax === "" ? undefined : Number(localMax);
    onChange({
      priceMin: Number.isFinite(nextMin) ? nextMin : undefined,
      priceMax: Number.isFinite(nextMax) ? nextMax : undefined,
    });
  }

  return (
    <div className="flex items-center gap-2">
      <input
        type="number"
        inputMode="numeric"
        aria-label={fromLabel}
        placeholder={bounds.min !== undefined ? String(Math.floor(bounds.min)) : fromLabel}
        value={localMin}
        onChange={(e) => setLocalMin(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => e.key === "Enter" && commit()}
        className="numeric w-full rounded-control border border-line-strong bg-surface px-2 py-1.5 text-sm focus:border-brass focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
      />
      <span className="text-ink-faint">–</span>
      <input
        type="number"
        inputMode="numeric"
        aria-label={toLabel}
        placeholder={bounds.max !== undefined ? String(Math.ceil(bounds.max)) : toLabel}
        value={localMax}
        onChange={(e) => setLocalMax(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => e.key === "Enter" && commit()}
        className="numeric w-full rounded-control border border-line-strong bg-surface px-2 py-1.5 text-sm focus:border-brass focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
      />
    </div>
  );
}
