"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

/**
 * Список заявки на товар — замена корзины. Собирается на клиенте, хранится в localStorage до
 * отправки (на сервере ему делать нечего). F-2.2 добавляет позиции, F-3.1 покажет панель и форму.
 * Снимок названия/цвета/объёма/цены фиксирует, что видел клиент в момент добавления.
 */
export interface RequestItem {
  productId: number;
  slug: string;
  variantId: number;
  name: string;
  colorCode: string | null;
  colorHex: string | null;
  volumeLiters: number | null;
  price: number | null;
  qty: number;
}

interface RequestValue {
  items: RequestItem[];
  count: number;
  has: (variantId: number) => boolean;
  add: (item: Omit<RequestItem, "qty">) => void;
  remove: (variantId: number) => void;
  setQty: (variantId: number, qty: number) => void;
  clear: () => void;
}

const STORAGE_KEY = "tb.request.items";
const RequestContext = createContext<RequestValue | null>(null);

function readStored(): RequestItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as RequestItem[]) : [];
  } catch {
    // приватный режим браузера бросает — тогда список живёт только в памяти
    return [];
  }
}

export function RequestProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<RequestItem[]>([]);

  // Читаем из localStorage только на клиенте после монтирования (иначе рассинхрон с SSR).
  useEffect(() => setItems(readStored()), []);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      // недоступный localStorage — не роняем приложение
    }
  }, [items]);

  const has = useCallback((variantId: number) => items.some((i) => i.variantId === variantId), [items]);

  const add = useCallback((item: Omit<RequestItem, "qty">) => {
    setItems((prev) =>
      prev.some((i) => i.variantId === item.variantId) ? prev : [...prev, { ...item, qty: 1 }],
    );
  }, []);

  const remove = useCallback((variantId: number) => {
    setItems((prev) => prev.filter((i) => i.variantId !== variantId));
  }, []);

  const setQty = useCallback((variantId: number, qty: number) => {
    setItems((prev) =>
      prev.map((i) => (i.variantId === variantId ? { ...i, qty: Math.max(1, qty) } : i)),
    );
  }, []);

  const clear = useCallback(() => setItems([]), []);

  const value = useMemo<RequestValue>(
    () => ({ items, count: items.length, has, add, remove, setQty, clear }),
    [items, has, add, remove, setQty, clear],
  );

  return <RequestContext.Provider value={value}>{children}</RequestContext.Provider>;
}

export function useRequest(): RequestValue {
  const ctx = useContext(RequestContext);
  if (!ctx) throw new Error("useRequest must be used within RequestProvider");
  return ctx;
}
