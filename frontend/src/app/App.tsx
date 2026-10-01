import { useCallback, useEffect, useRef, useState } from "react";
import { Database, ShieldCheck } from "lucide-react";
import { api, message } from "../lib/api";
import type { Connection } from "../types/api";
import { ConnectionsPage } from "../features/connections/ConnectionsPage";
import { QueryWorkspace } from "../features/query/QueryWorkspace";
export default function App() {
  const [page, setPage] = useState<"query" | "connections">("query");
  const [connections, setConnections] = useState<Connection[]>([]);
  const [connectionId, setConnectionId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const latestRequest = useRef(0);
  const refresh = useCallback(() => {
    const revision = ++latestRequest.current;
    api
      .connections()
      .then((data) => {
        if (revision !== latestRequest.current) return;
        setConnections(data);
        setConnectionId((current) =>
          data.some((c) => c.id === current && c.active) ? current : null,
        );
        setError("");
      })
      .catch((e) => {
        if (revision === latestRequest.current) setError(message(e));
      });
  }, []);
  useEffect(refresh, [refresh]);
  return (
    <>
      <header className="app-header">
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            setPage("query");
          }}
        >
          <Database size={30} strokeWidth={2.3} />
          <span>QueryHub</span>
        </a>
        <nav aria-label="Navegação principal">
          <button
            className={page === "query" ? "active" : ""}
            onClick={() => setPage("query")}
          >
            Consultas
          </button>
          <button
            className={page === "connections" ? "active" : ""}
            onClick={() => setPage("connections")}
          >
            Conexões
          </button>
        </nav>
        <span className="header-meta">
          <ShieldCheck size={15} /> PostgreSQL · Somente leitura
        </span>
      </header>
      <main>
        {error && (
          <div role="alert" className="notice error">
            {error}
            <button onClick={refresh}>Tentar novamente</button>
          </div>
        )}
        {page === "query" ? (
          <QueryWorkspace
            connections={connections}
            connectionId={connectionId}
            onSelect={setConnectionId}
            onManage={() => setPage("connections")}
          />
        ) : (
          <ConnectionsPage
            connections={connections}
            onRefresh={refresh}
            onSelect={(id) => {
              setConnectionId(id);
              setPage("query");
            }}
          />
        )}
      </main>
      <footer className="app-footer">
        QueryHub <span>Gerenciador de consultas multi-database</span>
      </footer>
    </>
  );
}
