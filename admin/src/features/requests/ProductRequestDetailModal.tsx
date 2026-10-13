import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { resolveErrorMessage } from "@/lib/api/messages";
import { StatusBadge } from "./StatusBadge";
import { AssigneeSelect } from "@/features/shared/assignees";
import {
  getProductRequest,
  transitionProductRequest,
  assignProductRequest,
  addProductRequestComment,
  ALLOWED_TRANSITIONS,
  normStatus,
  type ProductRequestDetail,
  type ProductRequestItem,
  type RequestStatus,
} from "./api";

/** Только цифры — для ссылки wa.me (WhatsApp — основной канал в Израиле). */
function waDigits(phone: string | undefined | null): string {
  return (phone ?? "").replace(/\D/g, "");
}

export function ProductRequestDetailModal({
  id,
  onClose,
  onChanged,
}: {
  id: number;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { dict, locale } = useI18n();
  const t = dict.productRequests;
  const { user } = useAuth();
  const qc = useQueryClient();

  const query = useQuery({ queryKey: ["product-request", id], queryFn: () => getProductRequest(id) });
  const detail = query.data;

  const [comment, setComment] = useState("");
  const [newComment, setNewComment] = useState("");
  const [reserveConfirm, setReserveConfirm] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [reserveFailed, setReserveFailed] = useState(false);

  function afterMutate(updated: ProductRequestDetail) {
    qc.setQueryData(["product-request", id], updated);
    onChanged();
    setComment("");
    setActionError(null);
  }

  const transitionM = useMutation({
    mutationFn: ({ to, c }: { to: RequestStatus; c?: string }) => transitionProductRequest(id, to, c),
    onSuccess: (d) => {
      afterMutate(d);
      setReserveConfirm(false);
      setReserveFailed(false);
      // Резерв, продажа и отмена двигают остатки — экран склада не должен показывать старые числа.
      qc.invalidateQueries({ queryKey: ["inventory"] });
      qc.invalidateQueries({ queryKey: ["low-stock"] });
    },
    onError: (e, vars) => {
      setActionError(resolveErrorMessage(dict.errors, e).text);
      setReserveFailed(vars.to === "RESERVED");
    },
  });
  const assignM = useMutation({
    mutationFn: (managerId: number | null) => assignProductRequest(id, managerId),
    onSuccess: afterMutate,
    onError: (e) => setActionError(resolveErrorMessage(dict.errors, e).text),
  });
  const commentM = useMutation({
    mutationFn: (text: string) => addProductRequestComment(id, text),
    onSuccess: () => {
      // POST /comments отвечает без тела — перечитываем деталь, чтобы показать новую ленту.
      qc.invalidateQueries({ queryKey: ["product-request", id] });
      onChanged();
      setNewComment("");
      setActionError(null);
    },
    onError: (e) => setActionError(resolveErrorMessage(dict.errors, e).text),
  });

  const dateFmt = (iso?: string) => (iso ? new Date(iso).toLocaleString(locale === "he" ? "he-IL" : "ru-RU") : "—");
  const money = (n?: number | null) => (n == null ? "—" : `₪${n.toLocaleString(locale === "he" ? "he-IL" : "ru-RU")}`);

  const status = detail ? normStatus(detail.status) : "NEW";
  const transitions = ALLOWED_TRANSITIONS[status];

  function onTransitionClick(to: RequestStatus) {
    setActionError(null);
    if (to === "RESERVED") setReserveConfirm(true);
    else transitionM.mutate({ to, c: comment.trim() || undefined });
  }

  const busy = transitionM.isPending || assignM.isPending || commentM.isPending;

  return (
    <Modal open onClose={onClose} title={detail ? t.detailTitle.replace("{number}", detail.request_number ?? "") : "…"}>
      {query.isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : query.isError || !detail ? (
        <p className="text-red-700">{t.loadError}</p>
      ) : (
        <div className="space-y-6">
          <div className="flex items-center gap-3">
            <StatusBadge status={detail.status} />
            <span className="text-sm text-ink-soft">{dateFmt(detail.created_at)}</span>
          </div>

          {/* Контакты */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.contacts}</h3>
            <div className="rounded-control border border-line p-3 text-sm">
              <div className="font-medium text-navy">{detail.customer_name}</div>
              <div className="mt-2 flex flex-wrap gap-2">
                {detail.customer_phone && (
                  <>
                    <a
                      href={`tel:${detail.customer_phone}`}
                      className="rounded-control bg-navy px-3 py-1.5 text-xs font-medium text-surface hover:bg-navy-deep"
                    >
                      {t.call} {detail.customer_phone}
                    </a>
                    <a
                      href={`https://wa.me/${waDigits(detail.customer_phone)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="rounded-control bg-emerald-600 px-3 py-1.5 text-xs font-medium text-surface hover:bg-emerald-700"
                    >
                      {t.whatsapp}
                    </a>
                  </>
                )}
                {detail.customer_email && (
                  <a href={`mailto:${detail.customer_email}`} className="rounded-control border border-line px-3 py-1.5 text-xs text-ink">
                    {detail.customer_email}
                  </a>
                )}
              </div>
              {detail.preferred_contact_time && (
                <div className="mt-2 text-ink-soft">
                  {t.preferredTime}: <span className="text-ink">{detail.preferred_contact_time}</span>
                </div>
              )}
              {detail.comment && (
                <div className="mt-2 text-ink-soft">
                  {t.customerComment}: <span className="text-ink">{detail.comment}</span>
                </div>
              )}
            </div>
          </section>

          {/* Позиции: снимок vs текущее состояние каталога */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.itemsTitle}</h3>
            <div className="space-y-2">
              {(detail.items ?? []).map((it, i) => (
                <ItemRow key={i} item={it} labels={t} money={money} />
              ))}
            </div>
            {detail.indicative_total != null && (
              <div className="mt-2 text-end text-sm text-ink-soft">
                {t.indicativeTotal}: <span className="font-semibold text-navy">{money(detail.indicative_total)}</span>
              </div>
            )}
          </section>

          {actionError && (
            <div className="rounded-control border border-red-200 bg-red-50 p-3 text-sm text-red-700">
              <div>{actionError}</div>
              {reserveFailed && <div className="mt-1 text-xs text-red-600">{t.reserveFailNote}</div>}
            </div>
          )}

          {/* Смена статуса — только разрешённые переходы */}
          {transitions.length > 0 && !reserveConfirm && (
            <section>
              <h3 className="mb-2 text-sm font-semibold text-navy">{t.changeStatus}</h3>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                placeholder={t.statusCommentPlaceholder}
                rows={2}
                className="mb-2 w-full rounded-control border border-line px-3 py-2 text-sm"
              />
              <div className="flex flex-wrap gap-2">
                {transitions.map((to) => (
                  <Button
                    key={to}
                    variant={to === "RESERVED" ? "brass" : "outline"}
                    disabled={busy}
                    onClick={() => onTransitionClick(to)}
                  >
                    {dict.enums.request_status[to]}
                  </Button>
                ))}
              </div>
            </section>
          )}

          {/* Подтверждение резерва (склад, срок 3 суток) */}
          {reserveConfirm && (
            <section className="rounded-control border border-terracotta/40 bg-terracotta/5 p-3">
              <h3 className="mb-1 text-sm font-semibold text-terracotta-ink">{t.reserveTitle}</h3>
              <p className="mb-3 text-sm text-ink">{t.reserveBody}</p>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="brass"
                  disabled={busy}
                  onClick={() => transitionM.mutate({ to: "RESERVED", c: comment.trim() || undefined })}
                >
                  {t.reserveConfirm}
                </Button>
                <Button variant="muted" disabled={busy} onClick={() => setReserveConfirm(false)}>
                  {dict.common.cancel}
                </Button>
              </div>
            </section>
          )}

          {/* Назначение менеджера */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.assignment}</h3>
            <div className="flex flex-wrap items-start gap-2 text-sm">
              <AssigneeSelect value={detail.assigned_manager_id} disabled={busy} onChange={(m) => assignM.mutate(m)} />
              {user && detail.assigned_manager_id !== user.id && (
                <Button variant="outline" disabled={busy} onClick={() => assignM.mutate(user.id)}>
                  {t.assignToMe}
                </Button>
              )}
            </div>
          </section>

          {/* История статусов */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.history}</h3>
            <ul className="space-y-1 text-sm">
              {(detail.history ?? []).map((h, i) => (
                <li key={i} className="flex flex-wrap gap-2 text-ink-soft">
                  <span className="text-ink">
                    {h.from_status ? `${labelStatus(dict, h.from_status)} → ` : ""}
                    {labelStatus(dict, h.to_status)}
                  </span>
                  <span>{dateFmt(h.created_at)}</span>
                  {h.comment && <span className="italic">«{h.comment}»</span>}
                </li>
              ))}
            </ul>
          </section>

          {/* Внутренние комментарии */}
          <section>
            <h3 className="mb-2 text-sm font-semibold text-navy">{t.comments}</h3>
            {(detail.comments ?? []).length === 0 ? (
              <p className="text-sm text-ink-soft">{t.noComments}</p>
            ) : (
              <ul className="mb-3 space-y-2 text-sm">
                {detail.comments!.map((c, i) => (
                  <li key={i} className="rounded-control border border-line p-2">
                    <div className="text-ink">{c.text}</div>
                    <div className="mt-1 text-xs text-ink-soft">{dateFmt(c.created_at)}</div>
                  </li>
                ))}
              </ul>
            )}
            <textarea
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              placeholder={t.commentPlaceholder}
              rows={2}
              className="mb-2 w-full rounded-control border border-line px-3 py-2 text-sm"
            />
            <Button
              variant="outline"
              disabled={busy || !newComment.trim()}
              onClick={() => commentM.mutate(newComment.trim())}
            >
              {t.send}
            </Button>
          </section>
        </div>
      )}
    </Modal>
  );
}

function labelStatus(dict: ReturnType<typeof useI18n>["dict"], raw?: string): string {
  const key = normStatus(raw);
  return dict.enums.request_status[key] ?? raw ?? "";
}

function ItemRow({
  item,
  labels,
  money,
}: {
  item: ProductRequestItem;
  labels: ReturnType<typeof useI18n>["dict"]["productRequests"];
  money: (n?: number | null) => string;
}) {
  const priceChanged =
    item.current_price != null && item.snapshot_price != null && item.current_price !== item.snapshot_price;
  const unavailable = (item.current_available ?? 0) <= 0;
  return (
    <div className="rounded-control border border-line p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium text-navy">{item.snapshot_name}</span>
        <span className="numeric text-ink-soft">{item.snapshot_sku}</span>
      </div>
      <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-ink-soft">
        <span>
          {labels.qty}: <span className="text-ink">{item.qty}</span>
        </span>
        {item.snapshot_color && <span>{item.snapshot_color}</span>}
        {item.snapshot_volume != null && <span>{item.snapshot_volume} л</span>}
      </div>
      <div className="mt-2 grid grid-cols-2 gap-3">
        <div>
          <div className="text-xs text-ink-soft">{labels.itemSnapshot}</div>
          <div className="text-ink">{money(item.snapshot_price)}</div>
        </div>
        <div>
          <div className="text-xs text-ink-soft">{labels.itemCurrent}</div>
          <div className={priceChanged ? "font-medium text-terracotta-ink" : "text-ink"}>
            {money(item.current_price)}
            {priceChanged && <span className="ms-1 text-xs">({labels.priceChanged})</span>}
          </div>
          <div className="mt-0.5 text-xs">
            {item.current_active === false ? (
              <span className="text-red-700">{labels.inactiveNow}</span>
            ) : unavailable ? (
              <span className="text-red-700">{labels.unavailableNow}</span>
            ) : (
              <span className="text-emerald-700">{labels.availabilityNow.replace("{n}", String(item.current_available))}</span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
