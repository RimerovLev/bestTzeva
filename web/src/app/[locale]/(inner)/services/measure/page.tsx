import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { MeasureForm } from "@/components/services/MeasureForm";
import { PageBand } from "@/components/motion/PageBand";

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  // Форма с персональными данными — не индексируем.
  return { title: dict.measure.title, robots: { index: false, follow: false } };
}

/** Заявка на замер (F-4.2): пошаговая форма с фото. Параметры переносятся из калькулятора (F-4.1). */
export default async function MeasurePage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = await getDictionary(locale);

  return (
    <section>
      <PageBand tone="mint" eyebrow={dict.nav.services} title={dict.measure.heading} body={dict.measure.intro} dx="-330px" />
      <MeasureForm locale={locale} dict={dict} />
    </section>
  );
}
