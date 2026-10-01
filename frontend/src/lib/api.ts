import type { Connection, ConnectionInput, Execution } from "../types/api";
export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch("/api" + path, {
    ...options,
    headers: { "Content-Type": "application/json", ...options.headers },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || "Não foi possível concluir a operação.");
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
const json = (method: string, value: unknown) => ({
  method,
  body: JSON.stringify(value),
});
export const api = {
  connections: (signal?: AbortSignal) =>
    request<Connection[]>("/connections", { signal }),
  save: (value: ConnectionInput, id?: number) =>
    request<Connection>(
      "/connections" + (id ? "/" + id : ""),
      json(id ? "PUT" : "POST", value),
    ),
  remove: (id: number) =>
    request<void>("/connections/" + id, { method: "DELETE" }),
  test: (id: number) =>
    request<{ success: boolean; message: string; responseTimeMs: number }>(
      `/connections/${id}/test`,
      { method: "POST" },
    ),
  databases: (id: number, signal?: AbortSignal) =>
    request<{ databases: string[] }>(`/connections/${id}/databases`, {
      signal,
    }),
  validate: (sql: string) =>
    request<{ valid: boolean; message: string; code: string }>(
      "/query/validate",
      json("POST", { sql }),
    ),
  queryConfig: (signal?: AbortSignal) => request<{ maxResultRows: number }>("/query/config", { signal }),
  execute: (connectionId: number, databases: string[], sql: string, rowLimit?: number) =>
    request<Execution>(
      "/query/execute",
      json("POST", { connectionId, databases, sql, rowLimit }),
    ),
};
export async function download(format: "csv" | "xlsx", execution: Execution) {
  const res = await fetch("/api/query/export/" + format, {
    ...json("POST", { execution }),
    headers: { "Content-Type": "application/json" },
  });
  if (!res.ok) {
    const error = await res.json().catch(() => ({}));
    throw new Error(error.detail || "Falha ao exportar.");
  }
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = `queryhub-${execution.executionId}.${format}`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const message = (error: unknown) =>
  error instanceof Error ? error.message : "Ocorreu um erro inesperado.";
