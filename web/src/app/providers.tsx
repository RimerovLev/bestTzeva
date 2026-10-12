"use client";

import { useState, type ReactNode } from "react";
import {
  QueryClient,
  QueryClientProvider,
  isServer,
} from "@tanstack/react-query";
import { ApiError } from "@/lib/api/problem";
import { RequestProvider } from "@/lib/request/RequestProvider";

/**
 * Клиентское серверное состояние — TanStack Query. Глобального стора нет.
 * Повторять запрос не нужно на ошибках 4xx (кроме 429): это ответ сервера, а не сбой сети.
 */
function makeQueryClient(): QueryClient {
  return new QueryClient({
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
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient(): QueryClient {
  if (isServer) return makeQueryClient();
  // В браузере переиспользуем один клиент, чтобы не терять кэш между рендерами.
  browserQueryClient ??= makeQueryClient();
  return browserQueryClient;
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(getQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <RequestProvider>{children}</RequestProvider>
    </QueryClientProvider>
  );
}
