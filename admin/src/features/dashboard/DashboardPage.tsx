import { useEffect, useState, type ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Skeleton } from "@/components/ui/Skeleton";
import { LineChart, Donut, type Slice } from "./charts";
import {
  PRESETS, presetPeriod, readPeriod, writePeriod, granularityFor,
  getSummary, getChart, getTopColors, getTopProducts, getRecent, type Period,
} from "./api";

/** Цвета долей/серий — токены админки, а не палитра по умолчанию. */
const SLICE_COLORS = ["var(--navy)", "var(--brass)", "var(--terracotta)", "var(--sage)", "var(--ink-soft)", "var(--brass-light)"];

/**
 * Дашборд (F-10.1) по третьему макету, но про спрос и склад, а не про продажи: продаж в системе нет,
 * сделка закрывается офлайн. Период общий для всех блоков и живёт в адресе (?period / ?from&to).
 * Склад (WAREHOUSE) к дашборду бэкенда доступа не имеет — для него короткий экран со ссылками.
 */
export function DashboardPage() {
  const { user } = useAuth();
  return user?.role === "WAREHOUSE" ? <WarehouseHome /> : <Dashboard />;
}

/** Склад: дашборд бэкенда ему закрыт — короткий экран со ссылками на рабочие разделы. */
function WarehouseHome() {
  const { dict } = useI18n();
  const { user } = useAuth();
  const t = dict.dashboard;
  const name = user?.firstName || user?.email || "";
  return (
    <section className="space-y-5">
      <h1 className="text-h2 font-bold text-navy">{t.hello.replace("{name}", name)}</h1>
      <div className="rounded-card border border-line bg-surface p-6 shadow-card">
        <p className="text-ink-soft">{t.warehouseNote}</p>
        <div className="mt-4 flex flex-wrap gap-3">
          <Link to="/warehouse" className="rounded-control bg-navy px-4 py-2 text-sm font-medium text-surface">{dict.nav.warehouse}</Link>
          <Link to="/product-requests" className="rounded-control border border-line px-4 py-2 text-sm text-navy">{dict.nav.productRequests}</Link>
        </div>
      </div>
    </section>
  );
}

function Dashboard() {
  const { dict, locale } = useI18n();
  const { user } = useAuth();
  const t = dict.dashboard;
  const [period, setPeriod] = useState<Period>(() => readPeriod());
  useEffect(() => writePeriod(period), [period]);

  const name = user?.firstName || user?.email || "";
  const nf = new Intl.NumberFormat(locale === "he" ? "he-IL" : "ru-RU");
  const df = (iso: string, opts: Intl.DateTimeFormatOptions) => new Date(iso).toLocaleDateString(locale === "he" ? "he-IL" : "ru-RU", opts);

  const key = [period.from, period.to];
  const summary = useQuery({ queryKey: ["dash-summary", ...key], queryFn: () => getSummary(period) });
  const chart = useQuery({ queryKey: ["dash-chart", ...key], queryFn: () => getChart(period) });
  const colors = useQuery({ queryKey: ["dash-colors", ...key], queryFn: () => getTopColors(period) });
  const products = useQuery({ queryKey: ["dash-products", ...key], queryFn: () => getTopProducts(period) });
  const recent = useQuery({ queryKey: ["dash-recent"], queryFn: getRecent });

  const s = summary.data;
  const gran = granularityFor(period);
  const points = chart.data ?? [];
  const chartLabels = points.map((p) => df(p.bucket ?? "", gran === "month" ? { month: "short", year: "2-digit" } : { day: "numeric", month: "short" }));
  const slices: Slice[] = (colors.data ?? []).map((c, i) => ({
    key: c.key ?? String(i), label: (c.label ?? "").replace(/^RAL(\d)/, "RAL $1"), value: c.quantity ?? 0, color: SLICE_COLORS[i % SLICE_COLORS.length]!,
  }));
  const topMax = Math.max(1, ...(products.data ?? []).map((p) => p.quantity ?? 0));

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-h2 font-bold text-navy">{t.hello.replace("{name}", name)}</h1>
          <p className="mt-1 text-sm text-ink-soft">{t.subtitle}</p>
        </div>
        <PeriodPicker value={period} onChange={setPeriod} labels={t} />
      </header>

      {/* Карточки: спрос и склад вместо «выручки/заказов» макета */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi title={t.kpiToday} loading={summary.isLoading} accent="var(--navy)"
          value={s ? nf.format((s.product_requests_today ?? 0) + (s.service_requests_today ?? 0)) : "—"}
          sub={s ? `${t.products}: ${nf.format(s.product_requests_today ?? 0)} · ${t.services}: ${nf.format(s.service_requests_today ?? 0)}` : ""} />
        <Kpi title={t.kpiProducts} loading={summary.isLoading} accent="var(--brass)"
          value={s ? nf.format(s.product_requests_period ?? 0) : "—"}
          delta={s ? s.product_change_pct ?? null : undefined} prev={s?.product_requests_prev_period} labels={t} nf={nf}
          link={<Link to="/product-requests" className="text-brass hover:underline">{t.open}</Link>} />
        <Kpi title={t.kpiServices} loading={summary.isLoading} accent="var(--terracotta)"
          value={s ? nf.format(s.service_requests_period ?? 0) : "—"}
          delta={s ? s.service_change_pct ?? null : undefined} prev={s?.service_requests_prev_period} labels={t} nf={nf}
          link={<Link to="/service-requests" className="text-brass hover:underline">{t.open}</Link>} />
        <Kpi title={t.kpiStock} loading={summary.isLoading} accent="var(--sage)"
          value={s ? nf.format(s.low_stock_positions ?? 0) : "—"}
          sub={s ? `${t.lowStock} · ${t.reserved}: ${nf.format(s.reserved_positions ?? 0)}` : ""}
          link={<Link to="/warehouse" className="text-brass hover:underline">{t.open}</Link>} />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card title={t.chartTitle[gran]} className="xl:col-span-2"
          extra={<Legend items={[{ label: t.products, color: "var(--navy)" }, { label: t.services, color: "var(--brass)" }]} />}>
          {chart.isLoading ? <Skeleton className="h-60 w-full" /> : (
            <LineChart labels={chartLabels} emptyLabel={t.noData} series={[
              { key: "p", label: t.products, color: "var(--navy)", values: points.map((p) => p.product_requests ?? 0) },
              { key: "s", label: t.services, color: "var(--brass)", values: points.map((p) => p.service_requests ?? 0) },
            ]} />
          )}
        </Card>
        <Card title={t.colorsTitle} hint={t.colorsHint}>
          {colors.isLoading ? <Skeleton className="h-44 w-full" /> : <Donut slices={slices} centerLabel={t.requestedUnits} emptyLabel={t.noData} />}
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Card title={t.topProducts} hint={t.colorsHint}>
          {products.isLoading ? <Skeleton className="h-40 w-full" /> : (products.data ?? []).length === 0 ? (
            <p className="text-sm text-ink-faint">{t.noData}</p>
          ) : (
            <ul className="space-y-3">
              {(products.data ?? []).map((p) => (
                <li key={p.key}>
                  <div className="mb-1 flex justify-between gap-3 text-sm"><bdi className="numeric truncate text-navy">{p.label}</bdi><span className="numeric text-ink-soft">{nf.format(p.quantity ?? 0)}</span></div>
                  <div className="h-2 rounded-pill bg-paper-warm"><div className="h-2 rounded-pill bg-brass" style={{ width: `${((p.quantity ?? 0) / topMax) * 100}%` }} /></div>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title={t.recentTitle} className="xl:col-span-2">
          {recent.isLoading ? <Skeleton className="h-40 w-full" /> : (recent.data ?? []).length === 0 ? (
            <p className="text-sm text-ink-faint">{t.noData}</p>
          ) : (
            <table className="w-full text-sm">
              <thead><tr className="text-start text-xs text-ink-faint">
                <th className="pb-2 text-start font-normal">{t.colType}</th><th className="pb-2 text-start font-normal">{t.colNumber}</th>
                <th className="pb-2 text-start font-normal">{t.colCustomer}</th><th className="pb-2 text-start font-normal">{t.colStatus}</th>
                <th className="pb-2 text-end font-normal">{t.colDate}</th>
              </tr></thead>
              <tbody>
                {(recent.data ?? []).map((r) => {
                  const isService = r.type === "service";
                  const status = (r.status ?? "").toUpperCase();
                  const statusLabel = isService
                    ? dict.enums.service_status[status as keyof typeof dict.enums.service_status]
                    : dict.enums.request_status[status as keyof typeof dict.enums.request_status];
                  return (
                    <tr key={`${r.type}-${r.number}`} className="border-t border-line">
                      <td className="py-2"><span className={`rounded-pill px-2 py-0.5 text-xs ${isService ? "bg-warn-bg text-warn-fg" : "bg-info-bg text-info-fg"}`}>{isService ? t.services : t.products}</span></td>
                      <td className="py-2"><Link to={isService ? "/service-requests" : "/product-requests"} className="numeric text-brass hover:underline"><bdi>{r.number}</bdi></Link></td>
                      <td className="py-2 text-navy">{r.customer_name}</td>
                      <td className="py-2 text-ink-soft">{statusLabel ?? r.status}</td>
                      <td className="numeric py-2 text-end text-ink-soft"><bdi>{r.created_at ? df(r.created_at, { day: "numeric", month: "short" }) : "—"}</bdi></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </Card>
      </div>
    </section>
  );
}

type DashDict = ReturnType<typeof useI18n>["dict"]["dashboard"];

function PeriodPicker({ value, onChange, labels }: { value: Period; onChange: (p: Period) => void; labels: DashDict }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <div className="flex overflow-hidden rounded-control border border-line bg-surface" role="group" aria-label={labels.period}>
        {PRESETS.map((d) => (
          <button key={d} type="button" aria-pressed={value.preset === d} onClick={() => onChange(presetPeriod(Number(d)))}
            className={`px-3 py-1.5 text-sm ${value.preset === d ? "bg-navy text-surface" : "text-ink-soft hover:bg-paper-warm"}`}>
            {labels.days.replace("{n}", d)}
          </button>
        ))}
      </div>
      <label className="flex items-center gap-1 text-xs text-ink-soft">{labels.from}
        <input type="date" value={value.from} max={value.to} onChange={(e) => e.target.value && onChange({ preset: "custom", from: e.target.value, to: value.to })}
          className="rounded-control border border-line bg-surface px-2 py-1 text-sm text-ink" />
      </label>
      <label className="flex items-center gap-1 text-xs text-ink-soft">{labels.to}
        <input type="date" value={value.to} min={value.from} onChange={(e) => e.target.value && onChange({ preset: "custom", from: value.from, to: e.target.value })}
          className="rounded-control border border-line bg-surface px-2 py-1 text-sm text-ink" />
      </label>
    </div>
  );
}

function Kpi({ title, value, sub, delta, prev, accent, link, loading, labels, nf }: {
  title: string; value: string; sub?: string; delta?: number | null; prev?: number; accent: string; link?: ReactNode;
  loading: boolean; labels?: DashDict; nf?: Intl.NumberFormat;
}) {
  return (
    <div className="relative overflow-hidden rounded-card border border-line bg-surface p-5 shadow-card">
      <span className="absolute inset-x-0 top-0 h-1" style={{ background: accent }} aria-hidden />
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm text-ink-soft">{title}</p>
        {link && <span className="text-xs">{link}</span>}
      </div>
      {loading ? <Skeleton className="mt-3 h-8 w-24" /> : (
        <>
          <p className="numeric mt-2 text-h1 font-bold text-navy"><bdi>{value}</bdi></p>
          {delta !== undefined && labels && (
            <p className="mt-1 text-xs">
              {delta === null ? <span className="text-ink-faint">{labels.newGrowth}</span> : (
                <span className={`rounded-pill px-2 py-0.5 ${delta > 0 ? "bg-ok-bg text-ok-fg" : delta < 0 ? "bg-crit-bg text-crit-fg" : "bg-paper-warm text-ink-soft"}`}>
                  <bdi>{delta > 0 ? "↑" : delta < 0 ? "↓" : "="} {Math.abs(delta)}%</bdi>
                </span>
              )}
              {prev !== undefined && nf && <span className="ms-2 text-ink-faint">{labels.prev}: <bdi className="numeric">{nf.format(prev)}</bdi></span>}
            </p>
          )}
          {sub && <p className="mt-1 text-xs text-ink-faint">{sub}</p>}
        </>
      )}
    </div>
  );
}

function Card({ title, hint, extra, className = "", children }: { title: string; hint?: string; extra?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <div className={`rounded-card border border-line bg-surface p-5 shadow-card ${className}`}>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-h3 font-semibold text-navy">{title}</h2>
          {hint && <p className="text-xs text-ink-faint">{hint}</p>}
        </div>
        {extra}
      </div>
      {children}
    </div>
  );
}

function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <div className="flex gap-4 text-xs text-ink-soft">
      {items.map((i) => <span key={i.label} className="flex items-center gap-1.5"><i className="inline-block h-2 w-4 rounded-pill" style={{ background: i.color }} />{i.label}</span>)}
    </div>
  );
}
