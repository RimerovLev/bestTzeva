export const locales = ["he", "ru"] as const;
export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = "he";

export const dir: Record<Locale, "rtl" | "ltr"> = { he: "rtl", ru: "ltr" };

const STORAGE_KEY = "tb.admin.locale";

export function isLocale(value: string): value is Locale {
  return (locales as readonly string[]).includes(value);
}

/**
 * Текущая локаль в модульной переменной — чтобы клиент API мог проставлять Accept-Language
 * без обращения к React-дереву. Обновляется провайдером локали. Это НЕ состояние аутентификации,
 * поэтому предпочтение языка допустимо хранить в localStorage.
 */
let current: Locale = readStoredLocale();

export function getLocale(): Locale {
  return current;
}

export function setLocale(locale: Locale): void {
  current = locale;
  try {
    localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // приватный режим браузера — молча игнорируем, локаль останется в памяти
  }
}

function readStoredLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && isLocale(stored)) return stored;
  } catch {
    // недоступный localStorage — падать нельзя
  }
  return defaultLocale;
}
