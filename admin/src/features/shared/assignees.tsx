import { useI18n } from "@/lib/i18n/I18nProvider";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useAssignees } from "./useAssignees";

/** Имя исполнителя в таблице/карточке. */
export function AssigneeName({ id, emptyLabel }: { id?: number | null; emptyLabel: string }) {
  const { nameOf } = useAssignees();
  const name = nameOf(id);
  return name ? <span className="text-navy">{name}</span> : <span className="text-ink-soft">{emptyLabel}</span>;
}

/**
 * Выбор исполнителя: «не назначен» + персонал. Текущий пользователь помечен «(вы)». Если назначенный
 * сотрудник выпал из списка (заблокирован), он всё равно виден как отдельный вариант.
 */
export function AssigneeSelect({
  value,
  disabled,
  onChange,
  id,
}: {
  value?: number | null;
  disabled?: boolean;
  onChange: (managerId: number | null) => void;
  id?: string;
}) {
  const { dict } = useI18n();
  const { user } = useAuth();
  const { list, isError } = useAssignees();
  const a = dict.assignees;
  const missing = value != null && !list.some((p) => p.id === value);

  return (
    <div className="flex flex-col gap-1">
      <select
        id={id}
        value={value ?? ""}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
        className="min-w-[14rem] rounded-control border border-line px-3 py-2 text-sm text-ink"
      >
        <option value="">{a.none}</option>
        {missing && <option value={value!}>#{value}</option>}
        {list.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
            {p.id === user?.id ? ` ${a.me}` : ""} · {dict.roles[(p.role ?? "").toUpperCase() as keyof typeof dict.roles] ?? p.role}
          </option>
        ))}
      </select>
      {isError && <span className="text-xs text-red-700">{a.loadError}</span>}
    </div>
  );
}
