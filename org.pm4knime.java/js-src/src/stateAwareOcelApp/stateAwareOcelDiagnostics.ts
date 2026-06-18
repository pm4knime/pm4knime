import {
  type UseCodeEditorReturn,
  getScriptingService,
} from "@knime/scripting-editor";

import {
  type Diagnostic,
  getEditorErrorStateFromDiagnostics,
  markDiagnosticsInEditor,
} from "@/generalDiagnostics";
import type { StateExpressionDiagnosticResult } from "@/stateAwareOcelApp/stateAwareOcelTypes";

export const runStateRuleDiagnostics = async (
  editorStates: UseCodeEditorReturn[],
  stateNames: string[],
): Promise<Diagnostic[]> => {
  const expressions = editorStates.map((editorState) => editorState.text.value);
  const results: StateExpressionDiagnosticResult[] =
    await getScriptingService().sendToService("getStateRuleDiagnostics", [
      stateNames,
      expressions,
    ]);

  results.forEach(({ diagnostics }, index) =>
    markDiagnosticsInEditor(diagnostics, editorStates[index]),
  );

  return results.map(({ diagnostics, returnType }) => ({
    errorState: getEditorErrorStateFromDiagnostics(diagnostics),
    returnType,
  }));
};
