import * as monaco from "monaco-editor";

import type { SubItem } from "@knime/scripting-editor";

import { functionDataToMarkdown } from "@/components/function-catalog/functionDescriptionToMarkdown";
import type { FunctionCatalogEntryData } from "@/components/functionCatalogTypes";

type StaticCompletionItem = Omit<monaco.languages.CompletionItem, "range">;

/** A regex that matches text before a string and the starting quote */
const INSIDE_DOUBLE_QUOTE_STRING_REGEX =
  /^(?:[^"\\]|\\.|"(?:[^"\\]|\\.)*")*"([^"\\]|\\.)*$/;
const INSIDE_SINGLE_QUOTE_STRING_REGEX =
  /^(?:[^'\\]|\\.|'(?:[^'\\]|\\.)*')*'([^'\\]|\\.)*$/;

/**
 * Convert the function catalog entries into a format that can be used by the
 * monaco editor for autocompletion.
 */
const convertFunctionsToInsertionItems = (
  entries: FunctionCatalogEntryData[],
): StaticCompletionItem[] =>
  entries.map((entry) => {
    if (entry.entryType === "function") {
      const argumentsPlaceholder = entry.arguments.length > 0 ? "..." : "";
      return {
        label: `${entry.name}(${argumentsPlaceholder})`,
        insertText: `${entry.name}($0)`,
        kind: monaco.languages.CompletionItemKind.Function,
        documentation: { value: functionDataToMarkdown(entry) },
        insertTextRules:
          monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
      } satisfies StaticCompletionItem;
    } else {
      return {
        label: entry.name,
        insertText: entry.name,
        kind: monaco.languages.CompletionItemKind.Constant,
        documentation: { value: entry.description },
        detail: `Type: ${entry.returnType}`,
      } satisfies StaticCompletionItem;
    }
  });

/** Escape quotes in the column or flow variable name */
const escapeName = (name: string, quote: "'" | '"') => {
  name = name.replace(/\\/g, "\\\\");
  if (quote === "'") {
    return name.replace(/'/g, "\\'");
  } else {
    return name.replace(/"/g, '\\"');
  }
};

/**
 * Construct a CompletionItem (missing the range) for columns and
 * flow variables from a text and type
 */
const getInputItemCompletion = (
  text: string,
  type: string,
): StaticCompletionItem => ({
  label: text,
  kind: monaco.languages.CompletionItemKind.Variable,
  insertText: text,
  detail: `Type: ${type}`,
});

/**
 * Map the items of columns of flow variables to all possible completions with the given quote.
 * Note that we pass the quote here because we want to omit the single quote completions if the
 * user did not type a single quote yet.
 */
const getInputsCompletions = (
  items: SubItem<Record<string, any>>[],
  prefix: "$" | "$$",
  quote: "'" | '"',
) =>
  items
    .filter((item) => item.supported)
    .flatMap((item) => {
      // If the item has an insertion text, use that and only that
      // This is the case for ROW_ID, ROW_INDEX, ROW_NUMBER
      if (item.insertionText) {
        return [
          getInputItemCompletion(item.insertionText, item.type.displayName),
        ];
      }

      const longForm = `${prefix}[${quote}${escapeName(item.name, quote)}${quote}]`;

      // Only add the short form if the name is a valid identifier
      if (/^[_a-zA-Z]\w*$/.test(item.name)) {
        const shortForm = `${prefix}${item.name}`;
        return [
          getInputItemCompletion(longForm, item.type.displayName),
          getInputItemCompletion(shortForm, item.type.displayName),
        ];
      } else {
        return [getInputItemCompletion(longForm, item.type.displayName)];
      }
    });

/** Utility to construct a monaco Range based on a Position */
const relativeRange = (
  position: monaco.IPosition,
  charsBefore: number,
  charsAfter: number = 0,
): monaco.IRange => ({
  startLineNumber: position.lineNumber,
  startColumn: Math.max(position.column - charsBefore, 1),
  endLineNumber: position.lineNumber,
  endColumn: position.column + charsAfter,
});

/**
 * Get the context of the completion. This includes
 * - `range`: The existing text that should be overwritten by the completion
 *   - For columns/flow variables: Parts of "$[" or "$$[" and closing quotes and brackets
 *   - For others: The current word
 * - `quote`: The quote character that should be used for the completion
 *    (defaults to `"` if no quote is used in the surrounding text)
 */
const getCompletionContext = (
  model: monaco.editor.ITextModel,
  position: monaco.IPosition,
): { range: monaco.IRange; quote: "'" | '"' } => {
  const relRange = (charsBefore: number, charsAfter: number = 0) =>
    relativeRange(position, charsBefore, charsAfter);

  const textBefore = (numChars: number) =>
    model.getValueInRange(relRange(numChars));

  const textAfter = (numChars: number) =>
    model.getValueInRange(relRange(0, numChars));

  // NB: The numbers are not magic but the length of the strings
  /* eslint-disable no-magic-numbers */

  if (textBefore(4) === '$$["') {
    // Started for flow variable with double quote
    return {
      range: relRange(4, textAfter(2) === '"]' ? 2 : 0),
      quote: '"',
    };
  } else if (textBefore(4) === "$$['") {
    // Started for flow variable with single quote
    return {
      range: relRange(4, textAfter(2) === "']" ? 2 : 0),
      quote: "'",
    };
  } else if (textBefore(3) === "$$[") {
    // Started for flow variable with opening bracket
    return {
      range: relRange(3, textAfter(1) === "]" ? 1 : 0),
      quote: '"',
    };
  } else if (textBefore(3) === '$["') {
    // Started for column with double quote
    return {
      range: relRange(3, textAfter(2) === '"]' ? 2 : 0),
      quote: '"',
    };
  } else if (textBefore(3) === "$['") {
    // Started for column with single quote
    return {
      range: relRange(3, textAfter(2) === "']" ? 2 : 0),
      quote: "'",
    };
  } else if (textBefore(2) === "$[") {
    // Started for column with opening bracket
    return {
      range: relRange(2, textAfter(1) === "]" ? 1 : 0),
      quote: '"',
    };
  } else if (textBefore(2) === "$$") {
    // Started for flow variable
    return { range: relRange(2), quote: '"' };
  } else if (textBefore(1) === "$") {
    // Started for column or flow variable
    return { range: relRange(1), quote: '"' };
  } else if (textBefore(2) === "&&" || textBefore(2) === "||") {
    // Started for logical operator
    return { range: relRange(2), quote: '"' };
  } else if (
    textBefore(1) === "&" ||
    textBefore(1) === "|" ||
    textBefore(1) === "!"
  ) {
    // Started for logical operator
    return { range: relRange(1), quote: '"' };
  } else {
    // ELSE: We are not in a special case -> use the current word
    return {
      range: {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: model.getWordUntilPosition(position).startColumn,
        endColumn: position.column,
      },
      quote: '"',
    };
  }
  /* eslint-enable no-magic-numbers */
};

const logicalOperatorsConstantsCompletions: StaticCompletionItem[] = [
  {
    label: "and",
    insertText: "and",
    kind: monaco.languages.CompletionItemKind.Operator,
    documentation: "Logical 'and' operator",
  },
  {
    label: "and",
    insertText: "and",
    kind: monaco.languages.CompletionItemKind.Operator,
    documentation: "Logical 'and' operator",
    filterText: "&&",
  },
  {
    label: "or",
    insertText: "or",
    kind: monaco.languages.CompletionItemKind.Operator,
    documentation: "Logical 'or' operator",
  },
  {
    label: "or",
    insertText: "or",
    kind: monaco.languages.CompletionItemKind.Operator,
    documentation: "Logical 'or' operator",
    filterText: "||",
  },
  {
    label: "not",
    insertText: "not",
    kind: monaco.languages.CompletionItemKind.Operator,
    documentation: "Logical 'not' operator",
  },
  {
    label: "not",
    insertText: "not",
    kind: monaco.languages.CompletionItemKind.Operator,
    documentation: "Logical 'not' operator",
    filterText: "!",
  },
];

export const registerCompletionItemProvider = ({
  columnGetter,
  flowVariableGetter,
  functionData,
  languageName,
}: {
  columnGetter: () => SubItem<Record<string, any>>[];
  flowVariableGetter: () => SubItem<Record<string, any>>[];
  functionData: FunctionCatalogEntryData[];
  languageName: string;
}) => {
  const functionsConstantsCompletions: StaticCompletionItem[] = [
    ...convertFunctionsToInsertionItems(functionData),
  ];

  const getColumnCompletions = (quote: "'" | '"') =>
    getInputsCompletions(columnGetter(), "$", quote);

  const getFlowVariableCompletions = (quote: "'" | '"') =>
    getInputsCompletions(flowVariableGetter(), "$$", quote);

  return monaco.languages.registerCompletionItemProvider(languageName, {
    triggerCharacters: ["$", "[", '"', "'", "&", "|", "!"],
    provideCompletionItems: (model, position) => {
      // We always provide the same items. Monaco does the filtering.
      // We only have to provide a reasonable range
      const { range, quote } = getCompletionContext(model, position);

      // Do not trigger the autocomplete if we are inside a string
      const textUntilComplete = model.getValueInRange({
        startLineNumber: 1,
        startColumn: 1,
        // We use the range here to not consider the text that would be overwritten
        // by the completion. Effect: Do not trigger for `"$["`
        endLineNumber: range.startLineNumber,
        endColumn: range.startColumn,
      });
      if (
        INSIDE_DOUBLE_QUOTE_STRING_REGEX.test(textUntilComplete) ||
        INSIDE_SINGLE_QUOTE_STRING_REGEX.test(textUntilComplete)
      ) {
        return null;
      }

      // Collect all the completion items using the quote from the context
      const items = [
        ...functionsConstantsCompletions,
        ...getColumnCompletions(quote),
        ...getFlowVariableCompletions(quote),
        ...logicalOperatorsConstantsCompletions,
      ];

      return {
        // Add the range to the items
        suggestions: items.map((item) => ({ ...item, range })),
      } satisfies monaco.languages.CompletionList;
    },
  });
};
