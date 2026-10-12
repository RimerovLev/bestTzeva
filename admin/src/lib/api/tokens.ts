/**
 * Access-токен живёт ТОЛЬКО в памяти вкладки. В localStorage/sessionStorage ничего, связанного с
 * аутентификацией, не кладётся: refresh — в httpOnly-куке (B-01), её фронт не видит. Перезагрузка
 * вкладки теряет access из памяти — он восстанавливается refresh'ем по куке при старте.
 */
let accessToken: string | null = null;

type AuthLostListener = () => void;
const authLostListeners = new Set<AuthLostListener>();

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

/** Подписка на «сессия окончательно потеряна» (refresh не удался) — провайдер уводит на логин. */
export function onAuthLost(listener: AuthLostListener): () => void {
  authLostListeners.add(listener);
  return () => authLostListeners.delete(listener);
}

export function notifyAuthLost(): void {
  clearAccessToken();
  for (const listener of authLostListeners) listener();
}
