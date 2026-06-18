import { type InitMockData } from "@knime/scripting-editor";
import { createScriptingServiceMock } from "@knime/scripting-editor/scripting-service-browser-mock";
import { createSettingsServiceMock } from "@knime/scripting-editor/settings-service-browser-mock";

import {
  FLOW_VARIABLES,
  FLOW_VARIABLE_INITIAL_DATA,
} from "@/__mocks__/mock-data";
import { log } from "@/common/functions";
import type { ExpressionFlowVariableNodeSettings } from "@/expressionSettingsService";

export const DEFAULT_FLOW_VARIABLE_INITIAL_SETTINGS: ExpressionFlowVariableNodeSettings =
  {
    scripts: ["mocked default script"],
    flowVariableOutputModes: ["APPEND"],
    createdFlowVariables: ["mocked output col"],
    replacedFlowVariables: [FLOW_VARIABLES.subItems![1].name],
    flowVariableReturnTypes: ["String"],
    languageVersion: 1,
    builtinFunctionsVersion: 1,
    builtinAggregationsVersion: 1,
  };

export default {
  scriptingService: createScriptingServiceMock({
    sendToServiceMockResponses: {
      runFlowVariableExpression: (options: any[] | undefined) => {
        log("runFlowVariableExpression", options);
        return Promise.resolve();
      },
      getFlowVariableDiagnostics: () => Promise.resolve([[]]),
    },
  }),
  settingsService: createSettingsServiceMock({
    settings: DEFAULT_FLOW_VARIABLE_INITIAL_SETTINGS,
  }),
  initialData: FLOW_VARIABLE_INITIAL_DATA,
  displayMode: "large",
} satisfies InitMockData;
