import { API_BASE, authFetch } from "@/lib/api/http";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "@/lib/api/problem";

export const PROPERTY_TYPES = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL", "OTHER"] as const;
export const WORK_TYPES = ["WALLS", "CEILING", "FACADE", "WOOD", "METAL", "DECORATIVE"] as const;
export const MEDIA_KINDS = ["BEFORE", "AFTER", "PROCESS"] as const;
export type PropertyType = (typeof PROPERTY_TYPES)[number];
export type WorkType = (typeof WORK_TYPES)[number];
export type MediaKind = (typeof MEDIA_KINDS)[number];

export interface PortfolioAdminCard {
  id: number;
  slug: string;
  title: string | null;
  property_type: PropertyType | null;
  city: string | null;
  completed_at: string | null;
  published: boolean;
  media_count: number;
}

export interface PortfolioTranslation {
  locale: string;
  title: string;
  description: string | null;
  meta_title: string | null;
  meta_description: string | null;
}

export interface PortfolioMedia {
  id: number;
  url: string;
  kind: MediaKind;
  pair_group: string | null;
  sort_order: number;
}

export interface PortfolioAdminView {
  id: number;
  slug: string;
  property_type: PropertyType | null;
  city: string | null;
  area_sqm: number | null;
  duration_days: number | null;
  completed_at: string | null;
  work_types: WorkType[];
  published: boolean;
  translations: PortfolioTranslation[];
  media: PortfolioMedia[];
}

/** Тело создания/обновления (snake_case). HE-перевод обязателен (проверяет и сервер). */
export interface PortfolioSave {
  slug: string;
  property_type?: PropertyType | null;
  city?: string | null;
  area_sqm?: number | null;
  duration_days?: number | null;
  completed_at?: string | null;
  work_types?: WorkType[];
  translations: PortfolioTranslation[];
}

async function fail(res: Response): Promise<never> {
  const body: unknown = await res.json().catch(() => undefined);
  if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
  throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
}

async function jsonReq<T>(path: string, method: string, body?: unknown): Promise<T> {
  const res = await authFetch(`${API_BASE}/api/v1/admin/portfolio${path}`, {
    method,
    ...(body !== undefined ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}),
  });
  if (!res.ok) return fail(res);
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listPortfolio(): Promise<PortfolioAdminCard[]> {
  return jsonReq<PortfolioAdminCard[]>("", "GET");
}

export function getPortfolio(id: number): Promise<PortfolioAdminView> {
  return jsonReq<PortfolioAdminView>(`/${id}`, "GET");
}

export function createPortfolio(body: PortfolioSave): Promise<{ id: number }> {
  return jsonReq<{ id: number }>("", "POST", body);
}

export function updatePortfolio(id: number, body: PortfolioSave): Promise<void> {
  return jsonReq<void>(`/${id}`, "PUT", body);
}

export function deletePortfolio(id: number): Promise<void> {
  return jsonReq<void>(`/${id}`, "DELETE");
}

export function setPortfolioPublished(id: number, value: boolean): Promise<void> {
  return jsonReq<void>(`/${id}/published?value=${value}`, "PATCH");
}

export function deletePortfolioMedia(mediaId: number): Promise<void> {
  return jsonReq<void>(`/media/${mediaId}`, "DELETE");
}

export async function uploadPortfolioMedia(
  id: number,
  file: File,
  kind: MediaKind,
  pairGroup?: string,
): Promise<PortfolioMedia> {
  const form = new FormData();
  form.append("file", file);
  const query = new URLSearchParams({ kind });
  if (pairGroup) query.set("pair_group", pairGroup);
  const res = await authFetch(`${API_BASE}/api/v1/admin/portfolio/${id}/media?${query.toString()}`, {
    method: "POST",
    body: form,
  });
  if (!res.ok) return fail(res);
  return (await res.json()) as PortfolioMedia;
}
