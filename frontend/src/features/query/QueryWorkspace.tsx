import { lazy, Suspense, useEffect, useState } from "react";
import { Play, ShieldCheck, Terminal, LoaderCircle } from "lucide-react";
import type { Connection, Execution } from "../../types/api";
import { api, message } from "../../lib/api";
import { DatabaseSelector } from "./DatabaseSelector";
import { ExecutionResults } from "./ExecutionResults";
const SqlEditor = lazy(() => import("./SqlEditor"));
export function QueryWorkspace({
  connections,
  connectionId,
  onSelect,
  onManage,
}: {
  connections: Connection[];
  connectionId: number | null;
  onSelect: (id: number | null) => void;
  onManage: () => void;
}) {
  const [databases, setDatabases] = useState<string[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [discovering, setDiscovering] = useState(false);
  const [sql, setSql] = useState(
    "SELECT\n    id,\n    nome,\n    situacao\nFROM agh.exemplo;",
  );
  const [maxRows, setMaxRows] = useState<number | null>(null);
  const [rowLimit, setRowLimit] = useState("");
  const [configError, setConfigError] = useState("");
  const validLimit = maxRows !== null && /^\d+$/.test(rowLimit) && Number(rowLimit) >= 1 && Number(rowLimit) <= maxRows;
  useEffect(() => {
    const controller = new AbortController();
    api.queryConfig(controller.signal).then(config => {
      if (!controller.signal.aborted) {
        if (!Number.isInteger(config.maxResultRows) || config.maxResultRows < 1) throw new Error("Limite do servidor indisponível. Recarregue a página.");
        setMaxRows(config.maxResultRows);
        setRowLimit(String(config.maxResultRows));
      }
    }).catch(e => { if (!controller.signal.aborted) setConfigError(message(e)); });
    return () => controller.abort();
  }, []);
  const [executing, setExecuting] = useState(false);
  const [validating, setValidating] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [execution, setExecution] = useState<Execution | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setDatabases([]);
    setSelected([]);
    setError("");
    setDiscovering(connectionId !== null);
    if (connectionId !== null)
      api
        .databases(connectionId, controller.signal)
        .then((r) => {
          if (!controller.signal.aborted) setDatabases(r.databases);
        })
        .catch((e) => {
          if (!controller.signal.aborted) setError(message(e));
        })
        .finally(() => {
          if (!controller.signal.aborted) setDiscovering(false);
        });
    return () => controller.abort();
  }, [connectionId]);
  async function execute() {
    if (connectionId === null || !validLimit) return;
    setExecuting(true);
    setNotice("");
    setError("");
    try {
      setExecution(await api.execute(connectionId, selected, sql, Number(rowLimit)));
    } catch (e) {
      setError(message(e));
    } finally {
      setExecuting(false);
    }
  }
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Workspace SQL</h1>
          <p>Uma consulta. Vários databases.</p>
        </div>
        <span className="read-only">
          <ShieldCheck size={16} /> Somente leitura
        </span>
      </div>
      <div className="workspace">
        <aside className="connection-rail">
          <div className="section-heading">
            <h3>Conexão ativa</h3>
            <button className="link" onClick={onManage}>
              Gerenciar
            </button>
          </div>
          <select
            aria-label="Conexão ativa"
            disabled={executing}
            value={connectionId ?? ""}
            onChange={(e) =>
              onSelect(e.target.value ? Number(e.target.value) : null)
            }
          >
            <option value="">Selecione uma conexão</option>
            {connections
              .filter((c) => c.active)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
          </select>
          {connections.length === 0 && (
            <button className="add-connection" onClick={onManage}>
              + Cadastrar conexão
            </button>
          )}
          <DatabaseSelector
            databases={databases}
            selected={selected}
            onChange={setSelected}
            loading={discovering}
            disabled={executing}
          />
        </aside>
        <div className="workspace-content">
          <section className="panel editor-panel">
            <div className="section-heading">
              <h2>
                <Terminal size={19} /> Editor SQL
              </h2>
              <span className="muted">PostgreSQL</span>
            </div>
            <div className="editor-frame">
              <Suspense
                fallback={
                  <div className="editor-loading">Carregando editor SQL…</div>
                }
              >
                <SqlEditor
                  value={sql}
                  onChange={(v) => {
                    setSql(v);
                    setNotice("");
                  }}
                  disabled={executing || validating}
                />
              </Suspense>
            </div>
            <div className="editor-footer">
              <label className="query-row-limit">
                <span>Registros por database</span>
                <input aria-label="Registros por database" type="number" min={1} max={maxRows ?? undefined} step={1}
                  value={rowLimit} disabled={executing || maxRows === null}
                  aria-describedby="row-limit-help" aria-invalid={maxRows !== null && !validLimit}
                  onChange={e => setRowLimit(e.target.value)} />
                <small id="row-limit-help">{maxRows === null ? "Carregando limite…" : `De 1 a ${maxRows.toLocaleString("pt-BR")}`}</small>
              </label>
              <div className="actions">
                <button
                  disabled={executing}
                  onClick={() => {
                    setSql("");
                    setNotice("");
                  }}
                >
                  Limpar
                </button>
                <button
                  disabled={executing || validating || !sql.trim()}
                  onClick={async () => {
                    setValidating(true);
                    setError("");
                    setNotice("");
                    try {
                      const r = await api.validate(sql);
                      if (r.valid) setNotice(r.message);
                      else setError(r.message);
                    } catch (e) {
                      setError(message(e));
                    } finally {
                      setValidating(false);
                    }
                  }}
                >
                  {validating ? "Validando…" : "Validar SQL"}
                </button>
                <button
                  className="primary"
                  disabled={
                    executing ||
                    !validLimit ||
                    !selected.length ||
                    !sql.trim() ||
                    connectionId === null
                  }
                  onClick={execute}
                >
                  {executing ? (
                    <LoaderCircle className="spin" size={16} />
                  ) : (
                    <Play size={16} />
                  )}{" "}
                  {executing ? "Executando…" : "Executar"}
                </button>
              </div>
            </div>
            {configError && <p role="alert" className="notice error">{configError}</p>}
            {notice && (
              <p role="status" className="notice success">
                {notice}
              </p>
            )}
            {error && (
              <p role="alert" className="notice error">
                {error}
              </p>
            )}
            {executing && (
              <p role="status" className="notice">
                Consultando {selected.length} databases. Aguarde a conclusão ou
                o timeout configurado.
              </p>
            )}
          </section>
          <ExecutionResults
            key={execution?.executionId ?? "empty"}
            execution={execution}
          />
        </div>
      </div>
    </>
  );
}
