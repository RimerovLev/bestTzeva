import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type DashboardSummary = components["schemas"]["DashboardSummary"];
export type ChartPoint = components["schemas"]["ChartPoint"];
export type TopItem = components["schemas"]["TopItem"];
export type RecentRequest = components["schemas"]["RecentRequestItem"];

/** Период дашборда: пресет в днях или произвольный диапазон. Хранится в адресе (?period=30 | ?from&to). */
export interface Period { preset: "7" | "30" | "90" | "custom"; from: string; to: string }
export const PRESETS = ["7", "30", "90"] as const;

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

export function presetPeriod(days: number): Period {
  const to = new Date();
  const from = new Date(); from.setDate(to.getDate() - (days - 1));
  return { preset: String(days) as Period["preset"], from: iso(from), to: iso(to) };
}

export function readPeriod(): Period {
  const sp = new URLSearchParams(window.location.search);
  const from = sp.get("from"), to = sp.get("to"), p = sp.get("period");
  if (from && to && /^\d{4}-\d{2}-\d{2}$/.test(from) && /^\d{4}-\d{2}-\d{2}$/.test(to)) return { preset: "custom", from, to };
  return presetPeriod(p === "7" || p === "90" ? Number(p) : 30);
}

export function writePeriod(p: Period) {
  const sp = new URLSearchParams(window.location.search);
  sp.delete("from"); sp.delete("to"); sp.delete("period");
  if (p.preset === "custom") { sp.set("from", p.from); sp.set("to", p.to); } else sp.set("period", p.preset);
  window.history.replaceState(window.history.state, "", `${window.location.pathname}?${sp.toString()}`);
}

/** Шаг графика по длине периода: до 45 дней — дни, до полугода — недели, дальше — месяцы. */
export function granularityFor(p: Period): "day" | "week" | "month" {
  const days = (Date.parse(p.to) - Date.parse(p.from)) / 86_400_000 + 1;
  return days <= 45 ? "day" : days <= 183 ? "week" : "month";
}

const range = (p: Period) => ({ from: p.from, to: p.to });

export async function getSummary(p: Period): Promise<DashboardSummary> {
  return unwrap(await api.GET("/api/v1/admin/dashboard/summary", { params: { query: range(p) } }));
}
export async function getChart(p: Period): Promise<ChartPoint[]> {
  return unwrap(await api.GET("/api/v1/admin/dashboard/requests-chart", { params: { query: { ...range(p), granularity: granularityFor(p) } } }));
}
export async function getTopColors(p: Period): Promise<TopItem[]> {
  return unwrap(await api.GET("/api/v1/admin/dashboard/top-colors", { params: { query: { ...range(p), limit: 6 } } }));
}
export async function getTopProducts(p: Period): Promise<TopItem[]> {
  return unwrap(await api.GET("/api/v1/admin/dashboard/top-products", { params: { query: { ...range(p), limit: 5 } } }));
}
export async function getRecent(): Promise<RecentRequest[]> {
  return unwrap(await api.GET("/api/v1/admin/dashboard/recent-requests", { params: { query: { limit: 8 } } }));
}
