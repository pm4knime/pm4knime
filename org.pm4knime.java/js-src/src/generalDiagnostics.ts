import { editor as MonacoEditor, Range } from "monaco-editor";

import type { SubItemType, UseCodeEditorReturn } from "@knime/scripting-editor";

import {
  DIAGNOSTIC_SEVERITY_TO_MARKER_SEVERITY,
  EXPRESSION_MARKERS_OWNER,
} from "@/common/constants";
import type { SelectorState } from "@/components/OutputSelector.vue";
import type { ExpressionReturnType } from "@/flowVariableApp/flowVariableTypes";

/** Identifies the error level of an ExpressionEditorPane */
export type ErrorLevel = "ERROR" | "WARNING" | "OK";

/** Error level with attached message */
export type EditorErrorState =
  | { level: "OK" }
  | { level: ErrorLevel; message: string };

export type ExpressionDiagnostic = {
  message: string;
  shortMessage: string;
  severity: "ERROR" | "WARNING";
  location: {
    start: number;
    stop: number;
  } | null;
};

export type ExpressionDiagnosticResult = {
  diagnostics: ExpressionDiagnostic[];
  returnType: SubItemType<ExpressionReturnType>;
};

export type Diagnostic = {
  errorState: EditorErrorState;
  returnType: SubItemType<ExpressionReturnType>;
};

export const markDiagnosticsInEditor = (
  diagnosticsForThisEditor: ExpressionDiagnostic[],
  editorState: UseCodeEditorReturn,
) => {
  const markers: MonacoEditor.IMarkerData[] = diagnosticsForThisEditor
    .map((diagnostic) =>
      diagnostic.location
        ? {
            ...Range.fromPositions(
              editorState.editorModel.getPositionAt(diagnostic.location.start),
              editorState.editorModel.getPositionAt(diagnostic.location.stop),
            ),
            message: diagnostic.message,
            severity:
              DIAGNOSTIC_SEVERITY_TO_MARKER_SEVERITY[diagnostic.severity],
          }
        : null,
    )
    .filter((diagnostic) => diagnostic !== null);

  MonacoEditor.setModelMarkers(
    editorState.editorModel,
    EXPRESSION_MARKERS_OWNER,
    markers,
  );
};

/**
 * Get the first diagnostic with the highest severity, and its shortMessage as the EditorErrorState.
 * If there are no diagnostics that are ERRORs or WARNINGs, return { level: "OK" }.
 *
 * @param diagnosticsForThisEditor
 * @returns the EditorErrorState showing the first diagnostic with the highest severity,
 *      or { level: "OK" } if there are no diagnostics.
 */
export const getEditorErrorStateFromDiagnostics = (
  diagnosticsForThisEditor: ExpressionDiagnostic[],
): EditorErrorState => {
  const errorDiagnostic = diagnosticsForThisEditor.find(
    (d) => d.severity === "ERROR",
  );
  if (errorDiagnostic) {
    return { level: "ERROR", message: errorDiagnostic.shortMessage };
  }

  const warningDiagnostic = diagnosticsForThisEditor.find(
    (d) => d.severity === "WARNING",
  );
  if (warningDiagnostic) {
    return { level: "WARNING", message: warningDiagnostic.shortMessage };
  }

  return { level: "OK" };
};

export const runOutputDiagnostics = (
  label: "column" | "flow variable",
  orderedStates: SelectorState[],
  inputItems: string[],
): (string | null)[] => {
  const labelCapitalised = label.charAt(0).toUpperCase() + label.slice(1);

  const appendedItemsSoFar: string[] = [];
  const allAppendedItems = orderedStates
    .filter((s) => s.outputMode === "APPEND")
    .map((s) => s.create);

  return orderedStates.map((state) => {
    if (state.outputMode === "APPEND") {
      if (state.create.trim() === "") {
        return `Appended ${label} name is empty.`;
      } else if (
        appendedItemsSoFar.includes(state.create) ||
        inputItems.includes(state.create)
      ) {
        return `${labelCapitalised} "${state.create}" already exists. Try another name.`;
      } else if (
        label === "flow variable" &&
        state.create.startsWith("knime")
      ) {
        return `${labelCapitalised} names starting with 'knime' are reserved. Try another name.`;
      }
      appendedItemsSoFar.push(state.create);
    }

    if (state.outputMode === "REPLACE_EXISTING") {
      const alreadyAppended = appendedItemsSoFar.includes(state.replace);

      if (!alreadyAppended && allAppendedItems.includes(state.replace)) {
        return `${labelCapitalised} "${state.replace}" was replaced before it was appended. Try reordering your expressions.`;
      } else if (!alreadyAppended && !inputItems.includes(state.replace)) {
        return `${labelCapitalised} "${state.replace}" does not exist. Try selecting another.`;
      }
    }
    return null;
  });
};
