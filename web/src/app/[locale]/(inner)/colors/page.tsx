import type { Metadata } from "next";
import { breadcrumbLd, pageMeta } from "@/lib/seo";
import { JsonLd } from "@/components/seo/JsonLd";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getColors, type ColorSwatch } from "@/lib/catalog/api";
import Link from "next/link";
import { ColorsView } from "@/components/colors/ColorsView";
import { PageBand } from "@/components/motion/PageBand";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return pageMeta({ locale, path: "/colors", siteName: dict.meta.siteName, title: dict.colors.title, description: dict.colors.subtitle });
}

export default async function ColorsPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();

  const [dict, colors] = await Promise.all([
    getDictionary(locale),
    getColors({}, { locale, revalidate: 3600 }).catch(() => [] as ColorSwatch[]),
  ]);

  return (
    <section>
      <JsonLd data={breadcrumbLd([{ label: dict.product.breadcrumbHome, href: `/${locale}` }, { label: dict.colors.title }])} />
      <PageBand tone="pink" eyebrow={dict.nav.colors} title={dict.colors.heading} body={dict.colors.subtitle} dx="-260px">
        <div className="band-meta">
          <Link href={`/${locale}#colors`} className="btn btn-sm">{dict.colors.tryOn}</Link>
        </div>
      </PageBand>
      <ColorsView colors={colors} locale={locale} dict={dict} />
    </section>
  );
}
