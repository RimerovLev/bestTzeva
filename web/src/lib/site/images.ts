import "server-only";

import type { CSSProperties } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** slot → CSS-переменная витрины. Дефолт картинки задан в CSS (fallback у var()). */
const SLOT_VARS: Record<string, string> = {
  hero_can: "--img-hero-can",
  works_before: "--img-works-before",
  works_after: "--img-works-after",
  banner: "--img-banner",
  logo: "--img-logo",
};

/**
 * Карта slot→url редактируемых овнером картинок сайта (из бэка). Любой сбой/пустой ответ → {}:
 * витрина тогда показывает дефолтные картинки из сборки (fallback в var()).
 */
export async function getSiteImages(): Promise<Record<string, string>> {
  try {
    const res = await fetch(`${API_BASE}/api/v1/site-images`, { next: { revalidate: 60 } });
    if (!res.ok) return {};
    const data: unknown = await res.json();
    if (data && typeof data === "object" && !Array.isArray(data)) {
      return data as Record<string, string>;
    }
    return {};
  } catch {
    return {};
  }
}

/** Inline-style с CSS-переменными: только заданные слоты переопределяют дефолт из CSS. */
export function siteImageVars(images: Record<string, string>): CSSProperties {
  const vars: Record<string, string> = {};
  for (const [slot, cssVar] of Object.entries(SLOT_VARS)) {
    const url = images[slot];
    if (typeof url === "string" && url) {
      vars[cssVar] = `url("${url}")`;
    }
  }
  return vars as CSSProperties;
}
