import { ApiError, CLIENT_ERROR_CODES } from "./problem";

/**
 * Плоский словарь текстов ошибок: код RFC 9457 → человеческий текст локали.
 * Живёт в dictionaries/<locale>.json под ключом `errors`.
 */
export type ErrorMessages = Record<string, string>;

export interface ResolvedError {
  /** Текст для пользователя. */
  text: string;
  /** trace_id — выводится мелким шрифтом рядом с сообщением, для поддержки. */
  traceId?: string;
}

/**
 * Текст ошибки для пользователя. Порядок: свой текст по `code` из словаря → серверный
 * `detail` → общий запасной текст. Никогда не показываем «сырой» код или стек.
 */
export function resolveErrorMessage(messages: ErrorMessages, error: unknown): ResolvedError {
  if (error instanceof ApiError) {
    const own = messages[error.code];
    const fallback = messages[CLIENT_ERROR_CODES.unknown] ?? error.code;
    return {
      text: own ?? error.detail ?? fallback,
      traceId: error.traceId,
    };
  }
  return { text: messages[CLIENT_ERROR_CODES.unknown] ?? "Error" };
}

/**
 * Пофайловые ошибки валидации по именам полей (для подсветки конкретных полей формы).
 * Возвращает первое сообщение на поле.
 */
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
