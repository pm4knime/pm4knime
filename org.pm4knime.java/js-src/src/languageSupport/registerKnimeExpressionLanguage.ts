import * as monaco from "monaco-editor";

import type { SubItem } from "@knime/scripting-editor";

import { LANGUAGE } from "@/common/constants";
import { functionDataToMarkdown } from "@/components/function-catalog/functionDescriptionToMarkdown";
import type { FunctionCatalogEntryData } from "@/components/functionCatalogTypes";

import { registerCompletionItemProvider } from "./autoComplete";

/**
 * Set up the knime-expression language and register it with Monaco.
 *
 * @param columnNamesForCompletion the column names that the autocompletion
 *                                 needs to know about, with types included.
 * @param flowVariableNamesForCompletion the flow variable names
 * @param extraCompletionItems anything else that we might want to add to the
 *                             autocompletion.
 * @param shouldSetTheme whether to set the editor theme as well. Might
 *                       cause conflict if you have multiple editors with
 *                       different languages.
 * @param languageName the name of the language. Useful if we ever need
 *                     different autocompletion suggestions per editor, because
 *                     autocompletion in Monaco is on a per-language basis.
 *
 * @returns a dispose function for the autocompletion. If you register the
 *          language multiple times, e.g. to add/remove autocompletion
 *          suggestions, call this.
 */
const register = ({
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
  monaco.languages.register({ id: languageName });

  monaco.languages.setMonarchTokensProvider(languageName, {
    defaultToken: "",

    keywords: ["MISSING", "TRUE", "FALSE"],

    brackets: [
      { open: "[", close: "]", token: "delimiter.bracket" },
      { open: "(", close: ")", token: "delimiter.parenthesis" },
    ],

    tokenizer: {
      root: [
        { include: "@whitespace" },
        { include: "@comments" },
        { include: "@numbers" },
        { include: "@columnAccess" },
        { include: "@rowInformationAccess" },
        { include: "@strings" },

        [/MISSING/, "constant"],
        [/TRUE/, "keyword.true"],
        [/FALSE/, "keyword.false"],
        [/[[\]()]/, "@brackets"],

        [
          /[a-zA-Z]\w*/,
          {
            cases: {
              "@keywords": "keyword",
              "@default": "identifier",
            },
          },
        ],
      ],

      whitespace: [[/\s+/, "white"]],

      comments: [[/(^#.*$)/, "comment"]],

      // Recognize decimals, exponents and ints
      numbers: [
        [/\.[0-9_]+(e-?\d+)?/, "number.float"], // no pre-decimal
        [/[0-9_]+\.(e-?\d+)?/, "number.float"], // no post-decimal
        [/[0-9_]+\.[0-9_]+(e-?\d+)?/, "number.float"], // pre+post decimal
        [/[0-9_]+(e-?\d+)?/, "number.float"], // scientific notation w/o deceimal point
        [/[0-9_]+/, "number.int"], // int
      ],

      strings: [
        [/'/, "string.escape", "@stringBody"],
        [/"/, "string.escape", "@dblStringBody"],
      ],
      stringBody: [
        [/[^\\']+/, "string"],
        [/\\./, "string.escape"],
        [/'/, "string.escape", "@popall"],
      ],
      dblStringBody: [
        [/[^\\"]+/, "string"],
        [/\\./, "string.escape"],
        [/"/, "string.escape", "@popall"],
      ],
      columnAccess: [
        [
          /\${1,2}\[\s*"/,
          "string.colname.escape",
          "@columnAccessBodyDoubleQuotes",
        ],
        [
          /\${1,2}\[\s*'/,
          "string.colname.escape",
          "@columnAccessBodySingleQuotes",
        ],
        [
          /\${1,2}(?=[A-Za-z_])/,
          "string.colname.escape",
          "@columnAccessBodyNoQuotes",
        ],
      ],
      rowInformationAccess: [
        [
          /\$\[(?!\[['"])/,
          "string.rowinfo.escape",
          "@rowInformationAccessBody",
        ],
      ],
      columnAccessBodySingleQuotes: [
        // seems like we have to make this a type of string in order to override bracket colouration
        [/[^\\']+/, "string.colname"],
        [/\\./, "string.colname"],
        // Match an end quote iff it is followed by a comma, and jump to the column offset rule
        [/'(?=\s*,)/, "string.colname.escape", "@columnOffsetSeparator"],
        // Otherwise, match an end quote followed by a bracket
        [/'\s*]/, "string.colname.escape", "@popall"],
      ],
      columnAccessBodyDoubleQuotes: [
        [/[^\\"]+/, "string.colname"],
        [/\\./, "string.colname"],
        // Match an end quote iff it is followed by a comma, and jump to the column offset rule
        [/"(?=\s*,)/, "string.colname.escape", "@columnOffsetSeparator"],
        // Otherwise, match an end quote followed by a bracket
        [/"\s*]/, "string.colname.escape", "@popall"],
      ],
      columnAccessBodyNoQuotes: [
        [/[A-Za-z_]\w*/, "string.colname", "@popall"],
        [/./, "", "@popall"],
      ],
      columnOffsetSeparator: [[/\s*,\s*/, "", "@columnOffset"]],
      columnOffset: [[/[\d_]+/, "number.coloffset", "@columnAccessTerminator"]],
      columnAccessTerminator: [[/\s*\]/, "string.colname.escape", "@popall"]],
      rowInformationAccessBody: [
        [/ROW_ID/, "string.rowinfo"],
        [/ROW_INDEX/, "string.rowinfo"],
        [/ROW_NUMBER/, "string.rowinfo"],
        [/\]/, "string.rowinfo.escape", "@popall"],
      ],
    },
  });

  const completionItemProvider = registerCompletionItemProvider({
    columnGetter,
    flowVariableGetter,
    functionData,
    languageName,
  });

  monaco.languages.setLanguageConfiguration(languageName, {
    comments: {
      lineComment: "#",
    },
    brackets: [
      ["[", "]"],
      ["(", ")"],
    ],
    autoClosingPairs: [
      { open: "[", close: "]" },
      { open: "(", close: ")" },
      { open: '"', close: '"' },
      { open: "'", close: "'" },
    ],
    folding: {
      offSide: true,
    },
  });

  monaco.editor.defineTheme(languageName, {
    base: "vs",
    inherit: true,
    rules: [
      { token: "string.colname.escape", foreground: "3289ac" },
      { token: "string.colname", foreground: "af01db" },
      { token: "number.coloffset", foreground: "af01db" },
      { token: "string.rowinfo.escape", foreground: "3289ac" },
      { token: "string.rowinfo", foreground: "af01db" },
    ],
    colors: {},
  });
  monaco.editor.setTheme("knime-expression");

  const hoverProvider = monaco.languages.registerHoverProvider(languageName, {
    provideHover: (model, position) => {
      const isIdentifierOrKeyword = () => {
        const lineContent = model.getLineContent(position.lineNumber);
        const tokens = monaco.editor.tokenize(lineContent, languageName);
        if (!tokens || tokens.length === 0) {
          return false;
        }

        let token: monaco.Token | null = null;
        for (const currentToken of tokens[0]) {
          if (currentToken.offset >= position.column) {
            break;
          }
          token = currentToken;
        }

        return (
          token !== null &&
          (token.type.includes("identifier") || token.type.includes("keyword"))
        );
      };

      if (!isIdentifierOrKeyword()) {
        return null;
      }

      const word = model.getWordAtPosition(position);
      if (!word) {
        return null;
      }

      const func = functionData.find((f) => f.name === word.word);
      if (func) {
        const markdownContent = functionDataToMarkdown(func);
        return {
          contents: [{ value: markdownContent }],
        };
      }

      return null;
    },
  });

  return () => {
    completionItemProvider.dispose();
    hoverProvider.dispose();
  };
};

/**
 * Set up the knime-expression language and register it with Monaco directly from initial data.
 *
 * @param initialData the initial data from the initialData service to use for
 * the autocompletion.
 * @param options options for the autocompletion.
 *
 * @returns a dispose function for the autocompletion. If you register the
 *          language multiple times, e.g. to add/remove autocompletion
 *          suggestions, call this.
 */
const registerKnimeExpressionLanguage = ({
  columnGetter = () => [],
  flowVariableGetter = () => [],
  functionData = [],
}: {
  columnGetter?: () => SubItem<Record<string, any>>[];
  flowVariableGetter?: () => SubItem<Record<string, any>>[];
  functionData?: FunctionCatalogEntryData[];
  languageName?: string;
} = {}) => {
  return register({
    columnGetter,
    flowVariableGetter,
    functionData,
    languageName: LANGUAGE,
  });
};

export default registerKnimeExpressionLanguage;
