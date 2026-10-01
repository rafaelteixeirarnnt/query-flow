import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { test, expect, vi, afterEach } from "vitest";
import App from "./App";
vi.mock("../features/query/SqlEditor", () => ({
  default: () => <textarea aria-label="SQL" />,
}));
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
test("ignores an old list response after saving and selecting a connection", async () => {
  let finishOld: (r: Response) => void = () => {};
  let gets = 0;
  const connection = {
    id: 1,
    version: 0,
    name: "Nova",
    description: "",
    host: "localhost",
    port: 5432,
    initialDatabase: "postgres",
    username: "reader",
    sslEnabled: false,
    active: true,
    createdAt: "",
    updatedAt: "",
  };
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string, init?: RequestInit) => {
      if (url.endsWith("/databases"))
        return Promise.resolve(new Response(JSON.stringify({ databases: [] })));
      if (init?.method === "POST")
        return Promise.resolve(new Response(JSON.stringify(connection)));
      if (++gets === 1)
        return new Promise<Response>((r) => {
          finishOld = r;
        });
      return Promise.resolve(new Response(JSON.stringify([connection])));
    }),
  );
  render(<App />);
  fireEvent.click(
    screen.getByRole("button", { name: "Conexões" }),
  );
  fireEvent.click(screen.getAllByRole("button", { name: "Nova conexão" })[0]);
  fireEvent.change(screen.getByLabelText("Nome da conexão"), {
    target: { value: "Nova" },
  });
  fireEvent.change(screen.getByLabelText("Host"), {
    target: { value: "localhost" },
  });
  fireEvent.change(screen.getByLabelText("Usuário"), {
    target: { value: "reader" },
  });
  fireEvent.change(screen.getByLabelText("Senha"), {
    target: { value: "test" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Salvar conexão" }));
  await screen.findByRole("button", { name: "Conectar" });
  fireEvent.click(screen.getByRole("button", { name: "Conectar" }));
  finishOld(new Response("[]"));
  await waitFor(() =>
    expect(screen.getByRole("combobox", { name: "Conexão ativa" })).toHaveValue(
      "1",
    ),
  );
  expect(screen.getByRole("option", { name: "Nova" })).toBeInTheDocument();
});
