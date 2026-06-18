import type { GenericInitialData } from "@knime/scripting-editor";

import type { AllowedDropDownValue } from "@/components/OutputSelector.vue";
import type { FunctionCatalogData } from "@/components/functionCatalogTypes";

export type OutputInsertionMode = "APPEND" | "REPLACE_EXISTING";

export type ExpressionVersion = {
  languageVersion: number;
  builtinFunctionsVersion: number;
  builtinAggregationsVersion: number;
};

export type GenericExpressionInitialData = GenericInitialData & {
  expressionVersion: ExpressionVersion;
  functionCatalog: FunctionCatalogData;
};

export type FlowVariableInitialData = GenericExpressionInitialData; // NOSONAR this type is here for consistency

export type RowMapperInitialData = GenericExpressionInitialData & {
  columnNamesAndTypes: AllowedDropDownValue[];
};

export type RowFilterInitialData = GenericExpressionInitialData; // NOSONAR this type is here for consistency
