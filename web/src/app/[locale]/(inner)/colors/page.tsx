import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getColors, type ColorSwatch } from "@/lib/catalog/api";
import { ColorsView } from "@/components/colors/ColorsView";

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
      <header className="mb-6">
        <h1 className="display text-h1 font-bold text-navy-deep">{dict.colors.title}</h1>
        <p className="mt-2 text-ink-soft">{dict.colors.subtitle}</p>
      </header>
      <ColorsView colors={colors} locale={locale} dict={dict} />
    </section>
  );
}
