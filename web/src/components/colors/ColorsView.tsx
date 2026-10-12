"use client";

import { useMemo, useState, type CSSProperties } from "react";
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
      <div className="card pf-bar">
        <div>
          <p className="lbl">{c.system}</p>
          <div className="opt-list" role="group" aria-label={c.system}>
            <button type="button" className="opt" aria-pressed={system === ""} onClick={() => setSystem("")}>{c.allSystems}</button>
            {availableSystems.map((s) => (
              <button
                key={s}
                type="button"
                className="opt"
                aria-pressed={system === s}
                onClick={() => {
                  setSystem(s);
                  setCollection("");
                }}
              >
                {c.systems[s]}
              </button>
            ))}
          </div>
        </div>

        {collections.length > 0 && (
          <label className="field">
            <span className="field-l">{c.collection}</span>
            <select value={collection} onChange={(e) => setCollection(e.target.value)}>
              <option value="">{c.allCollections}</option>
              {collections.map((col) => <option key={col} value={col}>{col}</option>)}
            </select>
          </label>
        )}

        <label className="field">
          <span className="field-l">{c.searchCode}</span>
          <input type="search" dir="ltr" value={q} onChange={(e) => setQ(e.target.value)} placeholder="RAL…" />
        </label>

        <label className="chk">
          <input type="checkbox" checked={popularOnly} onChange={(e) => setPopularOnly(e.target.checked)} />
          {c.popularOnly}
        </label>

        {hasFilters && <button type="button" onClick={reset} className="link-btn">{c.reset}</button>}
      </div>

      {filtered.length === 0 ? (
        <div className="card empty">
          <h3>{c.empty}</h3>
          {hasFilters && <button type="button" onClick={reset} className="btn btn-yellow btn-sm">{c.reset}</button>}
        </div>
      ) : (
        <div className="chips">
          {filtered.map((col) => (
            <Link
              key={col.id}
              href={`/${locale}/products?q=${encodeURIComponent(col.code ?? "")}`}
              title={c.toCatalog}
              className="card lift chip-tile"
            >
              <div className="swatch" style={{ "--c": col.hex ?? "var(--paper-warm)" } as CSSProperties} />
              <div className="cap">
                <b><bdi>{col.code}</bdi></b>
                {col.name && <span>{col.name}</span>}
                {col.collection && <span>{col.collection}</span>}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
