import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { resolveErrorMessage } from "@/lib/api/messages";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { createBrand, deleteBrand, listBrands } from "./refApi";

/** Бренды (MANAGER/ADMIN): список + создание + удаление (мягкое). Выбор бренда в форме товара берёт отсюда. */
export function BrandsPage() {
  const { user } = useAuth();
  const { dict } = useI18n();
  const t = dict.brands;
  const qc = useQueryClient();
  const allowed = !!user && canAccessPath(user.role, "/brands");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const q = useQuery({ queryKey: ["brands"], queryFn: listBrands, enabled: allowed });
  const invalidate = () => qc.invalidateQueries({ queryKey: ["brands"] });

  const createM = useMutation({
    mutationFn: () => createBrand(name.trim()),
    onSuccess: () => { setName(""); setError(null); invalidate(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });
  const delM = useMutation({
    mutationFn: (id: number) => deleteBrand(id),
    onSuccess: () => { setError(null); invalidate(); },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });

  return (
    <div className="space-y-4">
      <header>
        <h1 className="text-xl font-semibold">{t.title}</h1>
      </header>

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (name.trim()) createM.mutate();
        }}
      >
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t.namePlaceholder}
          className="flex-1 rounded-control border border-line-strong bg-surface px-3 py-2 text-body text-ink"
        />
        <Button type="submit" disabled={!name.trim() || createM.isPending}>
          {t.add}
        </Button>
      </form>

      {error && <p className="text-sm text-crit-fg">{error}</p>}

      {q.isLoading ? (
        <Skeleton className="h-32" />
      ) : q.isError ? (
        <p className="text-sm text-crit-fg">{t.loadError}</p>
      ) : (q.data ?? []).length === 0 ? (
        <p className="text-sm text-ink-soft">{t.empty}</p>
      ) : (
        <div className="overflow-hidden rounded-card border border-line">
          <table className="w-full text-sm">
            <tbody>
              {(q.data ?? []).map((b) => (
                <tr key={b.id} className="border-b border-line last:border-0">
                  <td className="px-3 py-2 font-medium">{b.name}</td>
                  <td className="px-3 py-2 text-ink-soft">{b.slug}</td>
                  <td className="px-3 py-2 text-right">
                    <Button
                      variant="muted"
                      onClick={() => {
                        if (window.confirm(t.confirmDelete)) delM.mutate(b.id);
                      }}
                    >
                      {t.delete}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
