import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ru from "@/dictionaries/ru.json";
import { AssigneeName, AssigneeSelect } from "./assignees";

const list = [
  { id: 7, name: "Dana Levi", role: "manager" as const },
  { id: 9, name: "Avi Cohen", role: "support" as const },
];

vi.mock("./useAssignees", () => ({
  useAssignees: () => ({
    list,
    isError: false,
    nameOf: (id?: number | null) => (id == null ? null : list.find((a) => a.id === id)?.name ?? `#${id}`),
  }),
}));
vi.mock("@/lib/i18n/I18nProvider", () => ({ useI18n: () => ({ dict: ru, locale: "ru" }) }));
vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: () => ({ user: { id: 9 } }) }));

describe("AssigneeSelect", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists staff with role, marks current user, and sends the picked id", () => {
    const onChange = vi.fn();
    render(<AssigneeSelect value={null} onChange={onChange} />);
    expect(screen.getByRole("option", { name: ru.assignees.none })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: `Dana Levi · ${ru.roles.MANAGER}` })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: `Avi Cohen ${ru.assignees.me} · ${ru.roles.SUPPORT}` })).toBeInTheDocument();

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "7" } });
    expect(onChange).toHaveBeenCalledWith(7);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "" } });
    expect(onChange).toHaveBeenLastCalledWith(null);
  });

  it("keeps an assignee who dropped out of the list visible", () => {
    render(<AssigneeSelect value={42} onChange={() => {}} />);
    expect(screen.getByRole("combobox")).toHaveValue("42");
    expect(screen.getByRole("option", { name: "#42" })).toBeInTheDocument();
  });
});

describe("AssigneeName", () => {
  it("shows the name, an id fallback, or the empty label", () => {
    const { rerender } = render(<AssigneeName id={7} emptyLabel="—" />);
    expect(screen.getByText("Dana Levi")).toBeInTheDocument();
    rerender(<AssigneeName id={42} emptyLabel="—" />);
    expect(screen.getByText("#42")).toBeInTheDocument();
    rerender(<AssigneeName id={null} emptyLabel="—" />);
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});
