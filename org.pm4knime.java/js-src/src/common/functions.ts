import { Consola, LogLevels } from "consola";
import { Selection } from "monaco-editor";

import {
  COLUMN_INSERTION_EVENT,
  type InputConnectionInfo,
  type InsertionEvent,
  type UseCodeEditorReturn,
  insertionEventHelper,
} from "@knime/scripting-editor";

import type { ExpressionEditorPaneExposes } from "@/components/ExpressionEditorPane.vue";
import { FUNCTION_INSERTION_EVENT } from "@/components/function-catalog/FunctionCatalog.vue";

export const setupConsola = () => {
  const consola = new Consola({
    level: import.meta.env.DEV ? LogLevels.trace : LogLevels.error,
  });
  const globalObject = typeof global === "object" ? global : window;

  // @ts-expect-error TODO how to tell TS that consola is a global?
  globalObject.consola = consola;
};

export const insertFunctionCall = ({
  editorState,
  functionName,
  functionArgs,
}: {
  editorState: UseCodeEditorReturn;
  functionName: string;
  functionArgs: string[] | null;
}): boolean => {
  const selection = editorState.editor.value?.getSelection();
  const editor = editorState.editor.value;

  if (selection && editor) {
    if (functionArgs === null) {
      // Insert without parentheses
      return editor.executeEdits("function-insert", [
        { range: selection, text: functionName },
      ]);
    } else {
      // Insert with parentheses
      return editor.executeEdits(
        "function-insert",
        [
          {
            range: selection,
            text: `${functionName}(${editor.getModel()?.getValueInRange(selection) ?? ""})`,
          },
        ],
        // Move the cursor between the parentheses
        (editOperations) => [
          Selection.fromPositions({
            lineNumber: editOperations.at(0)!.range.endLineNumber,
            column: editOperations.at(0)!.range.endColumn - 1,
          }),
        ],
      );
    }
  } else {
    return false;
  }
};

export const registerInsertionListener = (
  getActiveEditor: () => ExpressionEditorPaneExposes | null,
) => {
  const focusActiveEditorAndGetState = () => {
    const activeEditor = getActiveEditor();
    activeEditor?.focusEditorProgrammatically();
    return activeEditor?.getEditorState();
  };

  insertionEventHelper
    .getInsertionEventHelper(FUNCTION_INSERTION_EVENT)
    .registerInsertionListener((insertionEvent: InsertionEvent) => {
      const editorState = focusActiveEditorAndGetState();
      if (typeof editorState !== "undefined") {
        insertFunctionCall({
          editorState,
          functionName: insertionEvent.textToInsert,
          functionArgs: insertionEvent.extraArgs?.functionArgs ?? null,
        });
      }
    });

  insertionEventHelper
    .getInsertionEventHelper(COLUMN_INSERTION_EVENT)
    .registerInsertionListener((insertionEvent: InsertionEvent) => {
      const editorState = focusActiveEditorAndGetState();
      if (typeof editorState !== "undefined") {
        const codeToInsert = insertionEvent.textToInsert;

        // Note that we're ignoring requiredImport, because the expression editor
        // doesn't need imports.
        editorState.insertColumnReference(codeToInsert);
      }
    });
};

export const log = (message: any, ...args: any[]) => {
  if (typeof consola === "undefined") {
    // eslint-disable-next-line no-console
    console.log(message, ...args);
  } else {
    consola.log(message, ...args);
  }
};

/*
 *  Loops over the portInformation array and checks if non-optional ports are
 *  not connected or if the connected upstream nodes are not configured or executed.
 *  If any of these conditions are met, the first error message is returned.
 *  If no error is found, null is returned.
 */
export const mapConnectionInfoToErrorMessage = (
  inputConnectionInfo: InputConnectionInfo[] | undefined,
): string | null => {
  if (typeof inputConnectionInfo === "undefined") {
    return "No initial data available. This is an implementation error.";
  }

  const errorMessages = inputConnectionInfo
    .map((port) => {
      if (port.isOptional) {
        return null;
      }
      switch (port.status) {
        case "MISSING_CONNECTION":
          return "To evaluate your expression, connect input data first.";
        case "UNCONFIGURED_CONNECTION":
          return "To evaluate your expression, configure the previous nodes first.";
        case "UNEXECUTED_CONNECTION":
          return "To evaluate your expression, execute the previous nodes first.";
        case "OK":
          break;
      }

      return null;
    })
    .filter((message) => message !== null);

  return errorMessages.length > 0 ? errorMessages[0] : null;
};
