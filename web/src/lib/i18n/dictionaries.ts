import "server-only";
import type { Locale } from "./config";

/**
 * Словари локали грузятся на сервере. Тексты интерфейса только здесь — строк на иврите
 * или русском в разметке быть не должно ни одной, включая ошибки, плейсхолдеры и подписи.
 */
export type Dictionary = typeof import("@/dictionaries/he.json");

const loaders: Record<Locale, () => Promise<Dictionary>> = {
  he: () => import("@/dictionaries/he.json").then((m) => m.default),
  ru: () => import("@/dictionaries/ru.json").then((m) => m.default),
};

export function getDictionary(locale: Locale): Promise<Dictionary> {
  return loaders[locale]();
}
