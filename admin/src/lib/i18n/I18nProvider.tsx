import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { defaultLocale, dir, getLocale, setLocale as persistLocale, type Locale } from "./config";
import { dictionaryFor, type Dictionary } from "./dictionaries";

interface I18nValue {
  locale: Locale;
  dict: Dictionary;
  setLocale: (locale: Locale) => void;
}

const I18nContext = createContext<I18nValue | null>(null);

function applyToDocument(locale: Locale): void {
  const root = document.documentElement;
  root.lang = locale;
  root.dir = dir[locale];
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => {
    const initial = getLocale();
    applyToDocument(initial);
    return initial;
  });

  const setLocale = useCallback((next: Locale) => {
    persistLocale(next);
    applyToDocument(next);
    setLocaleState(next);
  }, []);

  const value = useMemo<I18nValue>(
    () => ({ locale, dict: dictionaryFor(locale), setLocale }),
    [locale, setLocale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    // не должно случаться: провайдер оборачивает всё приложение
    return { locale: defaultLocale, dict: dictionaryFor(defaultLocale), setLocale: () => {} };
  }
  return ctx;
}
