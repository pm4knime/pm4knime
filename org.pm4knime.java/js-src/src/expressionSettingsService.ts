import type { GenericNodeSettings } from "@knime/scripting-editor";
import { getSettingsService } from "@knime/scripting-editor";

import type { ExpressionVersion, OutputInsertionMode } from "@/common/types";
import type { FlowVariableType } from "@/flowVariableApp/flowVariableTypes";

export type ExpressionRowFilterNodeSettings = ExpressionVersion & {
  script: string;
  settingsAreOverriddenByFlowVariable?: boolean;
};

export type ExpressionRowMapperNodeSettings = ExpressionVersion & {
  scripts: string[];
  settingsAreOverriddenByFlowVariable?: boolean;
  outputModes: OutputInsertionMode[];
  createdColumns: string[];
  replacedColumns: string[];
};

export type ExpressionFlowVariableNodeSettings = ExpressionVersion & {
  scripts: string[];
  settingsAreOverriddenByFlowVariable?: boolean;
  flowVariableOutputModes: OutputInsertionMode[];
  createdFlowVariables: string[];
  replacedFlowVariables: string[];
  flowVariableReturnTypes: FlowVariableType[];
};

const getExpressionSettingsService = <T extends GenericNodeSettings>() => ({
  ...getSettingsService(),
  getSettings: (): T => getSettingsService().getSettings() as T,
  registerSettingsGetterForApply: (settingsGetter: () => T) =>
    getSettingsService().registerSettingsGetterForApply(settingsGetter),
});

export const getRowFilterSettingsService =
  getExpressionSettingsService<ExpressionRowFilterNodeSettings>;

export const getRowMapperSettingsService =
  getExpressionSettingsService<ExpressionRowMapperNodeSettings>;

export const getFlowVariableSettingsService =
  getExpressionSettingsService<ExpressionFlowVariableNodeSettings>;
