import { Link } from "@tanstack/react-router";
import {
  LayoutDashboard,
  ClipboardList,
  Paintbrush,
  Boxes,
  Tags,
  FolderTree,
  Palette,
  BarChart3,
  ScrollText,
  Users,
  type LucideIcon,
} from "lucide-react";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { visibleSections, type Role, type SectionKey } from "@/lib/auth/roles";

const ICONS: Record<SectionKey, LucideIcon> = {
  dashboard: LayoutDashboard,
  productRequests: ClipboardList,
  serviceRequests: Paintbrush,
  warehouse: Boxes,
  catalog: Tags,
  categories: FolderTree,
  colors: Palette,
  reports: BarChart3,
  audit: ScrollText,
  users: Users,
};

/**
 * Тёмная боковая панель по третьему макету: логотип и разделы. Показываются только разделы,
 * доступные роли (удобство, не защита — сервер всё равно проверяет). Логотип не зеркалится.
 */
export function Sidebar({ role }: { role: Role }) {
  const { dict } = useI18n();
  const sections = visibleSections(role);

  return (
    <aside className="flex w-64 shrink-0 flex-col bg-navy-deep text-paper">
      <div className="flex items-center gap-3 border-b border-white/10 px-6 py-5">
        <div className="grid h-9 w-9 place-items-center rounded-control bg-brass font-bold text-navy-deep">
          ב
        </div>
        <div className="leading-tight">
          <p className="font-bold">{dict.app.name}</p>
          <p className="text-xs text-paper-warm/60">{dict.app.subtitle}</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1 p-3">
        {sections.map((section) => {
          const Icon = ICONS[section.key];
          return (
            <Link
              key={section.key}
              to={section.path}
              activeOptions={{ exact: section.path === "/" }}
              className="flex items-center gap-3 rounded-control px-3 py-2 text-sm text-paper-warm/80 transition-colors hover:bg-white/10 hover:text-paper"
              activeProps={{ className: "flex items-center gap-3 rounded-control px-3 py-2 text-sm bg-white/10 text-paper font-medium" }}
            >
              <Icon size={18} aria-hidden />
              <span>{dict.nav[section.key]}</span>
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
