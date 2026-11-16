import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { test, expect, afterEach } from "vitest";
import { DynamicTable } from "./DynamicTable";
test("keeps duplicate labels and distinguishes null from an empty string", () => {
  render(
    <DynamicTable
      columns={[
        {
          key: "c1",
          label: "nome",
          jdbcType: 12,
          typeName: "text",
          sourceTypes: ["text"],
        },
        {
          key: "c2",
          label: "nome",
          jdbcType: 12,
          typeName: "text",
          sourceTypes: ["text"],
        },
      ]}
      rows={[
        { c1: null, c2: "" },
        { c1: "João", c2: "Maria" },
      ]}
    />,
  );
  expect(screen.getAllByText("nome").length).toBeGreaterThanOrEqual(2);
  expect(screen.getByText("NULL")).toBeInTheDocument();
  expect(screen.getByText("João")).toBeInTheDocument();
  fireEvent.change(screen.getByPlaceholderText("Pesquisar nos resultados..."), {
    target: { value: "Maria" },
  });
  expect(screen.queryByText("NULL")).not.toBeInTheDocument();
  expect(screen.getByText("João")).toBeInTheDocument();
});

test("filters numeric and boolean values as displayed even with a leading null", () => {
  const { unmount } = render(
    <DynamicTable
      columns={[
        {
          key: "c1",
          label: "number",
          jdbcType: 4,
          typeName: "int4",
          sourceTypes: ["int4"],
        },
        {
          key: "c2",
          label: "enabled",
          jdbcType: 16,
          typeName: "bool",
          sourceTypes: ["bool"],
        },
      ]}
      rows={[
        { c1: 1, c2: true },
        { c1: 12, c2: false },
        { c1: 2, c2: true },
      ]}
    />,
  );
  fireEvent.change(screen.getByLabelText("Filtrar number"), {
    target: { value: "12" },
  });
  expect(screen.getByRole("button", { name: "12" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "1" })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Filtrar number"), {
    target: { value: "" },
  });
  fireEvent.change(screen.getByLabelText("Filtrar enabled"), {
    target: { value: "true" },
  });
  expect(screen.getAllByRole("button", { name: "true" })).toHaveLength(2);
  unmount();
  render(
    <DynamicTable
      columns={[
        {
          key: "c1",
          label: "id",
          jdbcType: 4,
          typeName: "int4",
          sourceTypes: ["int4"],
        },
      ]}
      rows={[{ c1: null }, { c1: 12 }]}
    />,
  );
  fireEvent.change(screen.getByLabelText("Filtrar id"), {
    target: { value: "12" },
  });
  expect(screen.getByRole("button", { name: "12" })).toBeInTheDocument();
});

afterEach(cleanup);
test("renders only 100 initial records and paginates the rest", () => {
  render(<DynamicTable columns={[{key:'c1',label:'id',jdbcType:4,typeName:'int4',sourceTypes:['int4']}]} rows={Array.from({length:250},(_,i)=>({c1:i+1}))} />);
  expect(screen.getAllByRole('row')).toHaveLength(101);
  expect(screen.getByRole('combobox',{name:'Registros por página'})).toHaveValue('100');
  expect(screen.queryByRole('button',{name:'101'})).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button',{name:'Próxima página'}));
  expect(screen.getByRole('button',{name:'101'})).toBeInTheDocument();
});
