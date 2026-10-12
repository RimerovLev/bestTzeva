import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath, type SectionKey } from "@/lib/auth/roles";

/**
 * Заглушка раздела до его реализации (F-7…F-10). Проверяет доступ роли к пути: недоступный раздел
 * показывает «нет прав», а не пустой экран (сама защита — на сервере).
 */
export function SectionPage({ sectionKey, path }: { sectionKey: SectionKey; path: string }) {
  const { user } = useAuth();
  const { dict } = useI18n();

  if (!user || !canAccessPath(user.role, path)) {
    return (
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1>
        <p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p>
      </div>
    );
  }

  return (
    <section>
      <h1 className="text-h1 font-bold text-navy">{dict.nav[sectionKey]}</h1>
      <p className="mt-4 inline-block rounded-card border border-line bg-surface px-6 py-4 text-ink-faint shadow-card">
        {dict.common.comingSoon}
      </p>
    </section>
  );
}
