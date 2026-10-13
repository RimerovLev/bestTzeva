import { useI18n } from "@/lib/i18n/I18nProvider";
import { normStatus, type ServiceStatus } from "./api";

const TONE: Record<ServiceStatus, string> = {
  NEW: "bg-brass/15 text-brass",
  CONTACTED: "bg-navy/10 text-navy",
  MEASUREMENT_SCHEDULED: "bg-sky-100 text-sky-700",
  MEASURED: "bg-sky-100 text-sky-800",
  QUOTED: "bg-amber-100 text-amber-700",
  ACCEPTED: "bg-emerald-100 text-emerald-700",
  IN_PROGRESS: "bg-terracotta/15 text-terracotta-ink",
  COMPLETED: "bg-emerald-200 text-emerald-800",
  REJECTED: "bg-red-100 text-red-700",
  LOST: "bg-line text-ink-soft",
};

export function StatusBadge({ status }: { status: string | undefined | null }) {
  const { dict } = useI18n();
  const s = normStatus(status);
  return (
    <span className={`inline-block whitespace-nowrap rounded-pill px-2.5 py-0.5 text-xs font-medium ${TONE[s]}`}>
      {dict.enums.service_status[s]}
    </span>
  );
}
