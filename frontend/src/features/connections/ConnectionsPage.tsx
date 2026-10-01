import { useState } from "react";
import { Database, Plus, PlugZap, Pencil, Trash2 } from "lucide-react";
import type { Connection } from "../../types/api";
import { api, message } from "../../lib/api";
import { ConnectionDialog } from "./ConnectionDialog";
export function ConnectionsPage({
  connections,
  onRefresh,
  onSelect,
}: {
  connections: Connection[];
  onRefresh: () => void;
  onSelect: (id: number) => void;
}) {
  const [editing, setEditing] = useState<Connection | null | undefined>(
    undefined,
  );
  const [busy, setBusy] = useState<number | null>(null);
  const [notice, setNotice] = useState("");
  const [deleting, setDeleting] = useState<number | null>(null);
  async function test(c: Connection) {
    setBusy(c.id);
    setNotice("");
    try {
      const r = await api.test(c.id);
      setNotice(`${c.name}: ${r.message} (${r.responseTimeMs} ms)`);
    } catch (e) {
      setNotice(message(e));
    } finally {
      setBusy(null);
    }
  }
  return (
    <>
      <div className="page-title">
        <div>
          <h1>Conexões</h1>
          <p>Seus servidores PostgreSQL, prontos para consultar.</p>
        </div>
        <button className="primary" onClick={() => setEditing(null)}>
          <Plus size={17} /> Nova conexão
        </button>
      </div>
      {notice && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}
      {!connections.length ? (
        <div className="empty-panel">
          <Database size={36} />
          <h2>Adicione sua primeira conexão</h2>
          <p>Cadastre um servidor para descobrir os databases disponíveis.</p>
          <button className="primary" onClick={() => setEditing(null)}>
            <Plus size={16} /> Nova conexão
          </button>
        </div>
      ) : (
        <div className="connection-list">
          {connections.map((c) => (
            <article className="connection-card" key={c.id}>
              <div className="connection-title">
                <div className="db-icon">
                  <Database size={23} />
                </div>
                <div>
                  <h2>{c.name}</h2>
                  <p className="mono">
                    {c.host}:{c.port}
                  </p>
                </div>
                <span className={c.active ? "status success" : "status"}>
                  {c.active ? "Ativa" : "Inativa"}
                </span>
              </div>
              {c.description && <p>{c.description}</p>}
              <div className="connection-meta">
                {c.initialDatabase} <span>·</span> {c.username} <span>·</span>{" "}
                {c.sslEnabled ? "SSL habilitado" : "Sem SSL"}
              </div>
              <div className="connection-actions">
                <button
                  className="primary"
                  disabled={!c.active}
                  onClick={() => onSelect(c.id)}
                >
                  Conectar
                </button>
                <button
                  disabled={busy === c.id || !c.active}
                  onClick={() => test(c)}
                >
                  <PlugZap size={15} />
                  {busy === c.id ? "Testando…" : "Testar"}
                </button>
                <button onClick={() => setEditing(c)}>
                  <Pencil size={15} />
                  Editar
                </button>
                <button
                  className="icon-button danger"
                  title="Excluir conexão"
                  aria-label={`Excluir ${c.name}`}
                  onClick={() => setDeleting(c.id)}
                >
                  <Trash2 size={16} />
                </button>
              </div>
              {deleting === c.id && (
                <div className="delete-confirm">
                  <p>Excluir a conexão {c.name}?</p>
                  <button onClick={() => setDeleting(null)}>Cancelar</button>
                  <button
                    className="danger"
                    onClick={async () => {
                      try {
                        await api.remove(c.id);
                        onRefresh();
                        setDeleting(null);
                      } catch (e) {
                        setNotice(message(e));
                      }
                    }}
                  >
                    Confirmar exclusão
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
      {editing !== undefined && (
        <ConnectionDialog
          connection={editing ?? undefined}
          onClose={() => setEditing(undefined)}
          onSaved={onRefresh}
        />
      )}
    </>
  );
}
