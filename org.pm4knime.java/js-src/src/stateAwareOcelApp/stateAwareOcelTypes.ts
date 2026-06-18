import type { GenericInitialData, InputOutputModel, SubItemType } from "@knime/scripting-editor";

import type { AllowedDropDownValue } from "@/components/OutputSelector.vue";
import type { FunctionCatalogData } from "@/components/functionCatalogTypes";
import type { ExpressionReturnType } from "@/flowVariableApp/flowVariableTypes";
import type { ExpressionDiagnostic } from "@/generalDiagnostics";

export type ExpressionVersion = {
  languageVersion: number;
  builtinFunctionsVersion: number;
  builtinAggregationsVersion: number;
};

export type StateRule = {
  stateName: string;
  expression: string;
};

export type StateAwareOCELExample = {
  name: string;
  description: string;
  rules: StateRule[];
};

export type StateAwareOCELInitialData = GenericInitialData & {
  inputObjects: InputOutputModel[];
  flowVariables: InputOutputModel;
  functionCatalog: FunctionCatalogData;
  columnNamesAndTypes: AllowedDropDownValue[];
  examples: StateAwareOCELExample[];
};

export type StateAwareOCELSettings = ExpressionVersion & {
  eventIdColumn: string;
  objectIdColumn: string;
  timestampColumn: string;
  activityColumn: string;
  stateColumn: string;
  stateAwareActivityColumn: string;
  defaultState: string;
  addTransitionEvents: boolean;
  coalesceTransitionEvents: boolean;
  transitionPrefix: string;
  transitionEventIdPrefix: string;
  transitionEpsilonSeconds: number;
  stateNames: string[];
  expressions: string[];
  settingsAreOverriddenByFlowVariable?: boolean;
};

export type StateExpressionDiagnosticResult = {
  diagnostics: ExpressionDiagnostic[];
  returnType: SubItemType<ExpressionReturnType>;
};
