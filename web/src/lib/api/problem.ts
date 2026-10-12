/**
 * Формат ошибок бэкенда — RFC 9457 (application/problem+json). Бэкенд отдаёт его везде.
 * Пользователю текст берётся из словаря локали по `code`; серверный `detail` показывается
 * только если своего текста под этот код нет. `trace_id` ведёт поддержку к запросу в логах.
 */
export interface ProblemDetail {
  /** Машинный код ошибки — ключ для словаря локали. */
  code: string;
  /** Человеко-читаемая деталь с сервера (запасной текст). */
  detail?: string;
  /** Пофайловые/пополевые ошибки валидации: имя поля → сообщения. */
  errors?: Record<string, string[]>;
  /** Идентификатор запроса для связки с логами бэкенда. */
  trace_id?: string;
  /** HTTP-статус, если сервер его продублировал в теле. */
  status?: number;
  title?: string;
  type?: string;
}

export function isProblemDetail(value: unknown): value is ProblemDetail {
  return (
    typeof value === "object" &&
    value !== null &&
    "code" in value &&
    typeof (value as { code: unknown }).code === "string"
  );
}

/**
 * Единая ошибка клиента API. Бросается при любом неуспешном ответе, чтобы серверный код
 * и TanStack Query обрабатывали ошибку одинаково (через throw), а не разными путями.
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly detail?: string;
  readonly errors?: Record<string, string[]>;
  readonly traceId?: string;
  /** Значение заголовка Retry-After в секундах, если сервер его прислал. */
  readonly retryAfter?: number;

  constructor(params: {
    code: string;
    status: number;
    detail?: string;
    errors?: Record<string, string[]>;
    traceId?: string;
    retryAfter?: number;
  }) {
    super(params.detail ?? params.code);
    this.name = "ApiError";
    this.code = params.code;
    this.status = params.status;
    this.detail = params.detail;
    this.errors = params.errors;
    this.traceId = params.traceId;
    this.retryAfter = params.retryAfter;
  }

  static fromProblem(problem: ProblemDetail, status: number, retryAfter?: number): ApiError {
    return new ApiError({
      code: problem.code,
      status: problem.status ?? status,
      detail: problem.detail,
      errors: problem.errors,
      traceId: problem.trace_id,
      retryAfter,
    });
  }
}

/** Коды ошибок уровня транспорта (не приходят с сервера, генерируются на клиенте). */
export const CLIENT_ERROR_CODES = {
  network: "client.network_error",
  timeout: "client.timeout",
  unknown: "client.unknown",
} as const;
