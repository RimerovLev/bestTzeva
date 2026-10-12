import { describe, expect, it, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { DataTable, type Column, type TableLabels } from "./DataTable";

interface Row {
  id: number;
  name: string;
  qty: number;
}

const labels: TableLabels = {
  page: "page",
  of: "of",
  rowsTotal: "total",
  prev: "prev",
  next: "next",
  empty: "no data",
};

const columns: Column<Row>[] = [
  { key: "name", header: "Name", render: (r) => r.name, sortable: true },
  { key: "qty", header: "Qty", render: (r) => r.qty, numeric: true },
];

describe("DataTable", () => {
  it("renders rows", () => {
    render(
      <DataTable
        columns={columns}
        rows={[
          { id: 1, name: "Alpha", qty: 3 },
          { id: 2, name: "Beta", qty: 7 },
        ]}
        getRowKey={(r) => r.id}
        labels={labels}
      />,
    );
    expect(screen.getByText("Alpha")).toBeInTheDocument();
    expect(screen.getByText("Beta")).toBeInTheDocument();
  });

  it("toggles sort direction on a sortable header", () => {
    const onSortChange = vi.fn();
    const { rerender } = render(
      <DataTable
        columns={columns}
        rows={[{ id: 1, name: "Alpha", qty: 3 }]}
        getRowKey={(r) => r.id}
        labels={labels}
        onSortChange={onSortChange}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Name/ }));
    expect(onSortChange).toHaveBeenLastCalledWith({ field: "name", dir: "asc" });

    rerender(
      <DataTable
        columns={columns}
        rows={[{ id: 1, name: "Alpha", qty: 3 }]}
        getRowKey={(r) => r.id}
        labels={labels}
        sort={{ field: "name", dir: "asc" }}
        onSortChange={onSortChange}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Name/ }));
    expect(onSortChange).toHaveBeenLastCalledWith({ field: "name", dir: "desc" });
  });

  it("shows the empty state when there are no rows", () => {
    render(<DataTable columns={columns} rows={[]} getRowKey={(r) => r.id} labels={labels} />);
    expect(screen.getByText("no data")).toBeInTheDocument();
  });
});
