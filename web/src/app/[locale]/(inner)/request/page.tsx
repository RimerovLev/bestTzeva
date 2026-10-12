import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { RequestView } from "@/components/request/RequestView";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  // Личный список — не индексируем.
  return { title: dict.request.title, robots: { index: false, follow: false } };
}

export default async function RequestPage({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = await getDictionary(locale);

  return (
    <section>
      <h1 className="display mb-6 text-h1 font-bold text-navy-deep">{dict.request.title}</h1>
      <RequestView locale={locale} dict={dict} />
    </section>
  );
}
