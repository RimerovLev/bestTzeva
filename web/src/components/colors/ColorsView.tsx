"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import type { ColorSwatch } from "@/lib/catalog/api";

const SYSTEMS = ["RAL", "NCS", "INTERNAL"] as const;
type System = (typeof SYSTEMS)[number];

/**
 * Палитра цветов: фильтр по системе и коллекции, поиск по коду. Справочный набор загружается один
 * раз (SSR) и фильтруется на клиенте — без сетевого запроса на каждый ввод. Клик по цвету ведёт в
 * каталог (пока поиском по коду: у GET /products нет параметра цвета — см. заметку задачи).
 */
export function ColorsView({
  colors,
  locale,
  dict,
}: {
  colors: ColorSwatch[];
  locale: Locale;
  dict: Dictionary;
}) {
  const c = dict.colors;
  const [system, setSystem] = useState<System | "">("");
  const [collection, setCollection] = useState("");
  const [q, setQ] = useState("");
  const [popularOnly, setPopularOnly] = useState(false);

  const availableSystems = useMemo(
    () => SYSTEMS.filter((s) => colors.some((col) => (col.system ?? "").toUpperCase() === s)),
    [colors],
  );

  const collections = useMemo(() => {
    const set = new Set<string>();
    for (const col of colors) {
      if ((system === "" || (col.system ?? "").toUpperCase() === system) && col.collection) {
        set.add(col.collection);
      }
    }
    return [...set].sort();
  }, [colors, system]);

  const filtered = useMemo(() => {
    const query = q.trim().toLowerCase();
    return colors.filter((col) => {
      if (system && (col.system ?? "").toUpperCase() !== system) return false;
      if (collection && col.collection !== collection) return false;
      if (popularOnly && !col.popular) return false;
      if (query) {
        const hay = `${col.code ?? ""} ${col.name ?? ""}`.toLowerCase();
        if (!hay.includes(query)) return false;
      }
      return true;
    });
  }, [colors, system, collection, q, popularOnly]);

  const hasFilters = system !== "" || collection !== "" || q !== "" || popularOnly;

  function reset() {
    setSystem("");
    setCollection("");
    setQ("");
    setPopularOnly(false);
  }

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end gap-4">
        <div>
          <p className="mb-1 text-sm font-medium text-ink-soft">{c.system}</p>
          <div className="flex gap-1">
            <SystemButton active={system === ""} onClick={() => setSystem("")} label={c.allSystems} />
            {availableSystems.map((s) => (
              <SystemButton
                key={s}
                active={system === s}
                onClick={() => {
                  setSystem(s);
                  setCollection("");
                }}
                label={c.systems[s]}
              />
            ))}
          </div>
        </div>

        {collections.length > 0 && (
          <label className="flex flex-col gap-1">
            <span className="text-sm font-medium text-ink-soft">{c.collection}</span>
            <select
              value={collection}
              onChange={(e) => setCollection(e.target.value)}
              className="rounded-control border border-line-strong bg-surface px-2 py-1.5 text-sm focus:border-brass focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
            >
              <option value="">{c.allCollections}</option>
              {collections.map((col) => (
                <option key={col} value={col}>{col}</option>
              ))}
            </select>
          </label>
        )}

        <label className="flex flex-col gap-1">
          <span className="text-sm font-medium text-ink-soft">{c.searchCode}</span>
          <input
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="RAL…"
            className="rounded-control border border-line-strong bg-surface px-3 py-1.5 text-sm focus:border-brass focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
          />
        </label>

        <label className="flex items-center gap-2 text-sm text-ink-soft">
          <input type="checkbox" checked={popularOnly} onChange={(e) => setPopularOnly(e.target.checked)} className="accent-brass" />
          {c.popularOnly}
        </label>

        {hasFilters && (
          <button type="button" onClick={reset} className="text-sm text-brass hover:underline">
            {c.reset}
          </button>
        )}
      </div>

      {filtered.length === 0 ? (
        <p className="rounded-card border border-line bg-surface p-12 text-center text-ink-faint shadow-card">
          {c.empty}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
          {filtered.map((col) => (
            <Link
              key={col.id}
              href={`/${locale}/products?q=${encodeURIComponent(col.code ?? "")}`}
              title={c.toCatalog}
              className="group overflow-hidden rounded-card border border-line bg-surface shadow-card transition-shadow hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
            >
              <div className="aspect-[4/3]" style={{ backgroundColor: col.hex ?? "var(--line)" }} />
              <div className="p-2">
                <p className="numeric text-sm font-medium text-navy">{col.code}</p>
                <p className="truncate text-xs text-ink-faint">{col.collection}</p>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

function SystemButton({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-control border px-3 py-1.5 text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta ${
        active ? "border-brass bg-brass/10 font-medium text-navy" : "border-line-strong text-ink-soft hover:bg-paper-warm"
      }`}
    >
      {label}
    </button>
  );
}
