"use client";

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
    <div className="flex flex-col gap-6 lg:flex-row lg:items-start">
      {/* Десктоп: фильтры сбоку */}
      <aside className="hidden w-64 shrink-0 lg:block">{sidebar}</aside>

      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center justify-between gap-3">
          <p className="text-sm text-ink-soft">
            {dict.catalog.resultsCount.replace("{count}", String(total))}
          </p>
          {/* Мобайл: кнопка открытия панели фильтров */}
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            className="rounded-control border border-line-strong px-3 py-2 text-sm text-ink-soft lg:hidden"
          >
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
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-navy-deep/40" onClick={() => setDrawerOpen(false)} />
          <div className="absolute inset-inline-start-0 top-0 h-full w-80 max-w-[85%] overflow-y-auto bg-paper p-4 shadow-card">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-h3 font-bold text-navy">{dict.catalog.filters}</h2>
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                className="rounded-control px-3 py-1 text-sm text-ink-soft hover:bg-paper-warm"
              >
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
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <p className="text-h3 text-navy">{dict.catalog.errorTitle}</p>
        <button
          type="button"
          onClick={() => void query.refetch()}
          className="mt-4 rounded-control bg-navy px-4 py-2 text-sm text-surface hover:bg-navy-deep"
        >
          {dict.catalog.retry}
        </button>
      </div>
    );
  }

  if (products.length === 0) {
    // Пустая выдача поиска — это лид, а не тупик: предлагаем оставить заявку (F-2.3).
    if (searchQuery) {
      return (
        <div className="rounded-card border border-line bg-surface p-12 text-center shadow-card">
          <p className="text-h3 text-navy">{dict.emptyLead.searchTitle.replace("{q}", searchQuery)}</p>
          <p className="mx-auto mt-2 max-w-md text-ink-soft">{dict.emptyLead.searchBody}</p>
          <div className="mt-4 flex flex-wrap justify-center gap-3">
            <a
              href="tel:+972000000000"
              className="rounded-control bg-brass px-5 py-2 text-sm font-medium text-surface hover:bg-brass-light"
            >
              {dict.emptyLead.cta}
            </a>
            <button
              type="button"
              onClick={onReset}
              className="rounded-control border border-line-strong px-4 py-2 text-sm text-ink-soft hover:bg-paper-warm"
            >
              {dict.catalog.reset}
            </button>
          </div>
        </div>
      );
    }
    return (
      <div className="rounded-card border border-line bg-surface p-12 text-center shadow-card">
        <p className="text-h3 text-navy">{dict.catalog.emptyTitle}</p>
        <p className="mt-2 text-ink-soft">{dict.catalog.emptyBody}</p>
        <button
          type="button"
          onClick={onReset}
          className="mt-4 rounded-control border border-brass px-4 py-2 text-sm text-brass hover:bg-brass hover:text-surface"
        >
          {dict.catalog.reset}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <ProductGrid products={products} locale={locale} dict={dict} />
      {query.hasNextPage && (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={() => void query.fetchNextPage()}
            disabled={query.isFetchingNextPage}
            className="rounded-control bg-navy px-6 py-3 text-sm font-medium text-surface transition-colors hover:bg-navy-deep disabled:opacity-50"
          >
            {query.isFetchingNextPage ? dict.catalog.loading ?? "…" : dict.catalog.showMore}
          </button>
        </div>
      )}
    </div>
  );
}
