/**
 * Клиент «примерки цвета»: шлёт фото комнаты и цвет на бэкенд, получает data-URL перекрашенной
 * комнаты. Вызывается из браузера (клиентский компонент), поэтому базовый адрес — из NEXT_PUBLIC.
 * Вызов платный и лимитирован по IP на бэкенде; фронт делает его только по явному действию.
 */

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export type RenderError = "not_configured" | "rate_limited" | "failed" | "network" | "too_large";

export type RenderResult =
  | { ok: true; image: string }
  | { ok: false; error: RenderError; retryAfter?: number };

/** Отправить фото+цвет на рендер. name — человекочитаемое имя цвета (для подсказки модели). */
export async function renderRoom(
  file: Blob,
  colorHex: string,
  name: string,
  signal?: AbortSignal,
): Promise<RenderResult> {
  const form = new FormData();
  form.append("file", file, "room.jpg");
  form.append("color", colorHex);
  if (name) form.append("name", name);

  let res: Response;
  try {
    res = await fetch(`${API_BASE}/api/v1/color-studio/render`, {
      method: "POST",
      body: form,
      signal,
    });
  } catch {
    return { ok: false, error: "network" };
  }

  if (res.ok) {
    try {
      const data = (await res.json()) as { image?: string };
      if (data.image) return { ok: true, image: data.image };
    } catch {
      /* падение парсинга → общая ошибка ниже */
    }
    return { ok: false, error: "failed" };
  }

  // problem+json: различаем выключенную фичу, лимит и прочие сбои по code/статусу
  let code = "";
  try {
    const problem = (await res.json()) as { code?: string };
    code = problem.code ?? "";
  } catch {
    /* тело не JSON */
  }
  if (res.status === 429) {
    const ra = Number(res.headers.get("Retry-After"));
    return { ok: false, error: "rate_limited", retryAfter: Number.isFinite(ra) ? ra : undefined };
  }
  if (res.status === 413 || code === "ai.image_too_large") return { ok: false, error: "too_large" };
  if (code === "ai.not_configured" || res.status === 503) return { ok: false, error: "not_configured" };
  return { ok: false, error: "failed" };
}
