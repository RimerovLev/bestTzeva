import { api, refreshOnce, unwrap } from "./http";
import { clearAccessToken, setAccessToken } from "./tokens";
import type { components } from "./schema.public";
import type { Role } from "@/lib/auth/roles";

type MeResponse = components["schemas"]["MeResponse"];
type TokenResponse = components["schemas"]["TokenResponse"];

/** Нормализованный профиль сотрудника (camelCase для внутреннего использования). */
export interface SessionUser {
  id: number;
  email: string | null;
  phone: string | null;
  firstName: string | null;
  lastName: string | null;
  role: Role;
  locale: string | null;
}

function normalize(me: MeResponse): SessionUser {
  // Роль на проводе приходит в нижнем регистре (enum сериализуется как "admin"), хотя схема
  // OpenAPI объявляет её в верхнем. Приводим к верхнему — он используется в матрице ролей.
  const role = (me.role ?? "customer").toUpperCase() as Role;
  return {
    id: me.id ?? 0,
    email: me.email ?? null,
    phone: me.phone ?? null,
    firstName: me.first_name ?? null,
    lastName: me.last_name ?? null,
    role,
    locale: me.locale ?? null,
  };
}

function storeAccess(tokens: TokenResponse): void {
  if (tokens.access_token) setAccessToken(tokens.access_token);
}

/** Вход по email или телефону. Сервер ставит refresh в httpOnly-куку, access приходит в теле. */
export async function login(loginId: string, password: string): Promise<SessionUser> {
  const tokens = unwrap(
    await api.POST("/api/v1/auth/login", { body: { login: loginId, password } }),
  );
  storeAccess(tokens);
  return fetchMe();
}

export async function fetchMe(): Promise<SessionUser> {
  const me = unwrap(await api.GET("/api/v1/auth/me"));
  return normalize(me);
}

/**
 * Восстановление сессии при старте вкладки: access-токена в памяти нет, пробуем refresh по куке.
 * Успех → тянем профиль; неуспех → null (показываем форму входа).
 */
export async function bootstrap(): Promise<SessionUser | null> {
  const refreshed = await refreshOnce();
  if (!refreshed) return null;
  try {
    return await fetchMe();
  } catch {
    return null;
  }
}

/** Выход из текущей сессии: гасим серверную сессию и куку, затем чистим access из памяти. */
export async function logout(): Promise<void> {
  try {
    await api.POST("/api/v1/auth/logout", {});
  } catch {
    // даже если сервер ответил ошибкой — локально считаем себя вышедшими
  } finally {
    clearAccessToken();
  }
}
