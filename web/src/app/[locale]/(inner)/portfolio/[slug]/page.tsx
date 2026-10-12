import type { Metadata } from "next";
import { notFound, permanentRedirect } from "next/navigation";
import Link from "next/link";
import { isLocale, type Locale } from "@/lib/i18n/config";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getPortfolioProject, type PortfolioProject } from "@/lib/portfolio/api";
import { ApiError } from "@/lib/api/problem";
import { BeforeAfterSlider } from "@/components/portfolio/BeforeAfterSlider";
import { ToMeasureLink } from "@/components/portfolio/ToMeasureLink";

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "";

async function loadProject(slug: string, locale: Locale): Promise<PortfolioProject | null> {
  try {
    return await getPortfolioProject(slug, { locale, revalidate: 600 });
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
  const canonicalPath = `/${locale}/portfolio/${p.slug ?? slug}`;
  const description = p.meta_description ?? p.description ?? undefined;
  const image = p.pairs?.[0]?.after_url ?? p.standalone?.[0]?.url ?? undefined;
  return {
    title: p.meta_title ?? p.title ?? undefined,
    description,
    alternates: { canonical: canonicalPath },
    openGraph: {
      type: "article",
      title: p.meta_title ?? p.title ?? "",
      description,
      url: SITE_URL ? `${SITE_URL}${canonicalPath}` : canonicalPath,
      images: image ? [image] : undefined,
    },
  };
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
    <article className="mx-auto max-w-4xl">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(projectJsonLd(project, locale)) }} />

      <nav className="mb-4 text-sm text-ink-soft">
        <Link href={`/${locale}/portfolio`} className="hover:text-ink">
          ← {t.backToList}
        </Link>
      </nav>

      <header className="mb-6">
        <h1 className="display text-h1 font-bold text-navy-deep">{project.title}</h1>
        <div className="mt-3 flex flex-wrap gap-2 text-sm">
          {dict.enums.property_type[type] ? (
            <span className="rounded-pill bg-navy-deep/8 px-3 py-1 text-navy">{dict.enums.property_type[type]}</span>
          ) : null}
          {project.city ? <span className="rounded-pill bg-navy-deep/8 px-3 py-1 text-navy">{project.city}</span> : null}
          {project.area_sqm != null ? (
            <span className="rounded-pill bg-navy-deep/8 px-3 py-1 text-navy">
              {t.area}: {project.area_sqm} {t.areaUnit}
            </span>
          ) : null}
          {project.duration_days != null ? (
            <span className="rounded-pill bg-navy-deep/8 px-3 py-1 text-navy">
              {t.duration.replace("{days}", String(project.duration_days))}
            </span>
          ) : null}
        </div>
      </header>

      {project.description ? <p className="mb-8 text-ink-soft">{project.description}</p> : null}

      {project.pairs && project.pairs.length > 0 ? (
        <section className="mb-8 space-y-6">
          <p className="text-sm text-ink-soft">{t.dragHint}</p>
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
        <section className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2">
          {project.standalone.map((m, i) =>
            m.url ? (
              // eslint-disable-next-line @next/next/no-img-element -- внешние фото портфолио (CDN не в whitelist next/image)
              <img
                key={i}
                src={m.url}
                alt={project.title ?? ""}
                loading="lazy"
                className="aspect-[4/3] w-full rounded-card border border-line object-cover"
              />
            ) : null,
          )}
        </section>
      ) : null}

      {project.work_types && project.work_types.length > 0 ? (
        <section className="mb-8">
          <h2 className="mb-2 text-h3 font-semibold text-navy">{t.workTypesLabel}</h2>
          <div className="flex flex-wrap gap-2">
            {project.work_types.map((w) => {
              const key = (w ?? "").toUpperCase() as keyof typeof dict.enums.work_type;
              return (
                <span key={w} className="rounded-pill border border-line px-3 py-1 text-sm text-ink">
                  {dict.enums.work_type[key] ?? w}
                </span>
              );
            })}
          </div>
        </section>
      ) : null}

      <div className="mt-10 rounded-card bg-paper-warm p-6 text-center">
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
    url: SITE_URL ? `${SITE_URL}${canonicalPath}` : canonicalPath,
  };
}
