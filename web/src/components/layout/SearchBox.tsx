"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { Locale } from "@/lib/i18n/config";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { getProducts, type ProductSummary } from "@/lib/catalog/api";
import { formatPrice } from "@/lib/format";

const MIN_CHARS = 2;
const DEBOUNCE_MS = 300;
const MAX_SUGGESTIONS = 6;

/**
 * Поиск в шапке с подсказками. Запрос уходит с задержкой 300 мс, минимум 2 символа, каждый новый
 * ввод отменяет предыдущий (AbortController). Устаревшие ответы не перетирают свежие: применяем
 * только результат последнего по счётчику запроса. Строку не нормализуем и пробелы внутри не режем —
 * это делает бэкенд (иврит с огласовками и без, англ., рус.).
 */
export function SearchBox({ locale, dict }: { locale: Locale; dict: Dictionary }) {
  const router = useRouter();
  const [value, setValue] = useState("");
  const [suggestions, setSuggestions] = useState<ProductSummary[]>([]);
  const [open, setOpen] = useState(false);

  const seq = useRef(0);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const query = value.trim();
    if (query.length < MIN_CHARS) {
      abortRef.current?.abort();
      setSuggestions([]);
      setOpen(false);
      return;
    }

    const timer = setTimeout(() => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      const requestId = ++seq.current;

      // Отправляем строку как есть (без нормализации).
      getProducts({ q: value }, 0, { locale, signal: controller.signal })
        .then((res) => {
          // Свежесть: применяем только если это последний запрос.
          if (requestId !== seq.current) return;
          setSuggestions((res.products?.items ?? []).slice(0, MAX_SUGGESTIONS));
          setOpen(true);
        })
        .catch(() => {
          // отменённый запрос или ошибка — тихо игнорируем, подсказки не трогаем
        });
    }, DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [value, locale]);

  function submit(e: FormEvent) {
    e.preventDefault();
    const query = value.trim();
    if (query.length < MIN_CHARS) return;
    setOpen(false);
    router.push(`/${locale}/products?q=${encodeURIComponent(value)}`);
  }

  return (
    <form onSubmit={submit} className="relative w-full max-w-xs" role="search">
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={dict.search.placeholder}
        aria-label={dict.search.label}
        className="w-full rounded-control border border-line-strong bg-surface px-3 py-2 text-sm text-ink outline-none transition-colors focus:border-brass focus-visible:ring-2 focus-visible:ring-terracotta"
      />

      {open && (
        <div className="absolute inset-inline-start-0 top-full z-40 mt-1 w-full overflow-hidden rounded-card border border-line bg-surface shadow-card">
          {suggestions.length === 0 ? (
            <p className="px-3 py-3 text-sm text-ink-faint">{dict.search.noSuggestions}</p>
          ) : (
            <ul>
              {suggestions.map((s) => (
                <li key={s.id}>
                  <Link
                    href={`/${locale}/products/${s.slug ?? s.id}`}
                    onMouseDown={(e) => e.preventDefault()}
                    className="flex items-center justify-between gap-3 px-3 py-2 text-sm hover:bg-paper-warm"
                  >
                    <span className="truncate text-ink">{s.name}</span>
                    <span className="numeric shrink-0 text-xs text-ink-soft">
                      <bdi>{formatPrice(locale, s.min_price ?? 0)}</bdi>
                    </span>
                  </Link>
                </li>
              ))}
              <li className="border-t border-line">
                <button
                  type="submit"
                  onMouseDown={(e) => e.preventDefault()}
                  className="w-full px-3 py-2 text-start text-sm font-medium text-brass hover:bg-paper-warm"
                >
                  {dict.search.viewAll.replace("{q}", value.trim())}
                </button>
              </li>
            </ul>
          )}
        </div>
      )}
    </form>
  );
}
