import { api, unwrap } from "@/lib/api/http";

/** Выгрузка всех данных субъекта: секции-таблицы → строки. Каждый вызов пишется в аудит. */
export type SubjectExport = Record<string, unknown>;

export async function exportSubject(contact: string): Promise<SubjectExport> {
  return unwrap(await api.POST("/api/v1/admin/privacy/export", { body: { contact } })) as SubjectExport;
}

/** Обезличивание по контакту; сервер возвращает число обезличенных заявок. Необратимо. */
export async function anonymizeSubject(contact: string): Promise<number> {
  const res = unwrap(await api.POST("/api/v1/admin/privacy/anonymize", { body: { contact } })) as { anonymized?: number };
  return Number(res.anonymized ?? 0);
}

/**
 * Ключ для сверки двух вводов одного контакта при подтверждении (не для запроса — на сервер контакт
 * уходит как введён, приводит его сервер): email без регистра, телефон по цифрам, 972… ≡ 0….
 */
export function sameContactKey(raw: string): string {
  const v = raw.trim();
  if (v.includes("@")) return v.toLowerCase();
  const digits = v.replace(/\D/g, "");
  return digits.startsWith("972") ? `0${digits.slice(3)}` : digits;
}
