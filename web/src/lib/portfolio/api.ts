import type { components } from "@/lib/api/schema";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "@/lib/api/problem";
import type { Locale } from "@/lib/i18n/config";

export type PortfolioCard = components["schemas"]["PortfolioCard"];
export type PortfolioProject = components["schemas"]["PortfolioProjectView"];
export type MediaPair = components["schemas"]["MediaPair"];

export const PROPERTY_TYPES = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL", "OTHER"] as const;
export const WORK_TYPES = ["WALLS", "CEILING", "FACADE", "WOOD", "METAL", "DECORATIVE"] as const;

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

function requestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export interface PortfolioFilters {
  property_type?: string;
  work_type?: string;
  city?: string;
}

interface Opts {
  locale: Locale;
  revalidate?: number;
  signal?: AbortSignal;
}

async function apiGet<T>(path: string, query: Record<string, string | number | undefined>, o: Opts): Promise<T> {
  const url = new URL(API_BASE + path);
  for (const [k, v] of Object.entries(query)) if (v !== undefined && v !== "") url.searchParams.set(k, String(v));
  let res: Response;
  try {
    res = await fetch(url, {
      headers: { "Accept-Language": o.locale, "X-Request-Id": requestId() },
      signal: o.signal,
      ...(o.revalidate !== undefined ? { next: { revalidate: o.revalidate } } : {}),
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
  }
  if (!res.ok) {
    const body: unknown = await res.json().catch(() => undefined);
    if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as T;
}

export type PortfolioPage = components["schemas"]["PageResponsePortfolioCard"];

export function getPortfolioList(filters: PortfolioFilters, page: number, o: Opts): Promise<PortfolioPage> {
  return apiGet<PortfolioPage>("/api/v1/portfolio", { ...filters, page, size: 24 }, o);
}

/** Все slug'и проектов — для sitemap. */
export async function listPortfolioSlugs(o: Opts): Promise<string[]> {
  const slugs: string[] = [];
  for (let page = 0; page < 50; page++) {
    const res = await apiGet<PortfolioPage>("/api/v1/portfolio", { page, size: 100 }, o);
    for (const p of res.items ?? []) if (p.slug) slugs.push(p.slug);
    if (page + 1 >= (res.total_pages ?? 0)) break;
  }
  return slugs;
}

export function getPortfolioProject(slug: string, o: Opts): Promise<PortfolioProject> {
  return apiGet<PortfolioProject>(`/api/v1/portfolio/${encodeURIComponent(slug)}`, {}, o);
}
