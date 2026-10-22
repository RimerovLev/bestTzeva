import { createRootRoute, createRoute, createRouter, Outlet } from "@tanstack/react-router";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { DashboardPage } from "@/features/dashboard/DashboardPage";
import { SectionPage } from "@/routes/SectionPage";
import { CatalogPage } from "@/features/catalog/CatalogPage";
import { CategoriesPage } from "@/features/catalog/CategoriesPage";
import { ColorsAdminPage } from "@/features/catalog/ColorsAdminPage";
import { InventoryPage } from "@/features/inventory/InventoryPage";
import { ProductRequestsPage } from "@/features/requests/ProductRequestsPage";
import { ServiceRequestsPage } from "@/features/service-requests/ServiceRequestsPage";
import { AuditPage } from "@/features/audit/AuditPage";
import { PrivacyPage } from "@/features/privacy/PrivacyPage";
import { BlockListPage } from "@/features/spam/BlockListPage";
import { UsersPage } from "@/features/users/UsersPage";
import { SECTIONS, type SectionKey } from "@/lib/auth/roles";

/** Реализованные разделы: путь → компонент. Остальные пока показывают заглушку SectionPage. */
const IMPLEMENTED: Partial<Record<SectionKey, () => React.ReactNode>> = {
  productRequests: ProductRequestsPage,
  serviceRequests: ServiceRequestsPage,
  catalog: CatalogPage,
  categories: CategoriesPage,
  colors: ColorsAdminPage,
  warehouse: InventoryPage,
  spam: BlockListPage,
  audit: AuditPage,
  privacy: PrivacyPage,
  users: UsersPage,
};

const rootRoute = createRootRoute({
  component: () => (
    <AdminLayout>
      <Outlet />
    </AdminLayout>
  ),
});

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: DashboardPage,
});

// Разделы (кроме дашборда) — заглушки до F-7…F-10. Пути берутся из единого списка SECTIONS.
const sectionRoutes = SECTIONS.filter((section) => section.path !== "/").map((section) => {
  const Implemented = IMPLEMENTED[section.key];
  return createRoute({
    getParentRoute: () => rootRoute,
    path: section.path,
    component: Implemented
      ? () => <Implemented />
      : () => <SectionPage sectionKey={section.key} path={section.path} />,
  });
});

const routeTree = rootRoute.addChildren([indexRoute, ...sectionRoutes]);

export const router = createRouter({ routeTree, defaultPreload: "intent" });
