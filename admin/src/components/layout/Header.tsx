import { LogOut } from "lucide-react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";
import { LanguageToggle } from "./LanguageToggle";

/** Шапка рабочей области: имя пользователя, роль, язык и выход. */
export function Header() {
  const { dict } = useI18n();
  const { user, logout } = useAuth();

  const name = [user?.firstName, user?.lastName].filter(Boolean).join(" ") || user?.email || user?.phone || "";
  const roleLabel = user ? dict.roles[user.role] : "";

  return (
    <header className="flex items-center justify-between gap-4 border-b border-line bg-surface px-6 py-3">
      <div />
      <div className="flex items-center gap-4">
        <LanguageToggle />
        <div className="text-end">
          <p className="text-sm font-medium text-navy">{name}</p>
          <p className="text-xs text-ink-faint">{roleLabel}</p>
        </div>
        <button
          type="button"
          onClick={() => void logout()}
          className="inline-flex items-center gap-2 rounded-control border border-line-strong px-3 py-2 text-sm text-ink-soft transition-colors hover:bg-paper-warm focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
        >
          <LogOut size={16} aria-hidden />
          {dict.header.logout}
        </button>
      </div>
    </header>
  );
}
