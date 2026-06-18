import type {
  FunctionCatalogData,
  FunctionCatalogEntryData,
} from "@/components/functionCatalogTypes";

export type CatalogData = Record<string, FunctionCatalogEntryData[]>;
export const mapFunctionCatalogData = (
  data: FunctionCatalogData,
  maxArgs: number = 2,
): CatalogData => {
  const catalog: CatalogData = {};

  for (const category of data.categories) {
    catalog[category.fullName] = [];
  }

  for (const func of data.functions) {
    let functionEntryData: FunctionCatalogEntryData;

    if (func.entryType === "function") {
      const ellipseOrNot = func.arguments.length > 2 ? ", ..." : "";
      const firstTwoArgs = func.arguments
        .slice(0, maxArgs)
        .map((value) => {
          return value.vararg ? `${value.name}...` : value.name;
        })
        .join(", ");
      const fullArgs = func.arguments
        .map((value) => {
          return value.vararg ? `${value.name}...` : value.name;
        })
        .join(", ");
      functionEntryData = {
        ...func,
        displayName: `${func.name}(${firstTwoArgs}${ellipseOrNot})`,
        entryType: "function",
        displayNameWithFullArgs: `${func.name}(${fullArgs})`,
      };
    } else {
      functionEntryData = {
        ...func,
        entryType: "constant",
      };
    }

    catalog[functionEntryData.category].push(functionEntryData);
  }

  return catalog;
};
