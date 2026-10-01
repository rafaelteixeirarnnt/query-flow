import * as Dialog from "@radix-ui/react-dialog";
import { useState } from "react";
import { X, ShieldCheck } from "lucide-react";
import type { Connection, ConnectionInput } from "../../types/api";
import { api, message } from "../../lib/api";
export function ConnectionDialog({
  connection,
  onClose,
  onSaved,
}: {
  connection?: Connection;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<ConnectionInput>({
    name: connection?.name ?? "",
    description: connection?.description ?? "",
    host: connection?.host ?? "",
    port: connection?.port ?? 5432,
    initialDatabase: connection?.initialDatabase ?? "postgres",
    username: connection?.username ?? "",
    password: "",
    version: connection?.version,
    sslEnabled: connection?.sslEnabled ?? false,
    active: connection?.active ?? true,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const field = (
    key: keyof ConnectionInput,
    label: string,
    type = "text",
    required = true,
  ) => (
    <label key={key}>
      {label}
      <input
        required={required}
        maxLength={key === "password" ? 4096 : key === "host" ? 253 : 128}
        type={type}
        value={String(form[key] ?? "")}
        autoComplete={key === "password" ? "new-password" : "off"}
        onChange={(e) =>
          setForm({
            ...form,
            [key]: type === "number" ? Number(e.target.value) : e.target.value,
          })
        }
        min={type === "number" ? 1 : undefined}
        max={type === "number" ? 65535 : undefined}
        placeholder={
          key === "password" && connection
            ? "Deixe em branco para manter a senha"
            : undefined
        }
      />
    </label>
  );
  return (
    <Dialog.Root
      open
      onOpenChange={(open) => {
        if (!open && !saving) onClose();
      }}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="overlay" />
        <Dialog.Content className="dialog">
          <div className="section-heading">
            <Dialog.Title>
              {connection ? "Editar conexão" : "Nova conexão"}
            </Dialog.Title>
            <Dialog.Close asChild>
              <button
                className="icon-button"
                aria-label="Fechar"
                disabled={saving}
              >
                <X size={20} />
              </button>
            </Dialog.Close>
          </div>
          <Dialog.Description>
            Informe o servidor PostgreSQL que deseja consultar.
          </Dialog.Description>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setSaving(true);
              setError("");
              try {
                await api.save(form, connection?.id);
                onSaved();
                onClose();
              } catch (err) {
                setError(message(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            <fieldset disabled={saving} className="form-grid">
              {field("name", "Nome da conexão")}
              {field("description", "Descrição", "text", false)}
              {field("host", "Host")}
              {field("port", "Porta", "number")}
              {field("initialDatabase", "Database inicial")}
              {field("username", "Usuário")}
              {field("password", "Senha", "password", !connection)}
              <div className="checkbox-options">
                <label>
                  <input
                    type="checkbox"
                    checked={form.sslEnabled}
                    onChange={(e) =>
                      setForm({ ...form, sslEnabled: e.target.checked })
                    }
                  />{" "}
                  SSL com certificado válido
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={form.active}
                    onChange={(e) =>
                      setForm({ ...form, active: e.target.checked })
                    }
                  />{" "}
                  Conexão ativa
                </label>
              </div>
            </fieldset>
            <p className="security-note">
              <ShieldCheck size={16} /> Senha protegida com AES-256-GCM.
            </p>
            {error && (
              <p role="alert" className="notice error">
                {error}
              </p>
            )}
            <div className="dialog-footer">
              <button type="button" onClick={onClose} disabled={saving}>
                Cancelar
              </button>
              <button className="primary" disabled={saving}>
                {saving ? "Salvando…" : "Salvar conexão"}
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
