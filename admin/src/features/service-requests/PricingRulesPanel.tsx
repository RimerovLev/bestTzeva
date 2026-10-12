import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { resolveErrorMessage } from "@/lib/api/messages";
import { listPricingRules, updatePricingRule, upper, type PricingRuleView } from "./api";

interface Draft {
  price_per_sqm_min: string;
  price_per_sqm_max: string;
  min_order_price: string;
  active: boolean;
}

export function PricingRulesPanel() {
  const { dict } = useI18n();
  const t = dict.serviceRequests;
  const qc = useQueryClient();

  const rulesQuery = useQuery({ queryKey: ["pricing-rules"], queryFn: listPricingRules });
  const [editId, setEditId] = useState<number | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<string | null>(null);

  const saveM = useMutation({
    mutationFn: ({ id, body }: { id: number; body: Draft }) =>
      updatePricingRule(id, {
        price_per_sqm_min: Number(body.price_per_sqm_min),
        price_per_sqm_max: Number(body.price_per_sqm_max),
        min_order_price: Number(body.min_order_price),
        active: body.active,
      }),
    onSuccess: () => {
      setEditId(null);
      setDraft(null);
      setError(null);
      qc.invalidateQueries({ queryKey: ["pricing-rules"] });
    },
    onError: (e) => setError(resolveErrorMessage(dict.errors, e).text),
  });

  function startEdit(r: PricingRuleView) {
    setEditId(r.id!);
    setDraft({
      price_per_sqm_min: String(r.price_per_sqm_min ?? ""),
      price_per_sqm_max: String(r.price_per_sqm_max ?? ""),
      min_order_price: String(r.min_order_price ?? ""),
      active: r.active ?? true,
    });
    setError(null);
  }

  if (rulesQuery.isLoading) return <Skeleton className="h-64 w-full" />;

  const rules = rulesQuery.data ?? [];

  return (
    <div className="space-y-3">
      <h2 className="text-h3 font-bold text-navy">{t.pricingTitle}</h2>
      <p className="rounded-control border border-amber-200 bg-amber-50/60 p-2 text-xs text-amber-800">{t.pricingNote}</p>
      {error && <div className="rounded-control border border-red-200 bg-red-50 p-2 text-sm text-red-700">{error}</div>}

      <div className="overflow-x-auto rounded-card border border-line">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-line bg-paper-warm/40 text-start text-xs text-ink-soft">
              <th className="p-2 text-start">{t.pricingColProperty}</th>
              <th className="p-2 text-start">{t.pricingColWork}</th>
              <th className="p-2 text-end">{t.pricingColMin}</th>
              <th className="p-2 text-end">{t.pricingColMax}</th>
              <th className="p-2 text-end">{t.pricingColMinOrder}</th>
              <th className="p-2 text-center">{t.pricingColActive}</th>
              <th className="p-2" />
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => {
              const editing = editId === r.id && draft;
              return (
                <tr key={r.id} className="border-b border-line last:border-0">
                  <td className="p-2 text-navy">
                    {dict.enums.property_type[upper(r.property_type) as keyof typeof dict.enums.property_type] ?? r.property_type}
                  </td>
                  <td className="p-2 text-navy">
                    {dict.enums.work_type[upper(r.work_type) as keyof typeof dict.enums.work_type] ?? r.work_type}
                  </td>
                  {editing ? (
                    <>
                      <td className="p-1">
                        <NumInput value={draft!.price_per_sqm_min} onChange={(v) => setDraft({ ...draft!, price_per_sqm_min: v })} />
                      </td>
                      <td className="p-1">
                        <NumInput value={draft!.price_per_sqm_max} onChange={(v) => setDraft({ ...draft!, price_per_sqm_max: v })} />
                      </td>
                      <td className="p-1">
                        <NumInput value={draft!.min_order_price} onChange={(v) => setDraft({ ...draft!, min_order_price: v })} />
                      </td>
                      <td className="p-2 text-center">
                        <input type="checkbox" checked={draft!.active} onChange={(e) => setDraft({ ...draft!, active: e.target.checked })} />
                      </td>
                      <td className="whitespace-nowrap p-1">
                        <Button variant="brass" disabled={saveM.isPending} onClick={() => saveM.mutate({ id: r.id!, body: draft! })}>
                          {dict.common.save}
                        </Button>{" "}
                        <Button variant="muted" disabled={saveM.isPending} onClick={() => { setEditId(null); setDraft(null); }}>
                          {dict.common.cancel}
                        </Button>
                      </td>
                    </>
                  ) : (
                    <>
                      <td className="numeric p-2 text-end">{r.price_per_sqm_min}</td>
                      <td className="numeric p-2 text-end">{r.price_per_sqm_max}</td>
                      <td className="numeric p-2 text-end">{r.min_order_price}</td>
                      <td className="p-2 text-center">
                        <span className={r.active ? "text-emerald-700" : "text-ink-soft"}>{r.active ? "✓" : "—"}</span>
                      </td>
                      <td className="p-1 text-end">
                        <Button variant="outline" onClick={() => startEdit(r)}>
                          {t.edit}
                        </Button>
                      </td>
                    </>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function NumInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <input
      type="number"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="numeric w-24 rounded-control border border-line px-2 py-1 text-end"
    />
  );
}
