import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { bootstrap, login as sessionLogin, logout as sessionLogout, type SessionUser } from "@/lib/api/session";
import { onAuthLost } from "@/lib/api/tokens";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

interface AuthValue {
  status: AuthStatus;
  user: SessionUser | null;
  login: (loginId: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<SessionUser | null>(null);
  const started = useRef(false);

  // При старте вкладки access-токена нет — сразу пробуем refresh по куке, показываем скелетон,
  // а не форму входа. Ref гарантирует единственный прогон, включая двойной эффект StrictMode;
  // результат применяем всегда (провайдер верхнего уровня не размонтируется в ходе запроса).
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    bootstrap()
      .then((restored) => {
        setUser(restored);
        setStatus(restored ? "authenticated" : "anonymous");
      })
      .catch(() => {
        setUser(null);
        setStatus("anonymous");
      });
  }, []);

  // Refresh не удался на каком-то запросе — сессия потеряна, уводим на вход.
  useEffect(
    () =>
      onAuthLost(() => {
        setUser(null);
        setStatus("anonymous");
      }),
    [],
  );

  const login = useCallback(async (loginId: string, password: string) => {
    const authenticated = await sessionLogin(loginId, password);
    setUser(authenticated);
    setStatus("authenticated");
  }, []);

  const logout = useCallback(async () => {
    await sessionLogout();
    setUser(null);
    setStatus("anonymous");
  }, []);

  const value = useMemo<AuthValue>(() => ({ status, user, login, logout }), [status, user, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
