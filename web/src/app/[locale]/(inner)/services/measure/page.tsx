import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { MeasureForm } from "@/components/services/MeasureForm";

export const metadata: Metadata = { robots: { index: false, follow: false } };

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
      <h1 className="display mb-6 text-center text-h1 font-bold text-navy-deep">{dict.measure.title}</h1>
      <MeasureForm locale={locale} dict={dict} />
    </section>
  );
}
