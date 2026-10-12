import { useI18n } from "@/lib/i18n/I18nProvider";
import { normStatus, type RequestStatus } from "./api";

const TONE: Record<RequestStatus, string> = {
  NEW: "bg-brass/15 text-brass",
  CONTACTED: "bg-navy/10 text-navy",
  RESERVED: "bg-terracotta/15 text-terracotta",
  COMPLETED: "bg-emerald-100 text-emerald-700",
  CANCELLED: "bg-line text-ink-soft",
  LOST: "bg-red-100 text-red-700",
};

export function StatusBadge({ status }: { status: string | undefined | null }) {
  const { dict } = useI18n();
  const s = normStatus(status);
  return (
    <span className={`inline-block whitespace-nowrap rounded-pill px-2.5 py-0.5 text-xs font-medium ${TONE[s]}`}>
      {dict.enums.request_status[s]}
    </span>
  );
}
