import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { afterEach, test, expect } from "vitest";
import { ExecutionResults } from "./ExecutionResults";
import type { Execution, DatabaseResult } from "../../types/api";
afterEach(cleanup);
test("a database named all does not replace the consolidated tab", () => {
  const columns = [
    {
      key: "c1",
      label: "name",
      jdbcType: 12,
      typeName: "text",
      sourceTypes: ["text"],
    },
  ];
  const result = (database: string, value: string): DatabaseResult => ({
    database,
    status: "SUCCESS",
    columns,
    rows: [{ c1: value }],
    rowCount: 1,
    rowLimit: 500,
    executionTimeMs: 1,
    truncated: true,
    truncationReason: "ROW_LIMIT",
    error: null,
  });
  const execution: Execution = {
    executionId: "id",
    connectionId: 1,
    connectionName: "Test",
    executedAt: new Date().toISOString(),
    executionTimeMs: 1,
    summary: {
      selectedDatabases: 2,
      successCount: 2,
      errorCount: 0,
      rowCount: 2,
    },
    results: [result("all", "First"), result("other", "Second")],
    consolidated: { columns, rows: [{ c1: "First" }, { c1: "Second" }] },
    warnings: [],
  };
  render(<ExecutionResults execution={execution} />);
  expect(screen.getByText(/all: 1 registros \(limite configurado: 500\)/)).toBeInTheDocument();
  expect(screen.getByText("Second")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "all" }));
  expect(screen.queryByText("Second")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "Consolidado" }));
  expect(screen.getByText("Second")).toBeInTheDocument();
});
