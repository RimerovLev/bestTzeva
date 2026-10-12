import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getPortfolioList, type PortfolioPage } from "@/lib/portfolio/api";
import { PortfolioView } from "@/components/portfolio/PortfolioView";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return { title: dict.portfolio.title, description: dict.portfolio.subtitle };
}

/** Портфолио выполненных работ (F-4.3): сетка с фильтрами, карточка — фото «после». */
export default async function PortfolioPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();

  const empty: PortfolioPage = { items: [], page: 0, size: 24, total_elements: 0, total_pages: 0 };
  const [dict, initial] = await Promise.all([
    getDictionary(locale),
    getPortfolioList({}, 0, { locale, revalidate: 600 }).catch(() => empty),
  ]);

  const cities = [...new Set((initial.items ?? []).map((p) => p.city).filter((c): c is string => !!c))].sort();

  return (
    <section>
      <header className="mb-6">
        <h1 className="display text-h1 font-bold text-navy-deep">{dict.portfolio.title}</h1>
        <p className="mt-2 text-ink-soft">{dict.portfolio.subtitle}</p>
      </header>
      <PortfolioView initial={initial} cities={cities} locale={locale} dict={dict} />
    </section>
  );
}
