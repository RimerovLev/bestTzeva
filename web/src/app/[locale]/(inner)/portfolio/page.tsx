import type { Metadata } from "next";
import { breadcrumbLd, pageMeta } from "@/lib/seo";
import { JsonLd } from "@/components/seo/JsonLd";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getPortfolioList, type PortfolioPage } from "@/lib/portfolio/api";
import { PortfolioView } from "@/components/portfolio/PortfolioView";
import { PageBand } from "@/components/motion/PageBand";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return pageMeta({ locale, path: "/portfolio", siteName: dict.meta.siteName, title: dict.portfolio.title, description: dict.portfolio.subtitle });
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
    getPortfolioList({}, 0, { locale, revalidate: 3600 }).catch(() => empty),
  ]);

  const cities = [...new Set((initial.items ?? []).map((p) => p.city).filter((c): c is string => !!c))].sort();

  return (
    <section>
      <JsonLd data={breadcrumbLd([{ label: dict.product.breadcrumbHome, href: `/${locale}` }, { label: dict.portfolio.title }])} />
      <PageBand tone="blue" eyebrow={dict.nav.works} title={dict.portfolio.heading} body={dict.portfolio.subtitle} dx="-120px" />
      <PortfolioView initial={initial} cities={cities} locale={locale} dict={dict} />
    </section>
  );
}
