"use client";

import Link from "next/link";
import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useInfiniteQuery } from "@tanstack/react-query";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import {
  getProducts,
  type CategoryResponse,
  type ProductSearchResult,
} from "@/lib/catalog/api";
import {
  filtersKey,
  filtersToSearchParams,
  hasActiveFilters,
  parseFilters,
  type CatalogFilters,
} from "@/lib/catalog/params";
import { ProductGrid, ProductGridSkeleton } from "./ProductGrid";
import { FilterSidebar } from "./FilterSidebar";

interface CatalogViewProps {
  locale: Locale;
  dict: Dictionary;
  categories: CategoryResponse[];
  initialFilters: CatalogFilters;
  initialData?: ProductSearchResult;
}

export function CatalogView({
  locale,
  dict,
  categories,
  initialFilters,
  initialData,
}: CatalogViewProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Адрес — источник правды по фильтрам: ссылкой на выборку можно поделиться, «назад» работает.
  const filters = parseFilters(Object.fromEntries(searchParams.entries()));
  const key = filtersKey(filters);
  const isInitialSelection = key === filtersKey(initialFilters);

  const query = useInfiniteQuery({
    queryKey: ["products", locale, key],
    queryFn: ({ pageParam, signal }) => getProducts(filters, pageParam, { locale, signal }),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const nextPage = (last.products?.page ?? 0) + 1;
      return nextPage < (last.products?.total_pages ?? 0) ? nextPage : undefined;
    },
    // Первая выборка приходит с сервера (SSR) — не перезапрашиваем её на клиенте.
    initialData:
      isInitialSelection && initialData
        ? { pages: [initialData], pageParams: [0] }
        : undefined,
    staleTime: 60_000,
  });

  const products = query.data?.pages.flatMap((p) => p.products?.items ?? []) ?? [];
  const facets = query.data?.pages[0]?.facets ?? initialData?.facets ?? {};
  const total = query.data?.pages[0]?.products?.total_elements ?? 0;

  function applyFilters(patch: Partial<CatalogFilters>) {
    const next = { ...filters, ...patch };
    const qs = filtersToSearchParams(next).toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }

  function resetFilters() {
    router.replace(pathname, { scroll: false });
  }

  const sidebar = (
    <FilterSidebar
      filters={filters}
      facets={facets}
      categories={categories}
      dict={dict}
      locale={locale}
      onChange={applyFilters}
      onReset={resetFilters}
      showReset={hasActiveFilters(filters)}
    />
  );

  return (
    <div className="cat">
      {/* Десктоп: фильтры сбоку, прилипают при прокрутке */}
      <aside className="cat-side">{sidebar}</aside>

      <div className="min-w-0">
        <div className="cat-bar">
          <span className="pill">
            {dict.catalog.resultsCount.replace("{count}", String(total))}
          </span>
          {/* Мобайл: кнопка открытия панели фильтров */}
          <button type="button" onClick={() => setDrawerOpen(true)} className="btn btn-sm cat-filters-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden>
              <path d="M4 6h16M7 12h10M10 18h4" />
            </svg>
            {dict.catalog.openFilters}
          </button>
        </div>

        <CatalogResults
          query={query}
          products={products}
          locale={locale}
          dict={dict}
          onReset={resetFilters}
          searchQuery={filters.q}
        />
      </div>

      {/* Мобайл: выдвижная панель фильтров */}
      {drawerOpen && (
        <div className="drawer" role="dialog" aria-modal="true" aria-label={dict.catalog.filters}>
          <button type="button" className="drawer-bg" aria-label={dict.catalog.closeFilters} onClick={() => setDrawerOpen(false)} />
          <div className="drawer-panel">
            <div className="drawer-head">
              <h2>{dict.catalog.filters}</h2>
              <button type="button" onClick={() => setDrawerOpen(false)} className="btn btn-sm">
                {dict.catalog.closeFilters}
              </button>
            </div>
            {sidebar}
          </div>
        </div>
      )}
    </div>
  );
}

function CatalogResults({
  query,
  products,
  locale,
  dict,
  onReset,
  searchQuery,
}: {
  query: ReturnType<typeof useInfiniteQuery>;
  products: import("@/lib/catalog/api").ProductSummary[];
  locale: Locale;
  dict: Dictionary;
  onReset: () => void;
  searchQuery?: string;
}) {
  if (query.isLoading) return <ProductGridSkeleton />;

  if (query.isError) {
    return (
      <div className="card empty">
        <h3>{dict.catalog.errorTitle}</h3>
        <button type="button" onClick={() => void query.refetch()} className="btn btn-red btn-sm">
          {dict.catalog.retry}
        </button>
      </div>
    );
  }

  if (products.length === 0) {
    // Пустая выдача поиска — это лид, а не тупик: предлагаем оставить заявку (F-2.3).
    if (searchQuery) {
      return (
        <div className="card empty">
          <h3>{dict.emptyLead.searchTitle.replace("{q}", searchQuery)}</h3>
          <p>{dict.emptyLead.searchBody}</p>
          <div className="row">
            <Link href={`/${locale}#contact`} className="btn btn-red btn-sm">{dict.emptyLead.cta}</Link>
            <button type="button" onClick={onReset} className="btn btn-sm">{dict.catalog.reset}</button>
          </div>
        </div>
      );
    }
    return (
      <div className="card empty">
        <h3>{dict.catalog.emptyTitle}</h3>
        <p>{dict.catalog.emptyBody}</p>
        <button type="button" onClick={onReset} className="btn btn-yellow btn-sm">{dict.catalog.reset}</button>
      </div>
    );
  }

  return (
    <div>
      <ProductGrid products={products} locale={locale} dict={dict} />
      {query.hasNextPage && (
        <div className="more-row">
          <button
            type="button"
            onClick={() => void query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="btn btn-yellow"
          >
            {query.isFetchingNextPage ? dict.catalog.loading ?? "…" : dict.catalog.showMore}
          </button>
        </div>
      )}
    </div>
  );
}
