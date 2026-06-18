import type { SubItemType } from "@knime/scripting-editor";

import type { AllowedReturnTypes } from "@/components/ReturnTypeSelector.vue";

export type ExpressionReturnType =
  | "UNKNOWN"
  | "STRING"
  | "INTEGER"
  | "BOOLEAN"
  | "FLOAT";

export type FlowVariableType =
  | "String"
  | "Long"
  | "Integer"
  | "Boolean"
  | "Double"
  | "Unknown";

export const mapExpressionTypeToFlowVariableType = (
  type: SubItemType<ExpressionReturnType>,
): FlowVariableType => {
  switch (type.displayName) {
    case "STRING":
      return "String";
    case "INTEGER":
      return "Long";
    case "BOOLEAN":
      return "Boolean";
    case "FLOAT":
      return "Double";
    default:
      return "Unknown";
  }
};

export const getDropDownValuesForCurrentType = (
  type: SubItemType<ExpressionReturnType>,
): AllowedReturnTypes[] => {
  if (type.displayName === "INTEGER") {
    return [
      {
        id: "Long",
        text: "Long",
        type: {
          id: "variable-integer",
          text: "LongType",
        },
      },
      {
        id: "Integer",
        text: "Integer",
        type: {
          id: "variable-integer",
          text: "IntType",
        },
      },
    ];
  }

  const flowVariableType = mapExpressionTypeToFlowVariableType(type);
  return [
    {
      id: flowVariableType,
      text: flowVariableType,
      type: {
        id: type.id ?? "UNKNOWN",
        text: type.title ?? "Unknown variable type",
      },
    },
  ];
};
