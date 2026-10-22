import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useI18n } from "@/lib/i18n/I18nProvider";
import { canAccessPath } from "@/lib/auth/roles";
import { resolveErrorMessage } from "@/lib/api/messages";
import { Button } from "@/components/ui/Button";
import { Field } from "@/components/ui/Field";
import {
  blockEmployee,
  createEmployee,
  listEmployees,
  unblockEmployee,
  type Employee,
  type StaffRole,
} from "./api";

const ROLES: StaffRole[] = ["SUPPORT", "WAREHOUSE", "MANAGER", "ADMIN"];

/**
 * Сотрудники (только ADMIN): список персонала, приглашение (создание в статусе PENDING + письмо со
 * ссылкой на установку пароля) и блокировка/разблокировка. Пароли здесь не задаются.
 */
export function UsersPage() {
  const { user } = useAuth();
  const { dict } = useI18n();
  const t = dict.users;
  const qc = useQueryClient();

  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [role, setRole] = useState<StaffRole>("SUPPORT");
  const [invited, setInvited] = useState<string | null>(null);

  const list = useQuery({ queryKey: ["employees"], queryFn: listEmployees });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["employees"] });

  const createM = useMutation({
    mutationFn: createEmployee,
    onSuccess: (_id, input) => {
      setInvited(input.email);
      setEmail("");
      setFirstName("");
      setLastName("");
      setRole("SUPPORT");
      invalidate();
    },
  });
  const blockM = useMutation({ mutationFn: blockEmployee, onSuccess: invalidate });
  const unblockM = useMutation({ mutationFn: unblockEmployee, onSuccess: invalidate });

  if (!user || !canAccessPath(user.role, "/users")) {
    return (
      <div className="rounded-card border border-line bg-surface p-8 text-center shadow-card">
        <h1 className="text-h2 font-bold text-navy">{dict.common.noAccessTitle}</h1>
        <p className="mt-2 text-ink-soft">{dict.common.noAccessBody}</p>
      </div>
    );
  }

  function onInvite(e: FormEvent) {
    e.preventDefault();
    setInvited(null);
    if (email.trim() && firstName.trim() && lastName.trim()) {
      createM.mutate({ email: email.trim(), firstName: firstName.trim(), lastName: lastName.trim(), role });
    }
  }

  return (
    <div className="max-w-4xl space-y-5">
      <div>
        <h1 className="text-h2 font-bold text-navy">{t.title}</h1>
        <p className="mt-1 text-sm text-ink-soft">{t.subtitle}</p>
      </div>

      <section className="space-y-4 rounded-card border border-line bg-surface p-5 shadow-card">
        <h2 className="text-h3 font-bold text-navy">{t.inviteTitle}</h2>
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={onInvite}>
          <Field label={t.email} type="email" dir="ltr" autoComplete="off" value={email}
            onChange={(e) => setEmail(e.target.value)} required />
          <div className="flex flex-col gap-1">
            <label htmlFor="emp-role" className="text-sm font-medium text-ink-soft">{t.role}</label>
            <select id="emp-role" value={role} onChange={(e) => setRole(e.target.value as StaffRole)}
              className="rounded-control border border-line-strong bg-surface px-3 py-2 text-body text-ink outline-none focus:border-brass">
              {ROLES.map((r) => <option key={r} value={r}>{t.roles[r]}</option>)}
            </select>
          </div>
          <Field label={t.firstName} value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
          <Field label={t.lastName} value={lastName} onChange={(e) => setLastName(e.target.value)} required />
          <div className="sm:col-span-2 flex items-center gap-3">
            <Button type="submit" variant="primary" disabled={createM.isPending}>
              {createM.isPending ? dict.common.loading : t.invite}
            </Button>
            {invited && <p role="status" className="text-sm text-ink-soft">{t.inviteDone} <bdi dir="ltr">{invited}</bdi></p>}
          </div>
        </form>
        {createM.isError && <p className="text-sm text-crit-fg">{resolveErrorMessage(dict.errors, createM.error).text}</p>}
      </section>

      <section className="space-y-3 rounded-card border border-line bg-surface p-5 shadow-card">
        <h2 className="text-h3 font-bold text-navy">{t.listTitle}</h2>
        {list.isLoading ? (
          <p className="text-sm text-ink-soft">{dict.common.loading}</p>
        ) : list.isError ? (
          <p className="text-sm text-crit-fg">{resolveErrorMessage(dict.errors, list.error).text}</p>
        ) : (list.data ?? []).length === 0 ? (
          <p className="text-sm text-ink-soft">{t.empty}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-start text-sm">
              <thead>
                <tr className="border-b border-line text-ink-soft">
                  <th className="py-2 text-start font-medium">{t.colName}</th>
                  <th className="py-2 text-start font-medium">{t.colEmail}</th>
                  <th className="py-2 text-start font-medium">{t.colRole}</th>
                  <th className="py-2 text-start font-medium">{t.colStatus}</th>
                  <th className="py-2 text-end font-medium">{t.colActions}</th>
                </tr>
              </thead>
              <tbody>
                {(list.data ?? []).map((e: Employee) => (
                  <tr key={e.id} className="border-b border-line/60">
                    <td className="py-2">{[e.firstName, e.lastName].filter(Boolean).join(" ") || "—"}</td>
                    <td className="py-2"><bdi dir="ltr">{e.email ?? "—"}</bdi></td>
                    <td className="py-2">{t.roles[e.role] ?? e.role}</td>
                    <td className="py-2">{t.statuses[e.status] ?? e.status}</td>
                    <td className="py-2 text-end">
                      {e.status === "BLOCKED" ? (
                        <Button variant="outline" disabled={unblockM.isPending} onClick={() => unblockM.mutate(e.id)}>
                          {t.unblock}
                        </Button>
                      ) : (
                        <Button variant="outline" disabled={blockM.isPending || e.id === user.id}
                          className="!border-crit-fg !text-crit-fg hover:!bg-crit-fg hover:!text-surface"
                          onClick={() => blockM.mutate(e.id)}>
                          {t.block}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {(blockM.isError || unblockM.isError) && (
          <p className="text-sm text-crit-fg">
            {resolveErrorMessage(dict.errors, blockM.error ?? unblockM.error).text}
          </p>
        )}
      </section>
    </div>
  );
}
