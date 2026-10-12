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
 * Поиск в каталоге с подсказками. Запрос уходит с задержкой 300 мс, минимум 2 символа, каждый новый
 * ввод отменяет предыдущий (AbortController). Устаревшие ответы не перетирают свежие: применяем
 * только результат последнего по счётчику запроса. Строку не нормализуем и пробелы внутри не режем —
 * это делает бэкенд (иврит с огласовками и без, англ., рус.).
 */
export function SearchBox({
  locale,
  dict,
  initial = "",
  onSearch,
}: {
  locale: Locale;
  dict: Dictionary;
  initial?: string;
  /** Есть — поиск применяется к текущей выборке (каталог), нет — переход в каталог с ?q. */
  onSearch?: (q: string | undefined) => void;
}) {
  const router = useRouter();
  const [value, setValue] = useState(initial);
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
    setOpen(false);
    if (onSearch) {
      // пустая строка — снять поиск, остальные фильтры остаются
      onSearch(query ? value : undefined);
      return;
    }
    if (query.length < MIN_CHARS) return;
    router.push(`/${locale}/products?q=${encodeURIComponent(value)}`);
  }

  return (
    <form onSubmit={submit} className="srch" role="search">
      <svg className="srch-ico" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" aria-hidden>
        <circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" />
      </svg>
      <input
        type="search"
        className="inp"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder={dict.search.placeholder}
        aria-label={dict.search.label}
      />

      {open && (
        <div className="card srch-pop">
          {suggestions.length === 0 ? (
            <p className="none">{dict.search.noSuggestions}</p>
          ) : (
            <ul>
              {suggestions.map((s) => (
                <li key={s.id}>
                  <Link href={`/${locale}/products/${s.slug ?? s.id}`} onMouseDown={(e) => e.preventDefault()}>
                    <span>{s.name}</span>
                    {s.min_price != null && <span className="p"><bdi>{formatPrice(locale, s.min_price)}</bdi></span>}
                  </Link>
                </li>
              ))}
              <li>
                <button type="submit" className="all" onMouseDown={(e) => e.preventDefault()}>
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
