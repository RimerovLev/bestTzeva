import Script from "next/script";

/**
 * Скрипт Plausible. Подключается, только если задан домен (NEXT_PUBLIC_PLAUSIBLE_DOMAIN) — локально и
 * на стенде аналитика по умолчанию выключена. Грузится lazyOnload: после интерактива, не мешая
 * отрисовке. Свой сервер Plausible — через NEXT_PUBLIC_PLAUSIBLE_SRC.
 */
export function Analytics() {
  const domain = process.env.NEXT_PUBLIC_PLAUSIBLE_DOMAIN?.trim();
  if (!domain) return null;
  const src = process.env.NEXT_PUBLIC_PLAUSIBLE_SRC?.trim() || "https://plausible.io/js/script.js";
  return <Script src={src} data-domain={domain} strategy="lazyOnload" />;
}
