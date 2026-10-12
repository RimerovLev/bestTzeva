import { useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { ProductsList } from "./ProductsList";
import { ProductForm } from "./ProductForm";
import { BulkPriceDialog } from "./BulkPriceDialog";
import { ImportDialog } from "./ImportDialog";

/**
 * Каталог товаров в админке (F-7.1). Список ↔ форма создания агрегата; массовое изменение цен в
 * модалке. Редактирование существующего товара заблокировано отсутствием GET /admin/products/{id}
 * (нет способа подгрузить полный агрегат для предзаполнения) — см. заметку задачи.
 */
export function CatalogPage() {
  const { user } = useAuth();
  const { dict } = useI18n();
  const [creating, setCreating] = useState(false);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  if (!user || !canAccessPath(user.role, "/catalog")) {
    return (
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1>
        <p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p>
      </div>
    );
  }

  if (creating) {
    return <ProductForm onDone={() => setCreating(false)} onCancel={() => setCreating(false)} />;
  }

  return (
    <>
      <ProductsList onNew={() => setCreating(true)} onBulk={() => setBulkOpen(true)} onImport={() => setImportOpen(true)} />
      <BulkPriceDialog open={bulkOpen} onClose={() => setBulkOpen(false)} />
      <ImportDialog open={importOpen} onClose={() => setImportOpen(false)} />
    </>
  );
}
