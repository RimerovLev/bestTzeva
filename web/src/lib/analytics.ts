/**
 * События аналитики (Plausible). Plausible не ставит cookies и не собирает персональные данные,
 * поэтому баннер согласия не нужен (F-5.2 п.7). Если скрипт ещё не загрузился (он грузится лениво,
 * после интерактива), вызов встаёт в очередь `plausible.q` — так работает официальный сниппет.
 * В свойства событий не кладём ничего личного: ни телефонов, ни имён, ни номеров заявок.
 */
export type AnalyticsEvent =
  | "Product Request Submitted"
  | "Measure Request Submitted"
  | "Callback Request Submitted"
  | "Coverage Calculated"
  | "Service Calculator Used";

type Props = Record<string, string | number | boolean>;
type PlausibleFn = ((event: string, options?: { props?: Props }) => void) & { q?: unknown[][] };

export function track(event: AnalyticsEvent, props?: Props): void {
  if (typeof window === "undefined") return;
  try {
    const w = window as unknown as { plausible?: PlausibleFn };
    if (!w.plausible) {
      const stub = ((...args: unknown[]) => { (stub.q = stub.q ?? []).push(args); }) as PlausibleFn;
      w.plausible = stub;
    }
    w.plausible(event, props ? { props } : undefined);
  } catch {
    // аналитика никогда не ломает основной поток
  }
}
