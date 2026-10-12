"use client";

import { useState, type CSSProperties, type SyntheticEvent } from "react";
import Link from "next/link";
import { useQuery, keepPreviousData } from "@tanstack/react-query";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import {
  getPortfolioList,
  PROPERTY_TYPES,
  WORK_TYPES,
  type PortfolioCard,
  type PortfolioPage,
  type PortfolioFilters,
} from "@/lib/portfolio/api";

/**
 * Сетка портфолио с фильтрами по типу объекта, виду работ и городу. Виды работ есть только в детали
 * проекта, поэтому фильтрация идёт на сервере (GET /portfolio с параметрами). Первая страница без
 * фильтров приходит из SSR (initialData) — мгновенный первый рендер, дальше запросы по мере фильтров.
 */
export function PortfolioView({
  initial,
  cities,
  locale,
  dict,
}: {
  initial: PortfolioPage;
  cities: string[];
  locale: Locale;
  dict: Dictionary;
}) {
  const t = dict.portfolio;
  const [filters, setFilters] = useState<PortfolioFilters>({});
  const isInitial = !filters.property_type && !filters.work_type && !filters.city;

  const query = useQuery({
    queryKey: ["portfolio", locale, filters],
    queryFn: ({ signal }) => getPortfolioList(filters, 0, { locale, signal }),
    initialData: isInitial ? initial : undefined,
    placeholderData: keepPreviousData,
  });

  const items = query.data?.items ?? [];
  const hasFilter = !isInitial;

  function set<K extends keyof PortfolioFilters>(k: K, v: string) {
    setFilters((f) => ({ ...f, [k]: v || undefined }));
  }

  return (
    <div>
      <div className="card pf-bar">
        <label className="field">
          <span className="field-l">{t.filterType}</span>
          <select value={filters.property_type ?? ""} onChange={(e) => set("property_type", e.target.value)}>
            <option value="">{t.allTypes}</option>
            {PROPERTY_TYPES.map((p) => <option key={p} value={p}>{dict.enums.property_type[p]}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-l">{t.filterWork}</span>
          <select value={filters.work_type ?? ""} onChange={(e) => set("work_type", e.target.value)}>
            <option value="">{t.allWorks}</option>
            {WORK_TYPES.map((w) => <option key={w} value={w}>{dict.enums.work_type[w]}</option>)}
          </select>
        </label>
        <label className="field">
          <span className="field-l">{t.filterCity}</span>
          <select value={filters.city ?? ""} onChange={(e) => set("city", e.target.value)}>
            <option value="">{t.allCities}</option>
            {cities.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        {hasFilter && (
          <button type="button" onClick={() => setFilters({})} className="btn btn-sm">{t.reset}</button>
        )}
      </div>

      {items.length === 0 ? (
        <div className="card empty">
          <h3>{t.empty}</h3>
          <p>{t.emptyBody}</p>
          <div className="row">
            <Link href={`/${locale}/services/measure`} className="btn btn-red btn-sm">{t.toMeasure}</Link>
            {hasFilter && <button type="button" onClick={() => setFilters({})} className="btn btn-sm">{t.reset}</button>}
          </div>
        </div>
      ) : (
        <ul className="pf-grid">
          {items.map((p, i) => (
            <Tile key={p.id} project={p} locale={locale} dict={dict} color={TILE_COLORS[i % TILE_COLORS.length] ?? "var(--yellow)"} />
          ))}
        </ul>
      )}
    </div>
  );
}

/** Не загрузилось — прячем: остаётся цветная подложка, а не значок битой картинки с alt-текстом. */
function hideBroken(e: SyntheticEvent<HTMLImageElement>) {
  e.currentTarget.style.visibility = "hidden";
}

/** Подложка под фото, пока оно грузится или если его нет. */
const TILE_COLORS = ["var(--yellow)", "var(--mint)", "var(--pink)", "var(--blue)", "var(--violet)"];

function Tile({ project, locale, dict, color }: { project: PortfolioCard; locale: Locale; dict: Dictionary; color: string }) {
  const t = dict.portfolio;
  const type = (project.property_type ?? "").toUpperCase() as keyof typeof dict.enums.property_type;
  return (
    <li>
      <Link href={`/${locale}/portfolio/${project.slug}`} className="card lift pf-tile">
        <div className="pf-img" style={{ "--c": color } as CSSProperties}>
          {project.main_after_url ? (
            // eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио (CDN не в whitelist next/image)
            <img src={project.main_after_url} alt={project.title ?? ""} loading="lazy" onError={hideBroken} />
          ) : null}
        </div>
        <div className="pf-body">
          <div className="pf-chips">
            {dict.enums.property_type[type] ? <span className="pill y">{dict.enums.property_type[type]}</span> : null}
            {project.city ? <span className="pill">{project.city}</span> : null}
          </div>
          <h2>{project.title}</h2>
          <dl className="pf-meta">
            {project.area_sqm != null ? (
              <div><dt>{t.area}:</dt><dd><bdi>{project.area_sqm} {t.areaUnit}</bdi></dd></div>
            ) : null}
            {project.duration_days != null ? (
              <div><dd>{t.duration.replace("{days}", String(project.duration_days))}</dd></div>
            ) : null}
          </dl>
        </div>
      </Link>
    </li>
  );
}
