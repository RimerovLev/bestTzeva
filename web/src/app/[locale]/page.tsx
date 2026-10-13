import type { CSSProperties, ReactNode } from "react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { isLocale, type Locale } from "@/lib/i18n/config";
import { getDictionary, type Dictionary } from "@/lib/i18n/dictionaries";
import { getProducts, type ProductSummary } from "@/lib/catalog/api";
import { formatPrice } from "@/lib/format";
import { RAIL_COLORS } from "@/lib/motion/palette";
import { FluidHero } from "@/components/motion/FluidHero";
import { Tapes } from "@/components/motion/Tapes";
import { BeforeAfter } from "@/components/motion/BeforeAfter";
import { CatalogRail, type RailProduct } from "@/components/motion/CatalogRail";
import { ColorStudio } from "@/components/motion/ColorStudio";
import { QuickEstimate } from "@/components/motion/QuickEstimate";
import { ProcessPath } from "@/components/motion/ProcessPath";
import { ScrubText } from "@/components/motion/ScrubText";
import { LeadForm, type LeadFormText } from "@/components/motion/LeadForm";
import { CopyPhone } from "@/components/motion/CopyPhone";
import { RevealEffects } from "@/components/motion/RevealEffects";
import { JsonLd } from "@/components/seo/JsonLd";
import { businessLd, pageMeta } from "@/lib/seo";
import type { Metadata } from "next";

type PaintTypeKey = keyof Dictionary["enums"]["paint_type"];
type DirectionKey = keyof Dictionary["services"]["directions"];
const DIRECTIONS: DirectionKey[] = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL"];
const RAIL_SIZE = 6;

/** Заголовок секции с прокрашиваемым маркером под ключевыми словами. */
function SecTitle({ t, hlc, ink, id }: { t: { a: string; hl: string; b?: string }; hlc?: string; ink?: boolean; id?: string }) {
  const style = { ...(hlc ? { "--hlc": hlc } : {}), ...(ink ? { color: "var(--ink)" } : {}) } as CSSProperties;
  return (
    <h2 className="h-sec" id={id}>
      {t.a} <span className="hl" style={style}>{t.hl}</span>{t.b ? ` ${t.b}` : ""}
    </h2>
  );
}

function SecHead({ eyebrow, title, body }: { eyebrow: string; title: ReactNode; body?: string }) {
  return (
    <div className="sec-head reveal">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        {title}
      </div>
      {body && <p>{body}</p>}
    </div>
  );
}

const Check = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#121212" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
    <path d="M20 6 9 17l-5-5" />
  </svg>
);

const VALUE_ICONS = [
  <path key="q" d="m12 3 2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.4 6.8 19.1l1-5.8-4.3-4.1 5.9-.9Z" />,
  <g key="r"><path d="M12 3 5 6v6c0 4 3 7 7 9 4-2 7-5 7-9V6Z" /><path d="m9 12 2 2 4-4" /></g>,
  <g key="s"><circle cx="12" cy="8" r="4" /><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" /></g>,
];
const VALUE_COLORS = ["var(--yellow)", "var(--mint)", "var(--pink)"];
const VALUE_ROT = ["-2deg", "1.5deg", "-1deg"];

/** Несколько товаров для ленты. Нет API — лента остаётся с баннером и ссылкой на каталог. */
async function railProducts(locale: Locale, dict: Dictionary): Promise<RailProduct[]> {
  let items: ProductSummary[] = [];
  try {
    items = (await getProducts({}, 0, { locale, revalidate: 300 })).products?.items ?? [];
  } catch {
    return [];
  }
  return items.slice(0, RAIL_SIZE).map((p, i) => {
    const min = p.min_price, max = p.max_price ?? min;
    const typeKey = p.paint_type?.toUpperCase() as PaintTypeKey | undefined;
    return {
      key: String(p.id ?? p.slug ?? i),
      href: `/${locale}/products/${p.slug ?? p.id}`,
      name: p.name ?? "",
      type: typeKey ? dict.enums.paint_type[typeKey] ?? null : null,
      price: min != null ? formatPrice(locale, min) : null,
      priceFrom: min != null && max != null && max !== min,
      color: RAIL_COLORS[i % RAIL_COLORS.length] ?? "#e6d2b5",
    };
  });
}

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> {
  const { locale } = await params;
  if (!isLocale(locale)) return {};
  const dict = await getDictionary(locale);
  // title главной — «по умолчанию» из layout (без шаблона «· сайт»), здесь только canonical/hreflang/OG
  const meta = pageMeta({ locale, path: "", siteName: dict.meta.siteName, description: dict.home.hero.body });
  return { ...meta, openGraph: { ...meta.openGraph, title: `${dict.meta.siteName} — ${dict.meta.tagline}` } };
}

export default async function HomePage({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const dict = await getDictionary(locale);
  const h = dict.home;
  const errors = dict.errors as unknown as Record<string, string>;
  const products = await railProducts(locale, dict);

  const leadText: LeadFormText = {
    ...h.contact.form,
    needs: h.contact.form.needs.map((n) => ({ value: n.value as LeadFormText["needs"][number]["value"], label: n.label })),
  };

  return (
    <>
      <RevealEffects />
      <JsonLd data={businessLd(locale, dict.meta.siteName, h.hero.body)} />

      <FluidHero text={h.hero} />

      <Tapes a={h.tapes.a} b={h.tapes.b} />

      {/* РАБОТЫ */}
      <section className="sec s-blue drip" id="works" style={{ "--dx": "-120px" } as CSSProperties}>
        <div className="wrap works">
          <BeforeAfter {...h.works.slider} />
          <div>
            <div className="reveal">
              <span className="eyebrow">{dict.nav.works}</span>
              <SecTitle t={h.works.title} hlc="var(--yellow)" ink />
              <p className="lead" style={{ marginTop: 18 }}>{h.works.body}</p>
            </div>
            <ul className="points">
              {h.works.points.map((pt, i) => (
                <li key={i} className="card reveal">
                  <span className="ico" style={{ "--c": ["var(--yellow)", "var(--mint)", "var(--pink)"][i % 3] } as CSSProperties}><Check /></span>
                  <div><b>{pt.title}</b><span>{pt.body}</span></div>
                </li>
              ))}
            </ul>
            <Link className="btn btn-yellow more-link" href={`/${locale}/portfolio`}>{h.works.more}</Link>
          </div>
        </div>
      </section>

      {/* КАТАЛОГ */}
      <CatalogRail products={products} allHref={`/${locale}/products`} text={h.catalog.rail}>
        <SecHead eyebrow={dict.nav.prices} title={<SecTitle t={h.catalog.title} hlc="var(--paper)" />} body={h.catalog.body} />
      </CatalogRail>

      {/* ЦВЕТА + ПРИМЕРКА */}
      <section className="sec s-paper" id="colors">
        <div className="wrap">
          <SecHead eyebrow={dict.nav.colors} title={<SecTitle t={h.colors.title} />} body={h.colors.body} />
          <ColorStudio locale={locale} text={h.colors.studio} />
        </div>
      </section>

      {/* УСЛУГИ */}
      <section className="sec s-red drip" id="services" style={{ "--dx": "-40px" } as CSSProperties}>
        <div className="wrap">
          <SecHead eyebrow={dict.nav.services} title={<SecTitle t={h.services.title} hlc="var(--ink)" />} body={h.services.body} />
          <div className="stack">
            {DIRECTIONS.map((key, i) => {
              const d = dict.services.directions[key];
              return (
                <article key={key} className="svc-card card" style={{ "--i": i, "--rot": ["-1deg", "1deg", "-.6deg", ".8deg"][i] } as CSSProperties}>
                  <span className="svc-num" aria-hidden>{String(i + 1).padStart(2, "0")}</span>
                  <div><h3>{d.title}</h3><p>{d.includes}</p></div>
                  <div className="svc-meta">
                    <span className="pill">{d.timeline}</span>
                    <span className="pill y">{d.price}</span>
                  </div>
                </article>
              );
            })}
          </div>
          <QuickEstimate locale={locale} text={h.services.estimate} errors={errors} />
        </div>
      </section>

      {/* ПРОЦЕСС */}
      <section className="sec s-mint drip" aria-labelledby="proc-h" style={{ "--dx": "-330px" } as CSSProperties}>
        <div className="wrap">
          <SecHead eyebrow={h.process.eyebrow} title={<SecTitle t={h.process.title} hlc="var(--paper)" id="proc-h" />} />
          <ProcessPath steps={h.process.steps} />
        </div>
      </section>

      {/* О КОМПАНИИ */}
      <section className="sec s-violet drip" id="about" style={{ "--dx": "-170px" } as CSSProperties}>
        <div className="wrap about">
          <div>
            <span className="eyebrow">{dict.nav.about}</span>
            <SecTitle t={h.about.title} hlc="var(--yellow)" ink />
            <ScrubText text={h.about.body} />
          </div>
          <div className="values reveal">
            {h.about.values.map((v, i) => (
              <div key={i} className="value card" style={{ "--rot": VALUE_ROT[i % 3] } as CSSProperties}>
                <span className="v-ico" style={{ "--c": VALUE_COLORS[i % 3] } as CSSProperties}>
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#121212" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                    {VALUE_ICONS[i % 3]}
                  </svg>
                </span>
                <b>{v.title}</b>
                <span>{v.body}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* КОНТАКТ */}
      <section className="sec s-ink" id="contact">
        <div className="wrap contact">
          <div className="reveal">
            <span className="eyebrow">{h.contact.eyebrow}</span>
            <SecTitle t={h.contact.title} hlc="var(--red)" />
            <p className="lead" style={{ marginTop: 18 }}>{h.contact.body}</p>
            <div className="ci">
              <span className="ci-label">{dict.header.phoneLabel}</span>
              <CopyPhone {...h.contact.phone} />
            </div>
            <div className="ci">
              <span className="ci-label">{dict.footer.hours}</span>
              <span>{h.contactHoursValue}</span>
            </div>
          </div>
          <div className="form-card card reveal">
            <LeadForm locale={locale} text={leadText} errors={errors} />
          </div>
        </div>
      </section>
    </>
  );
}
