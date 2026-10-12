/**
 * Локали витрины. Иврит — основной, RTL. i18n заложена на три локали (en добавляется
 * позже без переделок), наполняются пока две.
 */
export const locales = ["he", "ru"] as const;
export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = "he";

/** Направление письма локали. */
export const dir: Record<Locale, "rtl" | "ltr"> = {
  he: "rtl",
  ru: "ltr",
};

export function isLocale(value: string): value is Locale {
  return (locales as readonly string[]).includes(value);
}
