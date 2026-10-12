import type { Metadata } from "next";
import { pageMeta } from "@/lib/seo";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import {
  getProducts,
  getCategories,
  type CategoryResponse,
  type ProductSearchResult,
} from "@/lib/catalog/api";
import { parseFilters } from "@/lib/catalog/params";
import { CatalogView } from "@/components/catalog/CatalogView";
import { PageBand } from "@/components/motion/PageBand";

type SearchParams = Record<string, string | string[] | undefined>;

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return pageMeta({ locale, path: "/products", siteName: dict.meta.siteName, title: dict.catalog.title, description: dict.catalog.subtitle });
}

export default async function ProductsPage({
  params,
  searchParams,
}: {
  params: Promise<{ locale: string }>;
  searchParams: Promise<SearchParams>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();

  const [dict, sp] = await Promise.all([getDictionary(locale), searchParams]);
  const filters = parseFilters(sp);

  // Первая страница и категории — с сервера (SSR ради индексации названий красок). Ошибку
  // серверной загрузки не роняем: клиент дозагрузит и покажет состояние ошибки с повтором.
  let initialData: ProductSearchResult | undefined;
  let categories: CategoryResponse[] = [];
  try {
    [initialData, categories] = await Promise.all([
      getProducts(filters, 0, { locale, revalidate: 300 }),
      getCategories({ locale, revalidate: 3600 }),
    ]);
  } catch {
    try {
      categories = await getCategories({ locale, revalidate: 3600 });
    } catch {
      categories = [];
    }
  }

  return (
    <section>
      <PageBand tone="yellow" eyebrow={dict.nav.prices} title={dict.catalog.heading} body={dict.catalog.subtitle} />
      <CatalogView
        locale={locale}
        dict={dict}
        categories={categories}
        initialFilters={filters}
        initialData={initialData}
      />
    </section>
  );
}
