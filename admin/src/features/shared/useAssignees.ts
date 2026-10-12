import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

export type Assignee = components["schemas"]["AssigneeView"];

/** Активный персонал заявок (SUPPORT/MANAGER/ADMIN): id, имя, роль. Список меняется редко. */
export function useAssignees() {
  const q = useQuery({
    queryKey: ["admin-assignees"],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/users/assignees")),
    staleTime: 5 * 60_000,
  });
  const list = q.data ?? [];
  const byId = new Map(list.map((a) => [a.id, a]));
  return {
    list,
    isError: q.isError,
    /** Имя по id; неизвестный (уволен, заблокирован) — «#id», чтобы назначение не терялось молча. */
    nameOf: (id?: number | null) => (id == null ? null : byId.get(id)?.name ?? `#${id}`),
  };
}
