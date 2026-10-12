import { describe, expect, it } from "vitest";
import { canAccessPath, visibleSections, type Role, type SectionKey } from "./roles";

/** Матрица «роль → видимые разделы» (готово-критерий F-6.1). */
const EXPECTED: Record<Role, SectionKey[]> = {
  CUSTOMER: [],
  SUPPORT: ["dashboard", "productRequests", "serviceRequests"],
  WAREHOUSE: ["dashboard", "productRequests", "warehouse"],
  MANAGER: [
    "dashboard",
    "productRequests",
    "serviceRequests",
    "warehouse",
    "catalog",
    "categories",
    "colors",
    "reports",
  ],
  ADMIN: [
    "dashboard",
    "productRequests",
    "serviceRequests",
    "warehouse",
    "catalog",
    "categories",
    "colors",
    "reports",
    "audit",
    "users",
  ],
};

describe("visibleSections", () => {
  for (const role of Object.keys(EXPECTED) as Role[]) {
    it(`${role} sees exactly the expected sections`, () => {
      expect(visibleSections(role).map((s) => s.key)).toEqual(EXPECTED[role]);
    });
  }
});

describe("canAccessPath", () => {
  it("SUPPORT cannot open the warehouse", () => {
    expect(canAccessPath("SUPPORT", "/warehouse")).toBe(false);
  });
  it("WAREHOUSE cannot open the catalog", () => {
    expect(canAccessPath("WAREHOUSE", "/catalog")).toBe(false);
  });
  it("only ADMIN opens the audit log", () => {
    expect(canAccessPath("ADMIN", "/audit")).toBe(true);
    expect(canAccessPath("MANAGER", "/audit")).toBe(false);
  });
  it("CUSTOMER has no admin dashboard", () => {
    expect(canAccessPath("CUSTOMER", "/")).toBe(false);
  });
});
