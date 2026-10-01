import { useMemo, useState } from "react";
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
} from "@tanstack/react-table";
import {
  Copy,
  ChevronLeft,
  ChevronRight,
  ArrowUpDown,
  Search,
} from "lucide-react";
import type { TableData } from "../types/api";
export const display = (value: unknown) =>
  value === null || value === undefined
    ? "NULL"
    : typeof value === "object"
      ? JSON.stringify(value)
      : String(value);
export function DynamicTable({ columns, rows }: TableData) {
  const [search, setSearch] = useState("");
  const [notice, setNotice] = useState("");
  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setNotice("Copiado.");
    } catch {
      setNotice("O navegador não permitiu copiar.");
    }
  };
  const definitions = useMemo<ColumnDef<Record<string, unknown>>[]>(
    () =>
      columns.map((c) => ({
        id: c.key,
        accessorFn: (row) => row[c.key],
        filterFn: (row, columnId, filter) =>
          display(row.getValue(columnId))
            .toLocaleLowerCase()
            .includes(String(filter).toLocaleLowerCase()),
        header: c.label,
        size: c.key === "__database" ? 250 : 170,
        cell: (info) =>
          info.getValue() == null ? (
            <span className="null">NULL</span>
          ) : (
            <span>{display(info.getValue())}</span>
          ),
      })),
    [columns],
  );
  const table = useReactTable({
    columns: definitions,
    data: rows,
    state: { globalFilter: search },
    onGlobalFilterChange: setSearch,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize: 25 } },
    columnResizeMode: "onChange",
  });
  return (
    <div className="data-table">
      <div className="table-tools">
        <label className="search">
          <Search size={16} />
          <input
            placeholder="Pesquisar nos resultados..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              table.setPageIndex(0);
            }}
          />
        </label>
        <span className="muted" role="status">
          {notice || "Clique em um valor para copiar"}
        </span>
      </div>
      <div className="table-scroll">
        <table style={{ minWidth: table.getTotalSize() + 50 }}>
          <thead>
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => (
                  <th key={header.id} style={{ width: header.getSize() }}>
                    <button
                      className="sort"
                      onClick={header.column.getToggleSortingHandler()}
                    >
                      {flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}
                      <ArrowUpDown size={12} />
                    </button>
                    <input
                      aria-label={`Filtrar ${header.column.columnDef.header}`}
                      placeholder="Filtrar..."
                      value={String(header.column.getFilterValue() ?? "")}
                      onChange={(e) =>
                        header.column.setFilterValue(e.target.value)
                      }
                    />
                    <div
                      onMouseDown={header.getResizeHandler()}
                      onTouchStart={header.getResizeHandler()}
                      className="resizer"
                    />
                  </th>
                ))}
                <th aria-label="Copiar linha" />
              </tr>
            ))}
          </thead>
          <tbody>
            {table.getRowModel().rows.map((row) => (
              <tr key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>
                    <button
                      className="cell-value"
                      title={display(cell.getValue())}
                      onClick={() => copy(display(cell.getValue()))}
                    >
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </button>
                  </td>
                ))}
                <td>
                  <button
                    className="icon-button"
                    title="Copiar linha"
                    aria-label="Copiar linha"
                    onClick={() =>
                      copy(
                        columns
                          .map((c) => display(row.original[c.key]))
                          .join("\t"),
                      )
                    }
                  >
                    <Copy size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {table.getRowModel().rows.length === 0 && (
          <div className="table-empty">Nenhum registro encontrado.</div>
        )}
      </div>
      <div className="table-footer">
        <span>
          {table.getFilteredRowModel().rows.length.toLocaleString("pt-BR")}{" "}
          registros · página {table.getState().pagination.pageIndex + 1} de{" "}
          {Math.max(1, table.getPageCount())}
        </span>
        <div className="actions">
          <select
            aria-label="Registros por página"
            value={table.getState().pagination.pageSize}
            onChange={(e) => table.setPageSize(Number(e.target.value))}
          >
            {[25, 50, 100].map((n) => (
              <option key={n} value={n}>
                {n} por página
              </option>
            ))}
          </select>
          <button
            className="icon-button"
            aria-label="Página anterior"
            disabled={!table.getCanPreviousPage()}
            onClick={() => table.previousPage()}
          >
            <ChevronLeft size={17} />
          </button>
          <button
            className="icon-button"
            aria-label="Próxima página"
            disabled={!table.getCanNextPage()}
            onClick={() => table.nextPage()}
          >
            <ChevronRight size={17} />
          </button>
        </div>
      </div>
    </div>
  );
}
