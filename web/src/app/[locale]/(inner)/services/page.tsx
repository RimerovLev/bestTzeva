import type { CSSProperties } from "react";
import type { Metadata } from "next";
import { breadcrumbLd, pageMeta } from "@/lib/seo";
import { JsonLd } from "@/components/seo/JsonLd";
import { notFound } from "next/navigation";
import { isLocale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { ServiceCalculator } from "@/components/services/ServiceCalculator";
import { PageBand } from "@/components/motion/PageBand";

const DIRECTIONS = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL"] as const;
const ROT = ["-1deg", "1deg", "-.6deg", ".8deg"];

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  return pageMeta({ locale, path: "/services", siteName: dict.meta.siteName, title: dict.services.title, description: dict.services.subtitle });
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
    <div>
      <JsonLd data={breadcrumbLd([{ label: dict.product.breadcrumbHome, href: `/${locale}` }, { label: dict.services.title }])} />
      <PageBand tone="red" eyebrow={dict.nav.services} title={dict.home.services.title} body={s.subtitle} dx="-40px" />

      <div className="stack">
        {DIRECTIONS.map((key, i) => {
          const d = s.directions[key];
          return (
            <article key={key} className="svc-card card" style={{ "--i": i, "--rot": ROT[i] } as CSSProperties}>
              <span className="svc-num" aria-hidden>{String(i + 1).padStart(2, "0")}</span>
              <div>
                <h2>{d.title}</h2>
                <p>{d.includes}</p>
              </div>
              <dl className="svc-meta">
                <dt className="sr-only">{s.timelineLabel}</dt>
                <dd className="pill">{d.timeline}</dd>
                <dt className="sr-only">{s.priceLabel}</dt>
                <dd className="pill y">{d.price}</dd>
              </dl>
            </article>
          );
        })}
      </div>

      <ServiceCalculator locale={locale} dict={dict} />
    </div>
  );
}
