import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { fieldErrors, resolveErrorMessage } from "@/lib/api/messages";
import { Button } from "@/components/ui/Button";
import { DataTable, type Column } from "@/components/ui/DataTable";
import { Field } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { addBlock, listBlocks, removeBlock, type BlockEntry, type BlockKind } from "./api";

/**
 * Блок-лист антиспама (F-10.2, MANAGER/ADMIN): IP и телефоны, с которых заявки отсекаются молча —
 * отправитель видит обычное «спасибо», в систему ничего не попадает. Телефон вводится как угодно,
 * сервер хранит его в виде +972…, поэтому один номер в разной записи — дубликат.
 */
export function BlockListPage() {
  const { user } = useAuth();
  const { dict, locale } = useI18n();
  const t = dict.spam;
  const qc = useQueryClient();

  const [kind, setKind] = useState<BlockKind>("PHONE");
  const [value, setValue] = useState("");
  const [reason, setReason] = useState("");
  const [toRemove, setToRemove] = useState<BlockEntry | null>(null);

  const allowed = !!user && canAccessPath(user.role, "/spam");
  const q = useQuery({ queryKey: ["admin-block-list"], queryFn: listBlocks, enabled: allowed });

  const addM = useMutation({
    mutationFn: () => addBlock({ kind, value: value.trim(), reason: reason.trim() || undefined }),
    onSuccess: () => {
      setValue("");
      setReason("");
      qc.invalidateQueries({ queryKey: ["admin-block-list"] });
    },
  });
  const removeM = useMutation({
    mutationFn: (id: number) => removeBlock(id),
    onSuccess: () => {
      setToRemove(null);
      qc.invalidateQueries({ queryKey: ["admin-block-list"] });
    },
  });

  if (!allowed) {
    return (
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1>
        <p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p>
      </div>
    );
  }

  const dt = (iso?: string) => (iso ? new Date(iso).toLocaleDateString(locale === "he" ? "he-IL" : "ru-RU") : "—");
  const kindLabel = (k?: string) => (k?.toUpperCase() === "IP" ? t.kindIp : t.kindPhone);
  const errs = fieldErrors(addM.error);

  const columns: Column<BlockEntry>[] = [
    {
      key: "kind",
      header: t.colKind,
      render: (r) => (
        <span className={`rounded-pill px-2 py-0.5 text-xs ${r.kind?.toUpperCase() === "IP" ? "bg-info-bg text-info-fg" : "bg-paper-warm text-ink-soft"}`}>
          {kindLabel(r.kind)}
        </span>
      ),
    },
    { key: "value", header: t.colValue, render: (r) => <bdi dir="ltr" className="numeric font-medium text-navy">{r.value}</bdi> },
    { key: "reason", header: t.colReason, render: (r) => (r.reason ? <span className="text-ink">{r.reason}</span> : <span className="text-ink-faint">—</span>) },
    { key: "created_at", header: t.colAdded, render: (r) => <span className="numeric">{dt(r.created_at)}</span> },
    {
      key: "actions",
      header: "",
      render: (r) => (
        <button type="button" onClick={() => setToRemove(r)} className="text-xs text-crit-fg hover:underline">
          {t.remove}
        </button>
      ),
    },
  ];

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-h2 font-bold text-navy">{t.title}</h1>
        <p className="mt-1 text-sm text-ink-soft">{t.subtitle}</p>
      </div>

      <form
        className="flex flex-wrap items-end gap-3 rounded-card border border-line bg-surface p-4 shadow-card"
        onSubmit={(e) => {
          e.preventDefault();
          if (value.trim()) addM.mutate();
        }}
      >
        <label className="flex flex-col gap-1 text-sm font-medium text-ink-soft">
          {t.colKind}
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value as BlockKind);
              addM.reset();
            }}
            className="rounded-control border border-line-strong px-3 py-2 text-ink"
          >
            <option value="PHONE">{t.kindPhone}</option>
            <option value="IP">{t.kindIp}</option>
          </select>
        </label>
        <div className="min-w-[12rem]">
          <Field
            label={t.colValue}
            value={value}
            dir="ltr"
            placeholder={kind === "IP" ? "203.0.113.7" : "050-123-4567"}
            onChange={(e) => setValue(e.target.value)}
            error={errs.value}
          />
        </div>
        <div className="min-w-[14rem] flex-1">
          <Field label={t.colReason} value={reason} maxLength={255} onChange={(e) => setReason(e.target.value)} />
        </div>
        <Button type="submit" variant="primary" disabled={!value.trim() || addM.isPending}>
          {t.add}
        </Button>
        {addM.isError && !errs.value && (
          <p className="basis-full text-sm text-crit-fg">{resolveErrorMessage(dict.errors, addM.error).text}</p>
        )}
      </form>

      {q.isError ? (
        <p className="rounded-card border border-line bg-surface p-6 text-crit-fg shadow-card">{t.loadError}</p>
      ) : (
        <DataTable
          columns={columns}
          rows={q.data ?? []}
          getRowKey={(r) => r.id!}
          labels={{
            page: dict.table.page,
            of: dict.table.of,
            rowsTotal: dict.table.rowsTotal,
            prev: dict.table.prev,
            next: dict.table.next,
            empty: q.isLoading ? dict.common.loading : t.empty,
          }}
        />
      )}

      <Modal
        open={toRemove != null}
        onClose={() => setToRemove(null)}
        title={t.removeTitle}
        footer={
          <>
            <Button variant="muted" onClick={() => setToRemove(null)}>{dict.common.cancel}</Button>
            <Button variant="primary" disabled={removeM.isPending} onClick={() => toRemove?.id != null && removeM.mutate(toRemove.id)}>
              {t.remove}
            </Button>
          </>
        }
      >
        <p>
          {t.removeBody} <bdi dir="ltr" className="numeric font-medium">{toRemove?.value}</bdi>
        </p>
        {removeM.isError && <p className="mt-2 text-sm text-crit-fg">{resolveErrorMessage(dict.errors, removeM.error).text}</p>}
      </Modal>
    </div>
  );
}
