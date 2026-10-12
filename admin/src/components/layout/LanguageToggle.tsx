import { locales, type Locale } from "@/lib/i18n/config";
import { useI18n } from "@/lib/i18n/I18nProvider";

const LABELS: Record<Locale, string> = { he: "עברית", ru: "Русский" };

/** Переключатель языка админки. Меняет направление и гарнитуру, предпочтение хранится локально. */
export function LanguageToggle() {
  const { locale, setLocale, dict } = useI18n();
  return (
    <div className="flex items-center gap-1 text-sm" aria-label={dict.header.language}>
      {locales.map((target, i) => (
        <span key={target} className="flex items-center gap-1">
          {i > 0 && <span aria-hidden className="text-line-strong">·</span>}
          <button
            type="button"
            onClick={() => setLocale(target)}
            aria-current={target === locale ? "true" : undefined}
            className={`transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta ${
              target === locale ? "font-semibold text-navy" : "text-ink-soft hover:text-navy"
            }`}
          >
            {LABELS[target]}
          </button>
        </span>
      ))}
    </div>
  );
}
