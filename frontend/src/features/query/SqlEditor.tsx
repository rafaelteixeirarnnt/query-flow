import Editor, { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
(self as unknown as { MonacoEnvironment: unknown }).MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};
loader.config({ monaco });
export default function SqlEditor({
  value,
  onChange,
  disabled,
}: {
  value: string;
  onChange: (v: string) => void;
  disabled: boolean;
}) {
  return (
    <Editor
      height="230px"
      language="sql"
      value={value}
      onChange={(v) => onChange(v ?? "")}
      loading={<p className="editor-loading">Carregando editor SQL…</p>}
      options={{
        readOnly: disabled,
        fontSize: 14,
        fontFamily: '"SFMono-Regular", Consolas, monospace',
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        padding: { top: 18 },
        lineNumbersMinChars: 3,
        automaticLayout: true,
        wordWrap: "on",
        tabSize: 2,
        renderLineHighlight: "none",
        overviewRulerLanes: 0,
      }}
    />
  );
}
