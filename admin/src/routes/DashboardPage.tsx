import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";

/** Заглушка дашборда (полноценный — F-10.1). Приветствие + метка «в разработке». */
export function DashboardPage() {
  const { dict } = useI18n();
  const { user } = useAuth();
  const name = user?.firstName || user?.email || user?.phone || "";

  return (
    <section>
      <h1 className="text-h1 font-bold text-navy">
        {dict.nav.dashboard}
        {name && <span className="text-ink-faint"> · {name}</span>}
      </h1>
      <p className="mt-4 inline-block rounded-card border border-line bg-surface px-6 py-4 text-ink-faint shadow-card">
        {dict.common.comingSoon}
      </p>
    </section>
  );
}
