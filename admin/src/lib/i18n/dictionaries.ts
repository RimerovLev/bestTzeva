import he from "@/dictionaries/he.json";
import ru from "@/dictionaries/ru.json";
import type { Locale } from "./config";

/**
 * Словари подключаются статически (клиентский SPA). Тексты интерфейса — только здесь;
 * строк на иврите/русском в разметке быть не должно, включая ошибки и подписи.
 */
export type Dictionary = typeof he;

const dictionaries: Record<Locale, Dictionary> = { he, ru };

export function dictionaryFor(locale: Locale): Dictionary {
  return dictionaries[locale];
}
