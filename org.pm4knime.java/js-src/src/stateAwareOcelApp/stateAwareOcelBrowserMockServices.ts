import type { StateAwareOCELInitialData, StateAwareOCELSettings } from "@/stateAwareOcelApp/stateAwareOcelTypes";

const settings: StateAwareOCELSettings = {
  languageVersion: 1,
  builtinFunctionsVersion: 1,
  builtinAggregationsVersion: 1,
  eventIdColumn: "event_id",
  objectIdColumn: "object_id",
  timestampColumn: "timestamp",
  activityColumn: "activity",
  stateColumn: "ocel_state",
  stateAwareActivityColumn: "state_aware_activity",
  defaultState: "Undefined",
  addTransitionEvents: true,
  coalesceTransitionEvents: false,
  transitionPrefix: "ST CHANGE ",
  transitionEventIdPrefix: "STC",
  transitionEpsilonSeconds: 0.001,
  stateNames: ["Understock", "Normal", "Overstock"],
  expressions: [
    '$["object_stock"] < $["object_safety_stock"]',
    '$["object_stock"] >= $["object_safety_stock"] and $["object_stock"] <= $["object_max_stock"]',
    '$["object_stock"] > $["object_max_stock"]',
  ],
};

const columnNamesAndTypes = [
  { id: "event_id", text: "event_id", type: { id: "STRING", text: "STRING" } },
  { id: "object_id", text: "object_id", type: { id: "STRING", text: "STRING" } },
  { id: "timestamp", text: "timestamp", type: { id: "LOCAL_DATE_TIME", text: "LOCAL_DATE_TIME" } },
  { id: "activity", text: "activity", type: { id: "STRING", text: "STRING" } },
  { id: "object_stock", text: "object_stock", type: { id: "FLOAT", text: "FLOAT" } },
  { id: "object_safety_stock", text: "object_safety_stock", type: { id: "FLOAT", text: "FLOAT" } },
  { id: "object_max_stock", text: "object_max_stock", type: { id: "FLOAT", text: "FLOAT" } },
];

const examples = [
  {
    name: "Inventory Min-Max states",
    description: "Classify each row into Understock, Normal, or Overstock.",
    rules: [
      { stateName: "Understock", expression: '$["object_stock"] < $["object_safety_stock"]' },
      {
        stateName: "Normal",
        expression:
          '$["object_stock"] >= $["object_safety_stock"] and $["object_stock"] <= $["object_max_stock"]',
      },
      { stateName: "Overstock", expression: '$["object_stock"] > $["object_max_stock"]' },
    ],
  },
];

const initialData: StateAwareOCELInitialData = {
  inputConnectionInfo: [{ status: "OK", isOptional: false }],
  inputObjects: [
    {
      name: "Input table",
      portType: "table",
      subItemCodeAliasTemplate: '$["{{ subItems.[0].name }}"]',
      subItems: columnNamesAndTypes.map((column) => ({
        name: column.text,
        type: { displayName: column.type.id },
      })),
    },
  ],
  flowVariables: { name: "Flow variables", portType: "flowVariable", subItems: [] },
  functionCatalog: { categories: [], functions: [] },
  columnNamesAndTypes,
  examples,
};

export default {
  initialData,
  settings,
  rpcServices: {
    ScriptingService: {
      getStateRuleDiagnostics: async (_stateNames: string[], expressions: string[]) =>
        expressions.map(() => ({ diagnostics: [], returnType: { displayName: "BOOLEAN" } })),
    },
  },
};
