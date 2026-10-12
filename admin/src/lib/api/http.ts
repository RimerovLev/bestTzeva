import createOpenApiClient, { type Client } from "openapi-fetch";
import type { paths as PublicPaths } from "./schema.public";
import type { paths as AdminPaths } from "./schema.admin";
import { getLocale } from "@/lib/i18n/config";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "./problem";
import { getAccessToken, notifyAuthLost, setAccessToken } from "./tokens";

/** Пути витрины (auth) и админ-группы не пересекаются — их пересечение типов даёт единый клиент. */
export type ApiPaths = PublicPaths & AdminPaths;

/** В dev — пусто: запросы на /api проксируются Vite на бэкенд (тот же origin ради куки). */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";
const REFRESH_PATH = "/api/v1/auth/refresh";

function requestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * Достраивает init: общие заголовки поверх уже существующих. openapi-fetch передаёт готовый
 * Request как `input`, поэтому заголовки (в т.ч. Content-Type и тело) берём и из него — иначе
 * наш init затёр бы Content-Type сериализованного JSON и сервер ответил бы 415.
 */
function withCommonHeaders(
  input: string | URL | Request,
  init: RequestInit | undefined,
  auth: boolean,
): RequestInit {
  const headers = new Headers();
  if (input instanceof Request) input.headers.forEach((value, key) => headers.set(key, value));
  if (init?.headers) new Headers(init.headers).forEach((value, key) => headers.set(key, value));
  headers.set("Accept-Language", getLocale());
  if (!headers.has("X-Request-Id")) headers.set("X-Request-Id", requestId());
  if (auth) {
    const token = getAccessToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }
  // credentials: include — иначе браузер не пошлёт httpOnly refresh-куку (B-01, CORS allowCredentials).
  return { ...init, headers, credentials: "include" };
}

/**
 * Эндпоинты, где 401 НЕ означает истёкший access и повторять через refresh нельзя:
 * сам refresh (иначе цикл) и login/register (401 = неверные данные, а не протухший токен).
 * /auth/me и /auth/logout сюда НЕ входят — им нужен Bearer и авто-обновление.
 */
function skipRefreshRetry(url: string): boolean {
  return /\/api\/v1\/auth\/(refresh|login|register)(\?|$)/.test(url);
}

// ── Single-flight refresh ───────────────────────────────────────────────────
// Параллельные 401 должны дождаться ОДНОГО общего refresh, а не запустить пять: держим промис
// в модуле. Бэкенд умеет окно снисхождения при гонке, но полагаться на него не надо.
let refreshPromise: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = doRefresh().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function doRefresh(): Promise<boolean> {
  try {
    // Без Authorization: refresh идёт по куке. Тело пустое.
    const url = API_BASE + REFRESH_PATH;
    const res = await fetch(url, withCommonHeaders(url, { method: "POST" }, false));
    if (!res.ok) return false;
    const body = (await res.json()) as { access_token?: string };
    if (!body.access_token) return false;
    setAccessToken(body.access_token);
    return true;
  } catch {
    return false;
  }
}

/**
 * Fetch с аутентификацией и авто-обновлением токена. На 401 (кроме самих auth-эндпоинтов и уже
 * повторённого запроса) делает один общий refresh и повторяет исходный запрос ровно раз. Если
 * refresh не удался — сессия потеряна.
 */
async function authFetch(input: string | URL | Request, init?: RequestInit): Promise<Response> {
  const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
  // Клон на случай повтора: первый fetch израсходует тело Request, для ретрая нужна свежая копия.
  const retrySource = input instanceof Request ? input.clone() : input;

  // Bearer добавляется всегда, когда токен есть (для login/refresh он просто игнорируется сервером).
  const response = await fetch(input, withCommonHeaders(input, init, true));
  if (response.status !== 401 || skipRefreshRetry(url)) return response;

  const refreshed = await refreshOnce();
  if (!refreshed) {
    notifyAuthLost();
    return response;
  }
  // повтор с новым access-токеном ровно один раз
  return fetch(retrySource, withCommonHeaders(retrySource, init, true));
}

/**
 * Springdoc представляет пагинацию как единый объект-параметр `page` (PageQuery). Разворачиваем его
 * в плоские query-параметры page/size/sort, которые ждёт Spring; остальные значения — как есть.
 */
function querySerializer(query: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value == null || value === "") continue;
    if (key === "page" && typeof value === "object") {
      const pq = value as { page?: number; size?: number };
      if (pq.page != null) sp.set("page", String(pq.page));
      if (pq.size != null) sp.set("size", String(pq.size));
    } else {
      sp.set(key, String(value));
    }
  }
  return sp.toString();
}

/** Типизированный клиент над сгенерированной схемой. Смена контракта ломает сборку в точке вызова. */
export function createApiClient(): Client<ApiPaths> {
  return createOpenApiClient<ApiPaths>({ baseUrl: API_BASE, fetch: authFetch, querySerializer });
}

/** Общий экземпляр клиента для приложения. */
export const api = createApiClient();

interface OpenApiResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** Разворачивает результат openapi-fetch: data при успехе, ApiError при ошибке (с логом trace_id). */
export function unwrap<T>(result: OpenApiResult<T>): T {
  const { data, error, response } = result;
  if (error !== undefined || !response.ok) {
    const retryAfterHeader = response.headers.get("Retry-After");
    const retryAfter = retryAfterHeader ? Number(retryAfterHeader) : undefined;
    const apiError = isProblemDetail(error)
      ? ApiError.fromProblem(error, response.status, Number.isFinite(retryAfter) ? retryAfter : undefined)
      : new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: response.status });
    if (apiError.traceId) {
      console.error(`[api] ${apiError.code} (trace_id=${apiError.traceId})`);
    }
    throw apiError;
  }
  return data as T;
}

export { API_BASE, refreshOnce, doRefresh };
