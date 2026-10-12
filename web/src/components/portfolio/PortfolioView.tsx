"use client";

import { useState } from "react";
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
      <div className="mb-6 flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.filterType}
          <select
            value={filters.property_type ?? ""}
            onChange={(e) => set("property_type", e.target.value)}
            className="min-w-[10rem] rounded-input border border-line bg-surface px-3 py-2 text-ink"
          >
            <option value="">{t.allTypes}</option>
            {PROPERTY_TYPES.map((p) => (
              <option key={p} value={p}>
                {dict.enums.property_type[p]}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.filterWork}
          <select
            value={filters.work_type ?? ""}
            onChange={(e) => set("work_type", e.target.value)}
            className="min-w-[10rem] rounded-input border border-line bg-surface px-3 py-2 text-ink"
          >
            <option value="">{t.allWorks}</option>
            {WORK_TYPES.map((w) => (
              <option key={w} value={w}>
                {dict.enums.work_type[w]}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm text-ink-soft">
          {t.filterCity}
          <select
            value={filters.city ?? ""}
            onChange={(e) => set("city", e.target.value)}
            className="min-w-[10rem] rounded-input border border-line bg-surface px-3 py-2 text-ink"
          >
            <option value="">{t.allCities}</option>
            {cities.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>

        {hasFilter && (
          <button
            type="button"
            onClick={() => setFilters({})}
            className="rounded-pill border border-line px-4 py-2 text-sm text-ink-soft hover:text-ink"
          >
            {t.reset}
          </button>
        )}
      </div>

      {items.length === 0 ? (
        <p className="py-16 text-center text-ink-soft">{t.empty}</p>
      ) : (
        <ul className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((p) => (
            <Tile key={p.id} project={p} locale={locale} dict={dict} />
          ))}
        </ul>
      )}
    </div>
  );
}

function Tile({ project, locale, dict }: { project: PortfolioCard; locale: Locale; dict: Dictionary }) {
  const t = dict.portfolio;
  const type = (project.property_type ?? "").toUpperCase() as keyof typeof dict.enums.property_type;
  return (
    <li className="group overflow-hidden rounded-card border border-line bg-surface shadow-card transition hover:shadow-lift">
      <Link href={`/${locale}/portfolio/${project.slug}`} className="block">
        <div className="relative aspect-[4/3] w-full overflow-hidden bg-paper-warm">
          {project.main_after_url ? (
            // eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио (CDN не в whitelist next/image)
            <img
              src={project.main_after_url}
              alt={project.title ?? ""}
              loading="lazy"
              className="h-full w-full object-cover transition group-hover:scale-[1.03]"
            />
          ) : null}
        </div>
        <div className="p-4">
          <div className="mb-2 flex flex-wrap gap-2 text-xs">
            {dict.enums.property_type[type] ? (
              <span className="rounded-pill bg-navy-deep/8 px-2 py-0.5 text-navy">{dict.enums.property_type[type]}</span>
            ) : null}
            {project.city ? <span className="rounded-pill bg-navy-deep/8 px-2 py-0.5 text-navy">{project.city}</span> : null}
          </div>
          <h2 className="text-base font-semibold text-ink">{project.title}</h2>
          <dl className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-ink-soft">
            {project.area_sqm != null ? (
              <div>
                <dt className="inline">{t.area}: </dt>
                <dd className="inline font-medium text-ink">
                  {project.area_sqm} {t.areaUnit}
                </dd>
              </div>
            ) : null}
            {project.duration_days != null ? (
              <div>
                <dd className="inline">{t.duration.replace("{days}", String(project.duration_days))}</dd>
              </div>
            ) : null}
          </dl>
        </div>
      </Link>
    </li>
  );
}
