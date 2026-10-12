import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { I18nProvider } from "@/lib/i18n/I18nProvider";
import { AuthProvider, useAuth } from "@/lib/auth/AuthProvider";
import { AppSkeleton } from "@/components/ui/Skeleton";
import { LoginScreen } from "@/routes/LoginScreen";
import { router } from "@/router";
import { ApiError } from "@/lib/api/problem";
import "@/styles/globals.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      retry(failureCount, error) {
        if (error instanceof ApiError) {
          if (error.status === 429) return failureCount < 2;
          if (error.status >= 400 && error.status < 500) return false;
        }
        return failureCount < 2;
      },
    },
  },
});

/**
 * Разводящий экран по статусу аутентификации: пока идёт refresh по куке — скелетон, нет сессии —
 * форма входа, есть — приложение с маршрутизацией. Так перезагрузка вкладки не выкидывает на логин.
 */
function AppGate() {
  const { status } = useAuth();
  if (status === "loading") return <AppSkeleton />;
  if (status === "anonymous") return <LoginScreen />;
  return <RouterProvider router={router} />;
}

const rootElement = document.getElementById("root");
if (!rootElement) throw new Error("#root not found");

createRoot(rootElement).render(
  <StrictMode>
    <I18nProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <AppGate />
        </AuthProvider>
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
);
