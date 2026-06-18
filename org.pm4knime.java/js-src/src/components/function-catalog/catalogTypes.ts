import type { FunctionCatalogEntryData } from "@/components/functionCatalogTypes";

export type SelectableCategory = {
  type: "category";
  name: string;
};

export type SelectableFunction = {
  type: "function";
  functionData: FunctionCatalogEntryData;
};

export type SelectableItem = SelectableCategory | SelectableFunction;
