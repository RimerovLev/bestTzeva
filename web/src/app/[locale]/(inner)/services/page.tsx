import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { ServiceCalculator } from "@/components/services/ServiceCalculator";

const DIRECTIONS = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL"] as const;

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return { title: dict.services.title, description: dict.services.subtitle };
}

export default async function ServicesPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = await getDictionary(locale);
  const s = dict.services;

  return (
    <div className="space-y-12">
      <header>
        <h1 className="display text-h1 font-bold text-navy-deep">{s.title}</h1>
        <p className="mt-2 max-w-2xl text-ink font-medium">{s.subtitle}</p>
      </header>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {DIRECTIONS.map((key) => {
          const d = s.directions[key];
          return (
            <div key={key} className="flex flex-col rounded-card border border-line bg-surface p-5 shadow-card">
              <h2 className="text-h3 font-bold text-navy">{d.title}</h2>
              <dl className="mt-3 space-y-2 text-sm">
                <div><dt className="text-ink-faint">{s.includesLabel}</dt><dd className="text-ink">{d.includes}</dd></div>
                <div><dt className="text-ink-faint">{s.timelineLabel}</dt><dd className="text-ink">{d.timeline}</dd></div>
                <div><dt className="text-ink-faint">{s.priceLabel}</dt><dd className="font-medium text-brass">{d.price}</dd></div>
              </dl>
            </div>
          );
        })}
      </div>

      <ServiceCalculator locale={locale} dict={dict} />
    </div>
  );
}
