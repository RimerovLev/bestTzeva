import { ApiError, CLIENT_ERROR_CODES } from "./problem";

export type ErrorMessages = Record<string, string>;

export interface ResolvedError {
  text: string;
  traceId?: string;
}

/** Текст ошибки: свой по `code` из словаря → серверный `detail` → общий запас. */
export function resolveErrorMessage(messages: ErrorMessages, error: unknown): ResolvedError {
  if (error instanceof ApiError) {
    const own = messages[error.code];
    const fallback = messages[CLIENT_ERROR_CODES.unknown] ?? error.code;
    return { text: own ?? error.detail ?? fallback, traceId: error.traceId };
  }
  return { text: messages[CLIENT_ERROR_CODES.unknown] ?? "Error" };
}

/** Пофайловые ошибки валидации по именам полей (первое сообщение на поле). */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof ApiError && error.errors) {
    const out: Record<string, string> = {};
    for (const [field, msgs] of Object.entries(error.errors)) {
      if (msgs.length > 0) out[field] = msgs[0]!;
    }
    return out;
  }
  return {};
}
