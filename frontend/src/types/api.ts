export interface Connection {
  id: number;
  version: number;
  name: string;
  description: string;
  host: string;
  port: number;
  initialDatabase: string;
  username: string;
  sslEnabled: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
export type ConnectionInput = Omit<
  Connection,
  "id" | "createdAt" | "updatedAt" | "version"
> & { password?: string; version?: number };
export interface Column {
  key: string;
  label: string;
  jdbcType: number;
  typeName: string;
  sourceTypes: string[];
}
export interface TableData {
  truncated?: boolean;
  columns: Column[];
  rows: Record<string, unknown>[];
}
export interface DatabaseResult extends TableData {
  rowLimit: number;
  database: string;
  status: "SUCCESS" | "ERROR";
  rowCount: number;
  executionTimeMs: number;
  truncated: boolean;
  truncationReason: string | null;
  error: { code: string; message: string } | null;
}
export interface Execution {
  executionId: string;
  connectionId: number;
  connectionName: string;
  executedAt: string;
  executionTimeMs: number;
  summary: {
    selectedDatabases: number;
    successCount: number;
    errorCount: number;
    rowCount: number;
  };
  results: DatabaseResult[];
  consolidated: TableData;
  warnings: string[];
}
