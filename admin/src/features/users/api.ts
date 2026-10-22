import { api, unwrap } from "@/lib/api/http";
import type { components } from "@/lib/api/schema.admin";

/** Роль/статус на проводе приходят в нижнем регистре; в UI приводим к верхнему (как в session.ts). */
export type StaffRole = "SUPPORT" | "WAREHOUSE" | "MANAGER" | "ADMIN";
export type UserStatus = "ACTIVE" | "PENDING" | "BLOCKED";

export interface Employee {
  id: number;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  role: StaffRole;
  status: UserStatus;
}

type EmployeeView = components["schemas"]["EmployeeView"];

function normalize(v: EmployeeView): Employee {
  return {
    id: v.id ?? 0,
    email: v.email ?? null,
    firstName: v.first_name ?? null,
    lastName: v.last_name ?? null,
    role: String(v.role ?? "SUPPORT").toUpperCase() as StaffRole,
    status: String(v.status ?? "ACTIVE").toUpperCase() as UserStatus,
  };
}

export async function listEmployees(): Promise<Employee[]> {
  const rows = unwrap(await api.GET("/api/v1/admin/users")) as EmployeeView[];
  return rows.map(normalize);
}

export interface CreateEmployeeInput {
  email: string;
  firstName: string;
  lastName: string;
  role: StaffRole;
}

export async function createEmployee(input: CreateEmployeeInput): Promise<number> {
  const res = unwrap(
    await api.POST("/api/v1/admin/users", {
      body: {
        email: input.email,
        first_name: input.firstName,
        last_name: input.lastName,
        role: input.role,
      },
    }),
  ) as { id?: number };
  return Number(res.id ?? 0);
}

export async function blockEmployee(id: number): Promise<void> {
  unwrap(await api.POST("/api/v1/admin/users/{id}/block", { params: { path: { id } } }));
}

export async function unblockEmployee(id: number): Promise<void> {
  unwrap(await api.POST("/api/v1/admin/users/{id}/unblock", { params: { path: { id } } }));
}
