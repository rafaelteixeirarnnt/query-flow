import {
  render,
  screen,
  waitFor,
  cleanup,
  fireEvent,
} from "@testing-library/react";
import { afterEach, test, expect, vi } from "vitest";
import { QueryWorkspace } from "./QueryWorkspace";
vi.mock("./SqlEditor", () => ({
  default: ({ disabled }: { disabled: boolean }) => (
    <textarea aria-label="SQL" disabled={disabled} />
  ),
}));
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
test("ignores discovery from an old connection after the selection changes", async () => {
  let finishA: (value: Response) => void = () => {};
  vi.stubGlobal(
    "fetch",
    vi.fn((url: string) =>
      url.includes("/1/")
        ? new Promise<Response>((resolve) => {
            finishA = resolve;
          })
        : Promise.resolve(
            new Response(JSON.stringify({ databases: ["database_B"] })),
          ),
    ),
  );
  const props = { connections: [], onSelect: () => {}, onManage: () => {} };
  const { rerender } = render(<QueryWorkspace {...props} connectionId={1} />);
  rerender(<QueryWorkspace {...props} connectionId={2} />);
  await screen.findByText("database_B");
  finishA(new Response(JSON.stringify({ databases: ["database_A"] })));
  await waitFor(() =>
    expect(screen.queryByText("database_A")).not.toBeInTheDocument(),
  );
  expect(screen.getByText("database_B")).toBeInTheDocument();
});

test("locks the editor while validating so a stale response cannot validate new SQL", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(() => new Promise(() => {})),
  );
  render(
    <QueryWorkspace
      connections={[]}
      connectionId={null}
      onSelect={() => {}}
      onManage={() => {}}
    />,
  );
  await screen.findByLabelText("SQL");
  fireEvent.click(screen.getByRole("button", { name: "Validar SQL" }));
  expect(screen.getByLabelText("SQL")).toBeDisabled();
});

test("loads the server maximum and sends the chosen per-database limit", async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve(new Response(JSON.stringify(
    url.endsWith('/query/config') ? { maxResultRows: 500 } :
    url.endsWith('/databases') ? { databases: ['qa'] } : null
  ))));
  vi.stubGlobal('fetch', fetchMock);
  render(<QueryWorkspace connections={[]} connectionId={1} onSelect={() => {}} onManage={() => {}} />);
  const limit = await screen.findByRole('spinbutton', { name: 'Registros por database' });
  await waitFor(() => expect(limit).toHaveValue(500));
  fireEvent.click(await screen.findByRole('checkbox', { name: 'qa' }));
  fireEvent.change(limit, { target: { value: '50' } });
  fireEvent.click(screen.getByRole('button', { name: 'Executar' }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/query/execute', expect.objectContaining({
    body: expect.stringContaining('"rowLimit":50')
  })));
  fireEvent.change(limit, { target: { value: '501' } });
  expect(screen.getByRole('button', { name: 'Executar' })).toBeDisabled();
});
