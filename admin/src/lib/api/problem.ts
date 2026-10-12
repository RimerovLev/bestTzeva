/**
 * Формат ошибок бэкенда — RFC 9457 (application/problem+json). Пользователю текст берётся из
 * словаря локали по `code`; серверный `detail` — только если своего текста нет. `trace_id` ведёт
 * поддержку к запросу в логах.
 */
export interface ProblemDetail {
  code: string;
  detail?: string;
  errors?: Record<string, string[]>;
  trace_id?: string;
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

/** Единая ошибка клиента API: бросается при любом неуспешном ответе. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly detail?: string;
  readonly errors?: Record<string, string[]>;
  readonly traceId?: string;
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

export const CLIENT_ERROR_CODES = {
  network: "client.network_error",
  timeout: "client.timeout",
  unknown: "client.unknown",
} as const;
