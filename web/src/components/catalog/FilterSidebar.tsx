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
    <div className="card flt">
      {/* Категории */}
      <FilterGroup title={c.category}>
        <ul className="opt-list">
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
          <ul className="opt-list">
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
      <label className="chk">
        <input
          type="checkbox"
          checked={filters.inStock ?? false}
          onChange={(e) => onChange({ inStock: e.target.checked ? true : undefined })}
        />
        {c.inStockOnly}
      </label>

      {showReset && (
        <button type="button" onClick={onReset} className="link-btn">
          {c.reset}
        </button>
      )}
    </div>
  );
}

function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="flt-h">{title}</h3>
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
    <button type="button" onClick={onClick} aria-pressed={active} className="opt">
      <span>{label}</span>
      {count !== undefined && <bdi className="cnt">{count}</bdi>}
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
      className="inp"
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
    <div className="inp-row">
      <input
        type="number"
        inputMode="numeric"
        aria-label={fromLabel}
        placeholder={bounds.min !== undefined ? String(Math.floor(bounds.min)) : fromLabel}
        value={localMin}
        onChange={(e) => setLocalMin(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => e.key === "Enter" && commit()}
        className="inp" dir="ltr"
      />
      <span aria-hidden>–</span>
      <input
        type="number"
        inputMode="numeric"
        aria-label={toLabel}
        placeholder={bounds.max !== undefined ? String(Math.ceil(bounds.max)) : toLabel}
        value={localMax}
        onChange={(e) => setLocalMax(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => e.key === "Enter" && commit()}
        className="inp" dir="ltr"
      />
    </div>
  );
}
