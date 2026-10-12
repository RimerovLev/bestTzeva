import createOpenApiClient, { type Client } from "openapi-fetch";
import type { paths } from "./schema";
import { defaultLocale, type Locale } from "@/lib/i18n/config";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "./problem";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** Таймаут запроса по умолчанию. */
const DEFAULT_TIMEOUT_MS = 15_000;

function requestId(): string {
  // crypto.randomUUID есть и в Node 22, и в браузере.
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/**
 * Тонкая обёртка над fetch поверх openapi-fetch. Один модуль для сервера и клиента:
 * типы путей и ответов приходят из сгенерированной схемы, поэтому смена контракта на
 * бэкенде ломает сборку в точке вызова, а не проявляется в рантайме.
 *
 * В каждый запрос добавляются X-Request-Id (клиентский, бэкенд его подхватывает) и
 * Accept-Language из текущей локали. Таймаут — через AbortSignal.
 */
export function createApiClient(locale: Locale = defaultLocale): Client<paths> {
  const client = createOpenApiClient<paths>({
    baseUrl: API_BASE_URL,
    // прокидываем куки (refresh) на кросс-доменных запросах админки; витрине не мешает
    credentials: "include",
  });

  client.use({
    onRequest({ request }) {
      request.headers.set("Accept-Language", locale);
      if (!request.headers.has("X-Request-Id")) {
        request.headers.set("X-Request-Id", requestId());
      }
      return request;
    },
  });

  return client;
}

/** Ошибка транспорта → ApiError с клиентским кодом. */
function toTransportError(err: unknown): ApiError {
  if (err instanceof DOMException && err.name === "AbortError") {
    return new ApiError({ code: CLIENT_ERROR_CODES.timeout, status: 0 });
  }
  return new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
}

/** Структурная форма результата openapi-fetch — без его внутренних дженерик-ограничений. */
interface OpenApiResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/**
 * Разворачивает результат openapi-fetch: возвращает data при успехе, бросает ApiError при
 * ответе-ошибке. traceId логируется в консоль — по нему запрос находится в логах бэкенда.
 */
export function unwrap<T>(result: OpenApiResult<T>): T {
  const { data, error, response } = result;
  if (error !== undefined || !response.ok) {
    const retryAfterHeader = response.headers.get("Retry-After");
    const retryAfter = retryAfterHeader ? Number(retryAfterHeader) : undefined;
    const apiError = isProblemDetail(error)
      ? ApiError.fromProblem(error, response.status, Number.isFinite(retryAfter) ? retryAfter : undefined)
      : new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: response.status });

    if (apiError.traceId) {
      // eslint-disable-next-line no-console
      console.error(`[api] ${apiError.code} (trace_id=${apiError.traceId})`);
    }
    throw apiError;
  }
  return data as T;
}

/** AbortSignal с таймаутом для передачи в вызовы openapi-fetch (`signal`). */
export function timeoutSignal(ms: number = DEFAULT_TIMEOUT_MS): AbortSignal {
  return AbortSignal.timeout(ms);
}

export { toTransportError, API_BASE_URL };
