"use client";

import { forwardRef, useEffect, useImperativeHandle, useRef } from "react";

/** Site key публичный (вшивается в страницу). Пусто → капча выключена (фронт шлёт запрос без токена). */
const SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY ?? "";
export const turnstileEnabled = SITE_KEY.length > 0;

type TurnstileApi = {
  render: (el: HTMLElement, opts: Record<string, unknown>) => string;
  execute: (id: string) => void;
  reset: (id: string) => void;
  remove: (id: string) => void;
};
declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

let scriptPromise: Promise<void> | null = null;
/** Грузим скрипт Turnstile один раз на страницу. */
function loadScript(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject(new Error("ssr"));
  if (window.turnstile) return Promise.resolve();
  if (scriptPromise) return scriptPromise;
  scriptPromise = new Promise((resolve, reject) => {
    const s = document.createElement("script");
    s.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error("turnstile load failed"));
    document.head.appendChild(s);
  });
  return scriptPromise;
}

export interface TurnstileHandle {
  /** Получить одноразовый токен (null — капча выключена или не получилось). */
  getToken: () => Promise<string | null>;
}

/**
 * Невидимый Turnstile: виджет рендерится без UI и запускается по требованию (execute) перед платной
 * покраской. Если Cloudflare решит показать челлендж — он появится в этом контейнере (interaction-only).
 */
export const TurnstileGate = forwardRef<TurnstileHandle>(function TurnstileGate(_props, ref) {
  const boxRef = useRef<HTMLDivElement>(null);
  const widgetId = useRef<string | null>(null);
  const pending = useRef<((token: string | null) => void) | null>(null);

  useEffect(() => {
    if (!turnstileEnabled) return;
    let cancelled = false;
    loadScript()
      .then(() => {
        if (cancelled || !boxRef.current || widgetId.current != null || !window.turnstile) return;
        widgetId.current = window.turnstile.render(boxRef.current, {
          sitekey: SITE_KEY,
          execution: "execute",
          appearance: "interaction-only",
          callback: (token: string) => { pending.current?.(token); pending.current = null; },
          "error-callback": () => { pending.current?.(null); pending.current = null; },
          "timeout-callback": () => { pending.current?.(null); pending.current = null; },
        });
      })
      .catch(() => { /* не удалось загрузить — getToken вернёт null */ });
    return () => {
      cancelled = true;
      if (widgetId.current != null && window.turnstile) {
        try { window.turnstile.remove(widgetId.current); } catch { /* ignore */ }
        widgetId.current = null;
      }
    };
  }, []);

  useImperativeHandle(ref, () => ({
    async getToken() {
      if (!turnstileEnabled) return null;
      try {
        await loadScript();
      } catch {
        return null;
      }
      if (widgetId.current == null || !window.turnstile) return null;
      const id = widgetId.current;
      return new Promise<string | null>((resolve) => {
        pending.current = resolve;
        const safety = setTimeout(() => { if (pending.current) { pending.current(null); pending.current = null; } }, 20000);
        const done = (t: string | null) => { clearTimeout(safety); resolve(t); };
        pending.current = done;
        try {
          window.turnstile!.reset(id);
          window.turnstile!.execute(id);
        } catch {
          clearTimeout(safety);
          pending.current = null;
          resolve(null);
        }
      });
    },
  }), []);

  if (!turnstileEnabled) return null;
  return <div ref={boxRef} className="vz-turnstile" />;
});
