import type { ReactNode } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";

/** Оболочка админки: тёмная боковая панель + светлая рабочая область с шапкой. */
export function AdminLayout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return null;

  return (
    <div className="flex h-full">
      <Sidebar role={user.role} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header />
        <main className="flex-1 overflow-auto bg-paper p-6">{children}</main>
      </div>
    </div>
  );
}
