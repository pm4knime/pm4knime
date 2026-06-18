import {
  type UseCodeEditorReturn,
  getScriptingService,
} from "@knime/scripting-editor";

import {
  type Diagnostic,
  type ExpressionDiagnosticResult,
  getEditorErrorStateFromDiagnostics,
  markDiagnosticsInEditor,
} from "@/generalDiagnostics";

/**
 * Run the diagnostics for all editors and set the markers in the respective editors.
 *
 * @param editorStates
 * @param allFlowVariableOutputNames a list of all output flow variables names.
 */
export const runFlowVariableDiagnostics = async (
  editorStates: UseCodeEditorReturn[],
  allFlowVariableOutputNames: string[],
): Promise<Diagnostic[]> => {
  const newTexts = editorStates.map((editorState) => editorState.text.value);

  const results: ExpressionDiagnosticResult[] =
    await getScriptingService().sendToService("getFlowVariableDiagnostics", [
      newTexts,
      allFlowVariableOutputNames,
    ]);

  results.forEach(({ diagnostics }, index) =>
    markDiagnosticsInEditor(diagnostics, editorStates[index]),
  );

  return results.map(({ diagnostics, returnType }) => ({
    errorState: getEditorErrorStateFromDiagnostics(diagnostics),
    returnType,
  }));
};
