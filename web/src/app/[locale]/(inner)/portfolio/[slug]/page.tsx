import type { Metadata } from "next";
import { notFound, permanentRedirect } from "next/navigation";
import { isLocale, type Locale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getPortfolioProject, type PortfolioProject } from "@/lib/portfolio/api";
import { ApiError } from "@/lib/api/problem";
import { BeforeAfterSlider } from "@/components/portfolio/BeforeAfterSlider";
import { ToMeasureLink } from "@/components/portfolio/ToMeasureLink";
import { PageBand } from "@/components/motion/PageBand";
import { JsonLd } from "@/components/seo/JsonLd";
import { abs, breadcrumbLd, pageMeta } from "@/lib/seo";
import { Breadcrumbs } from "@/components/product/Breadcrumbs";


async function loadProject(slug: string, locale: Locale): Promise<PortfolioProject | null> {
  try {
    return await getPortfolioProject(slug, { locale, revalidate: 3600 });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string; slug: string }>;
}): Promise<Metadata> {
  const { locale, slug } = await params;
  if (!isLocale(locale)) return {};
  const p = await loadProject(slug, locale);
  if (!p) return {};
  const dict = await getDictionary(locale);
  return pageMeta({
    locale,
    path: `/portfolio/${p.slug ?? slug}`,
    siteName: dict.meta.siteName,
    title: p.meta_title ?? p.title ?? undefined,
    description: p.meta_description ?? p.description,
    image: p.pairs?.[0]?.after_url ?? p.standalone?.[0]?.url,
    type: "article",
  });
}

export default async function PortfolioProjectPage({
  params,
}: {
  params: Promise<{ locale: string; slug: string }>;
}) {
  const { locale, slug } = await params;
  if (!isLocale(locale)) notFound();

  const [dict, project] = await Promise.all([getDictionary(locale), loadProject(slug, locale)]);
  if (!project) notFound();
  // Устаревший slug (бэкенд отдал 301, fetch последовал) → постоянный редирект на актуальный адрес.
  if (project.slug && project.slug !== slug) {
    permanentRedirect(`/${locale}/portfolio/${project.slug}`);
  }

  const t = dict.portfolio;
  const type = (project.property_type ?? "").toUpperCase() as keyof typeof dict.enums.property_type;

  return (
    <article>
      <JsonLd data={[
        projectJsonLd(project, locale),
        breadcrumbLd([
          { label: dict.product.breadcrumbHome, href: `/${locale}` },
          { label: t.title, href: `/${locale}/portfolio` },
          { label: project.title ?? "" },
        ]),
      ]} />

      <PageBand
        tone="violet"
        title={project.title ?? ""}
        body={project.description}
        product
        dx="-170px"
        crumbs={
          <Breadcrumbs
            label={dict.product.breadcrumbLabel}
            items={[
              { label: dict.product.breadcrumbHome, href: `/${locale}` },
              { label: t.title, href: `/${locale}/portfolio` },
              { label: project.title ?? "" },
            ]}
          />
        }
      >
        <div className="band-meta">
          {dict.enums.property_type[type] ? <span className="pill y">{dict.enums.property_type[type]}</span> : null}
          {project.city ? <span className="pill">{project.city}</span> : null}
          {project.area_sqm != null ? <span className="pill">{t.area}: <bdi>{project.area_sqm} {t.areaUnit}</bdi></span> : null}
          {project.duration_days != null ? <span className="pill">{t.duration.replace("{days}", String(project.duration_days))}</span> : null}
        </div>
      </PageBand>

      {project.pairs && project.pairs.length > 0 ? (
        <section className="pf-section">
          <p className="small">{t.dragHint}</p>
          {project.pairs.map((pair, i) =>
            pair.before_url && pair.after_url ? (
              <BeforeAfterSlider
                key={pair.pair_group ?? i}
                beforeUrl={pair.before_url}
                afterUrl={pair.after_url}
                beforeLabel={t.before}
                afterLabel={t.after}
                alt={`${project.title ?? ""} — ${pair.pair_group ?? ""}`}
              />
            ) : null,
          )}
        </section>
      ) : null}

      {project.standalone && project.standalone.length > 0 ? (
        <section className="pf-section pf-photos">
          {project.standalone.map((m, i) =>
            m.url ? (
              // eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио (CDN не в whitelist next/image)
              <img key={i} src={m.url} alt={project.title ?? ""} loading="lazy" />
            ) : null,
          )}
        </section>
      ) : null}

      {project.work_types && project.work_types.length > 0 ? (
        <section className="pf-section">
          <h2>{t.workTypesLabel}</h2>
          <div className="pf-chips">
            {project.work_types.map((w) => {
              const key = (w ?? "").toUpperCase() as keyof typeof dict.enums.work_type;
              return <span key={w} className="pill">{dict.enums.work_type[key] ?? w}</span>;
            })}
          </div>
        </section>
      ) : null}

      <div className="card cta-card">
        <h2>{t.ctaTitle}</h2>
        <ToMeasureLink locale={locale} propertyType={project.property_type ?? undefined} label={t.toMeasure} />
      </div>
    </article>
  );
}

function projectJsonLd(p: PortfolioProject, locale: Locale) {
  const images = [
    ...(p.pairs ?? []).map((pair) => pair.after_url).filter(Boolean),
    ...(p.standalone ?? []).map((m) => m.url).filter(Boolean),
  ];
  const canonicalPath = `/${locale}/portfolio/${p.slug ?? ""}`;
  return {
    "@context": "https://schema.org",
    "@type": "CreativeWork",
    name: p.title ?? undefined,
    description: p.meta_description ?? p.description ?? undefined,
    dateCreated: p.completed_at ?? undefined,
    image: images.length ? images : undefined,
    locationCreated: p.city ? { "@type": "Place", name: p.city } : undefined,
    url: abs(canonicalPath),
  };
}
