import { useState } from "react";
import { Search, Database } from "lucide-react";
export function DatabaseSelector({
  databases,
  selected,
  onChange,
  loading,
  disabled,
}: {
  databases: string[];
  selected: string[];
  onChange: (next: string[]) => void;
  loading: boolean;
  disabled: boolean;
}) {
  const [filter, setFilter] = useState("");
  const visible = databases.filter((d) =>
    d.toLowerCase().includes(filter.toLowerCase()),
  );
  return (
    <div className="database-selector">
      <div className="section-heading">
        <h3>Databases</h3>
        <span className="count">{databases.length}</span>
      </div>
      <label className="search">
        <Search size={16} />
        <input
          placeholder="Pesquisar database..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
      </label>
      <div className="selection-actions">
        <button
          disabled={disabled || !visible.length}
          onClick={() => onChange([...new Set([...selected, ...visible])])}
        >
          Selecionar {filter ? "filtrados" : "todos"}
        </button>
        <button
          disabled={disabled || !selected.length}
          onClick={() => onChange([])}
        >
          Limpar
        </button>
      </div>
      <div className="database-list">
        {loading ? (
          <p className="muted">Descobrindo databases…</p>
        ) : visible.length ? (
          visible.map((d) => (
            <label
              className={
                selected.includes(d)
                  ? "database-item selected"
                  : "database-item"
              }
              key={d}
            >
              <input
                type="checkbox"
                checked={selected.includes(d)}
                disabled={disabled}
                onChange={(e) =>
                  onChange(
                    e.target.checked
                      ? [...selected, d]
                      : selected.filter((x) => x !== d),
                  )
                }
              />
              <Database size={15} />
              <span title={d}>{d}</span>
            </label>
          ))
        ) : (
          <p className="muted">
            {databases.length
              ? "Nenhum database encontrado."
              : "Selecione uma conexão para descobrir seus databases."}
          </p>
        )}
      </div>
      <p className="selection-count">
        <strong>{selected.length}</strong> de {databases.length} selecionados
      </p>
    </div>
  );
}
