import type { ReactNode } from "react";

export type SortDir = "asc" | "desc";

export interface SortState {
  field: string;
  dir: SortDir;
}

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  sortable?: boolean;
  /** Числовая колонка — выравнивание «к концу строки» + tabular-nums (см. RTL-правила). */
  numeric?: boolean;
}

/** Метаданные страницы в формате PageResponse бэкенда. */
export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TableLabels {
  page: string;
  of: string;
  rowsTotal: string;
  prev: string;
  next: string;
  empty: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  getRowKey: (row: T) => string | number;
  labels: TableLabels;
  meta?: PageMeta;
  sort?: SortState;
  onSortChange?: (sort: SortState) => void;
  onPageChange?: (page: number) => void;
  /** Доп. класс на строку — например подсветка позиций ниже порога. */
  rowClassName?: (row: T) => string;
}

/**
 * Таблица с сортировкой и пагинацией под PageResponse. Порядок колонок в RTL зеркалит браузер
 * (это HTML-таблица в dir=rtl), числа выравниваются «к концу строки». Своя, а не из UI-кита.
 */
export function DataTable<T>({
  columns,
  rows,
  getRowKey,
  labels,
  meta,
  sort,
  onSortChange,
  onPageChange,
  rowClassName,
}: DataTableProps<T>) {
  function toggleSort(field: string) {
    if (!onSortChange) return;
    const dir: SortDir = sort?.field === field && sort.dir === "asc" ? "desc" : "asc";
    onSortChange({ field, dir });
  }

  return (
    <div className="overflow-hidden rounded-card border border-line bg-surface shadow-card">
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-line bg-paper-warm/60 text-ink-soft">
              {columns.map((col) => {
                const active = sort?.field === col.key;
                return (
                  <th
                    key={col.key}
                    scope="col"
                    className={`px-4 py-3 font-medium ${col.numeric ? "text-end" : "text-start"}`}
                  >
                    {col.sortable && onSortChange ? (
                      <button
                        type="button"
                        onClick={() => toggleSort(col.key)}
                        className="inline-flex items-center gap-1 hover:text-navy focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
                        aria-sort={active ? (sort!.dir === "asc" ? "ascending" : "descending") : "none"}
                      >
                        {col.header}
                        <span aria-hidden className={active ? "text-brass" : "text-line-strong"}>
                          {active ? (sort!.dir === "asc" ? "▲" : "▼") : "↕"}
                        </span>
                      </button>
                    ) : (
                      col.header
                    )}
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="px-4 py-12 text-center text-ink-faint">
                  {labels.empty}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={getRowKey(row)} className={`border-b border-line last:border-0 hover:bg-paper-warm/40 ${rowClassName?.(row) ?? ""}`}>
                  {columns.map((col) => (
                    <td key={col.key} className={`px-4 py-3 ${col.numeric ? "col-numeric" : "text-start"}`}>
                      {col.render(row)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {meta && (
        <div className="flex items-center justify-between gap-4 border-t border-line px-4 py-3 text-sm text-ink-soft">
          <span>
            {labels.rowsTotal}: <bdi className="numeric">{meta.totalElements}</bdi>
          </span>
          <div className="flex items-center gap-3">
            <span>
              {labels.page} <bdi className="numeric">{meta.page + 1}</bdi> {labels.of}{" "}
              <bdi className="numeric">{Math.max(1, meta.totalPages)}</bdi>
            </span>
            <div className="flex gap-1">
              <button
                type="button"
                onClick={() => onPageChange?.(meta.page - 1)}
                disabled={meta.page <= 0}
                className="rounded-control border border-line-strong px-3 py-1 disabled:opacity-40 focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
              >
                {labels.prev}
              </button>
              <button
                type="button"
                onClick={() => onPageChange?.(meta.page + 1)}
                disabled={meta.page >= meta.totalPages - 1}
                className="rounded-control border border-line-strong px-3 py-1 disabled:opacity-40 focus:outline-none focus-visible:ring-2 focus-visible:ring-terracotta"
              >
                {labels.next}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
