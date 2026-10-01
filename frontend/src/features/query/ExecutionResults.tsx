import { useState } from "react";
import { FileDown, Table2, CheckCircle2, AlertCircle } from "lucide-react";
import type { Execution } from "../../types/api";
import { DynamicTable } from "../../components/DynamicTable";
import { download, message } from "../../lib/api";
export function ExecutionResults({
  execution,
}: {
  execution: Execution | null;
}) {
  const [tab, setTab] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [exporting, setExporting] = useState(false);
  async function exportFile(format: "csv" | "xlsx") {
    if (!execution) return;
    setExporting(true);
    setError("");
    try {
      await download(format, execution);
    } catch (e) {
      setError(message(e));
    } finally {
      setExporting(false);
    }
  }
  const result = execution?.results.find((r) => r.database === tab);
  const table = result ?? execution?.consolidated;
  return (
    <section className="panel results">
      <div className="section-heading">
        <h2>Resultados</h2>
        <div className="actions">
          <button
            disabled={!execution || exporting}
            onClick={() => exportFile("csv")}
          >
            <FileDown size={16} /> CSV
          </button>
          <button
            disabled={!execution || exporting}
            onClick={() => exportFile("xlsx")}
          >
            <Table2 size={16} /> Excel
          </button>
        </div>
      </div>
      {!execution ? (
        <div className="empty-result">
          <Table2 size={32} />
          <h3>Seus resultados aparecem aqui</h3>
          <p>Selecione os databases e execute uma consulta de leitura.</p>
        </div>
      ) : (
        <>
          <div className="execution-summary">
            <span>
              <strong>{execution.summary.selectedDatabases}</strong> databases
            </span>
            <span className="success">
              <strong>{execution.summary.successCount}</strong> sucesso
            </span>
            <span className={execution.summary.errorCount ? "danger" : ""}>
              <strong>{execution.summary.errorCount}</strong> erros
            </span>
            <span>
              <strong>
                {execution.summary.rowCount.toLocaleString("pt-BR")}
              </strong>{" "}
              registros
            </span>
            <span>
              {(execution.executionTimeMs / 1000).toLocaleString("pt-BR")} s
            </span>
          </div>
          <p className="execution-context">
            {execution.connectionName} ·{" "}
            {new Date(execution.executedAt).toLocaleString("pt-BR")}
          </p>
          {execution.warnings.map((w) => (
            <p key={w} role="alert" className="notice warning">
              {w === "AUDIT_UNAVAILABLE"
                ? "Não foi possível registrar toda a auditoria desta execução."
                : w === "CONSOLIDATED_LIMIT"
                  ? "Consolidado limitado pelo orçamento de memória. As abas individuais preservam os resultados retornados."
                  : w}
            </p>
          ))}
          {execution.results.some((r) => r.truncated) && (
            <p className="notice warning">
              Resultado limitado.{" "}
              {execution.results
                .filter((r) => r.truncated)
                .map(
                  (r) =>
                    `${r.database}: ${r.rowCount} registros (${r.truncationReason === "ROW_LIMIT" ? `limite configurado: ${r.rowLimit}` : "limite de tamanho"})`,
                )
                .join(" · ")}
            </p>
          )}
          <div
            className="result-tabs"
            role="tablist"
            aria-label="Resultados por database"
          >
            <button
              role="tab"
              aria-selected={tab === null || !result}
              className={tab === null || !result ? "active" : ""}
              onClick={() => setTab(null)}
            >
              Consolidado
            </button>
            {execution.results.map((r) => (
              <button
                key={r.database}
                role="tab"
                aria-selected={tab === r.database}
                className={tab === r.database ? "active" : ""}
                onClick={() => setTab(r.database)}
              >
                {r.status === "SUCCESS" ? (
                  <CheckCircle2 size={14} />
                ) : (
                  <AlertCircle size={14} className="danger" />
                )}
                {r.database}
              </button>
            ))}
          </div>
          {result?.error ? (
            <div role="alert" className="notice error">
              {result.error.message} <small>{result.error.code}</small>
            </div>
          ) : (
            table && (
              <DynamicTable key={execution.executionId + tab} {...table} />
            )
          )}
          <details className="execution-details">
            <summary>Resumo por database</summary>
            <table>
              <thead>
                <tr>
                  <th>Database</th>
                  <th>Status</th>
                  <th>Registros</th>
                  <th>Tempo</th>
                </tr>
              </thead>
              <tbody>
                {execution.results.map((r) => (
                  <tr key={r.database}>
                    <td>{r.database}</td>
                    <td className={r.error ? "danger" : "success"}>
                      {r.error ? r.error.message : "Sucesso"}
                    </td>
                    <td>{r.rowCount}</td>
                    <td>{r.executionTimeMs} ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </details>
        </>
      )}
      {error && (
        <p role="alert" className="notice error">
          {error}
        </p>
      )}
    </section>
  );
}
