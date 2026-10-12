import type { MetadataRoute } from "next";
import { abs } from "@/lib/seo";

/**
 * Индексируется вся витрина, кроме API, внутренних путей Next и личных форм (заявка, замер —
 * они ещё и noindex в метаданных). Отфильтрованные выборки каталога не закрываем: их склеивает
 * canonical на /products.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/", "/_next/", "/*/request", "/*/services/measure"],
      },
    ],
    sitemap: abs("/sitemap.xml"),
  };
}
