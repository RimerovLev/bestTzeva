import type { components } from "@/lib/api/schema";
import { ApiError, CLIENT_ERROR_CODES, isProblemDetail } from "@/lib/api/problem";
import type { Locale } from "@/lib/i18n/config";

export type CalculateRequest = components["schemas"]["CalculateRequest"];
export type CalculateResponse = components["schemas"]["CalculateResponse"];
export type WorkTypeCost = components["schemas"]["WorkTypeCostView"];
export type PresignResponse = components["schemas"]["PresignResponse"];
export type ServiceRequestCreate = components["schemas"]["ServiceRequestCreate"];
export type ServiceRequestResponse = components["schemas"]["ServiceRequestResponse"];

export const PROPERTY_TYPES = ["APARTMENT", "HOUSE", "OFFICE", "COMMERCIAL", "OTHER"] as const;
export const WORK_TYPES = ["WALLS", "CEILING", "FACADE", "WOOD", "METAL", "DECORATIVE"] as const;
export const SERVICE_OPTIONS = ["FURNITURE_REMOVAL", "SURFACE_PREP", "PRIMING", "HEIGHT_WORK"] as const;

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

function requestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

/** Ключ переноса параметров калькулятора в форму замера (F-4.2 читает при старте). */
export const MEASURE_PREFILL_KEY = "tb.measure.prefill";

/**
 * Расчёт стоимости услуг покраски (F-4.1). Считается на сервере; результат всегда ориентировочный
 * (is_estimate + текст disclaimer с сервера). Лимит 60/час на IP — поэтому вызываем по кнопке.
 */
export async function calculateService(
  body: CalculateRequest,
  options: { locale: Locale; signal?: AbortSignal },
): Promise<CalculateResponse> {
  const url = `${API_BASE}/api/v1/service-requests/calculate`;
  let res: Response;
  try {
    res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept-Language": options.locale,
        "X-Request-Id": requestId(),
      },
      body: JSON.stringify(body),
      signal: options.signal,
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") throw err;
    throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
  }
  if (!res.ok) {
    const problem: unknown = await res.json().catch(() => undefined);
    const retry = res.headers.get("Retry-After");
    if (isProblemDetail(problem)) throw ApiError.fromProblem(problem, res.status, retry ? Number(retry) : undefined);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as CalculateResponse;
}

// ── Заявка на замер (F-4.2) ──────────────────────────────────────────────────

async function post<T>(path: string, body: unknown, locale: Locale): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept-Language": locale, "X-Request-Id": requestId() },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
  }
  if (!res.ok) {
    const problem: unknown = await res.json().catch(() => undefined);
    const retry = res.headers.get("Retry-After");
    if (isProblemDetail(problem)) throw ApiError.fromProblem(problem, res.status, retry ? Number(retry) : undefined);
    throw new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: res.status });
  }
  return (await res.json()) as T;
}

/** Токен формы (антиспам, минимальное время заполнения). */
export async function getFormToken(locale: Locale): Promise<string> {
  const data = await (async () => {
    try {
      const res = await fetch(`${API_BASE}/api/v1/service-requests/form-token`, { headers: { "Accept-Language": locale, "X-Request-Id": requestId() } });
      return (await res.json()) as { form_token?: string };
    } catch {
      throw new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 });
    }
  })();
  return data.form_token ?? "";
}

/** Presigned-адреса для прямой загрузки фото в хранилище (через бэкенд файлы не идут). */
export function presignPhotos(files: { filename: string; content_type: string }[], locale: Locale): Promise<PresignResponse> {
  return post<PresignResponse>("/api/v1/service-requests/photos/presign", { files }, locale);
}

/** Прямой PUT сжатого файла в хранилище по presigned-URL, с прогрессом. */
export function uploadToStorage(url: string, blob: Blob, onProgress?: (pct: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    xhr.setRequestHeader("Content-Type", blob.type || "image/jpeg");
    xhr.upload.onprogress = (e) => { if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100)); };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new ApiError({ code: CLIENT_ERROR_CODES.unknown, status: xhr.status })));
    xhr.onerror = () => reject(new ApiError({ code: CLIENT_ERROR_CODES.network, status: 0 }));
    xhr.send(blob);
  });
}

/** Создание заявки на замер с привязанными фото (object_key из presign). */
export function submitServiceRequest(body: ServiceRequestCreate, locale: Locale): Promise<ServiceRequestResponse> {
  return post<ServiceRequestResponse>("/api/v1/service-requests", body, locale);
}
