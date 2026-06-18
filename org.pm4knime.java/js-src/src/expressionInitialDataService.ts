import { getInitialData } from "@knime/scripting-editor";

import type {
  FlowVariableInitialData,
  GenericExpressionInitialData,
  RowFilterInitialData,
  RowMapperInitialData,
} from "@/common/types";

const getExpressionInitialDataService = <
  T extends GenericExpressionInitialData,
>() => ({
  getInitialData: (): T => getInitialData() as T,
});

export const getRowMapperInitialDataService =
  getExpressionInitialDataService<RowMapperInitialData>;
export const getFlowVariableInitialDataService =
  getExpressionInitialDataService<FlowVariableInitialData>;
export const getRowFilterInitialDataService =
  getExpressionInitialDataService<RowFilterInitialData>;
