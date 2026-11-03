import { API_BASE, authFetch } from "@/lib/api/http";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "@/lib/api/problem";

/** Слот картинки сайта (ответ бэка, snake_case). url=null — слот не задан, витрина берёт дефолт. */
export interface SiteImageView {
  slot: string;
  url: string | null;
  content_type: string | null;
  updated_at: string | null;
}

/** Порядок слотов в UI. Подписи берутся из словаря (dict.siteImages.slots). */
export const SITE_IMAGE_SLOTS = [
  "hero_can",
  "works_before",
  "works_after",
  "banner",
  "logo",
  "social_1",
  "social_2",
  "social_3",
  "social_4",
  "social_5",
  "social_6",
] as const;
export type SiteImageSlot = (typeof SITE_IMAGE_SLOTS)[number];

async function fail(res: Response): Promise<never> {
  const body: unknown = await res.json().catch(() => undefined);
  if (isProblemDetail(body)) throw ApiError.fromProblem(body, res.status);
  throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
}

export async function listSiteImages(): Promise<SiteImageView[]> {
  const res = await authFetch(`${API_BASE}/api/v1/admin/site-images`);
  if (!res.ok) return fail(res);
  return (await res.json()) as SiteImageView[];
}

export async function uploadSiteImage(slot: string, file: File): Promise<SiteImageView> {
  const form = new FormData();
  form.append("file", file);
  // Content-Type НЕ задаём — браузер сам проставит multipart boundary.
  const res = await authFetch(`${API_BASE}/api/v1/admin/site-images/${encodeURIComponent(slot)}`, {
    method: "POST",
    body: form,
  });
  if (!res.ok) return fail(res);
  return (await res.json()) as SiteImageView;
}

export async function resetSiteImage(slot: string): Promise<void> {
  const res = await authFetch(`${API_BASE}/api/v1/admin/site-images/${encodeURIComponent(slot)}`, {
    method: "DELETE",
  });
  if (!res.ok && res.status !== 204) return fail(res);
}
