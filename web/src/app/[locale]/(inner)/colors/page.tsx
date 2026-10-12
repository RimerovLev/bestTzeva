import type { Metadata } from "next";
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
  return { title: dict.colors.title, description: dict.colors.subtitle };
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
      <PageBand tone="pink" eyebrow={dict.nav.colors} title={dict.colors.heading} body={dict.colors.subtitle} dx="-260px">
        <div className="band-meta">
          <Link href={`/${locale}#colors`} className="btn btn-sm">{dict.colors.tryOn}</Link>
        </div>
      </PageBand>
      <ColorsView colors={colors} locale={locale} dict={dict} />
    </section>
  );
}
