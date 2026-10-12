import type { Locale } from "@/lib/i18n/config";

const NUMBER_LOCALE: Record<Locale, string> = { he: "he-IL", ru: "ru-RU" };

/**
 * Цена в шекелях. Считается на сервере (BigDecimal) — на клиенте только форматируется.
 * Возвращает строку без направленческих меток; в RTL её оборачивают в <bdi class="numeric">,
 * иначе знак ₪ прыгает на другую сторону числа.
 */
export function formatPrice(locale: Locale, amount: number): string {
  const formatted = new Intl.NumberFormat(NUMBER_LOCALE[locale], {
    maximumFractionDigits: 2,
  }).format(amount);
  return `₪${formatted}`;
}

/** Диапазон цен вариантов: одно значение, если min == max, иначе «min–max». */
export function formatPriceRange(locale: Locale, min: number, max: number): string {
  if (min === max) return formatPrice(locale, min);
  return `${formatPrice(locale, min)}–${formatPrice(locale, max)}`;
}
