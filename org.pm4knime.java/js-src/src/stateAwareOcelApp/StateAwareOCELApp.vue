<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";

import {
  type InputOutputModel,
  InputOutputPane,
  ScriptingEditor,
  consoleHandler,
  useReadonlyStore,
} from "@knime/scripting-editor";

import { INITIAL_PANE_SIZES, LANGUAGE } from "@/common/constants";
import { mapConnectionInfoToErrorMessage } from "@/common/functions";
import FunctionCatalog from "@/components/function-catalog/FunctionCatalog.vue";
import registerKnimeExpressionLanguage from "@/languageSupport/registerKnimeExpressionLanguage";
import StateRuleExpressionContainer, {
  type RuleEditorStates,
  type StateRuleExpressionContainerExposes,
} from "@/stateAwareOcelApp/StateRuleExpressionContainer.vue";
import { runStateRuleDiagnostics } from "@/stateAwareOcelApp/stateAwareOcelDiagnostics";
import {
  getStateAwareOCELInitialDataService,
  getStateAwareOCELSettingsService,
} from "@/stateAwareOcelApp/stateAwareOcelSettingsService";
import type {
  ExpressionVersion,
  StateAwareOCELExample,
  StateAwareOCELSettings,
  StateRule,
} from "@/stateAwareOcelApp/stateAwareOcelTypes";

const initialData = getStateAwareOCELInitialDataService().getInitialData();
const initialSettings = getStateAwareOCELSettingsService().getSettings();

const stateRuleContainerRef = ref<StateRuleExpressionContainerExposes>();
const currentInputOutputItems = ref<InputOutputModel[]>();
const configurationError = ref<string | null>(null);

const availableColumnChoices = computed(() =>
  (initialData.columnNamesAndTypes ?? []).map((choice) => choice.text ?? choice.id),
);

const outputSettings = ref({
  eventIdColumn: initialSettings.eventIdColumn,
  objectIdColumn: initialSettings.objectIdColumn,
  timestampColumn: initialSettings.timestampColumn,
  activityColumn: initialSettings.activityColumn,
  stateColumn: initialSettings.stateColumn,
  stateAwareActivityColumn: initialSettings.stateAwareActivityColumn,
  defaultState: initialSettings.defaultState,
  addTransitionEvents: initialSettings.addTransitionEvents,
  coalesceTransitionEvents: initialSettings.coalesceTransitionEvents,
  transitionPrefix: initialSettings.transitionPrefix,
  transitionEventIdPrefix: initialSettings.transitionEventIdPrefix,
  transitionEpsilonSeconds: initialSettings.transitionEpsilonSeconds,
});

const initialRules = computed<StateRule[]>(() =>
  initialSettings.stateNames.map((stateName, index) => ({
    stateName,
    expression: initialSettings.expressions[index] ?? "true",
  })),
);

const getInitialItems = (): InputOutputModel[] => [
  ...initialData.inputObjects.map((inputItem) => structuredClone(inputItem)),
  structuredClone(initialData.flowVariables),
];

const validateStateNames = (states: RuleEditorStates) => {
  const normalizedNames = states.states.map((state) => state.stateName.trim());
  const duplicateNames = new Set(
    normalizedNames.filter((name, index) => name && normalizedNames.indexOf(name) !== index),
  );

  states.states.forEach((state) => {
    if (!state.stateName.trim()) {
      stateRuleContainerRef.value?.setStateNameErrorState(state.key, {
        level: "ERROR",
        message: "State name must not be empty.",
      });
    } else if (duplicateNames.has(state.stateName.trim())) {
      stateRuleContainerRef.value?.setStateNameErrorState(state.key, {
        level: "ERROR",
        message: `State name '${state.stateName.trim()}' is used more than once.`,
      });
    } else {
      stateRuleContainerRef.value?.setStateNameErrorState(state.key, { level: "OK" });
    }
  });

  return normalizedNames.some((name) => !name) || duplicateNames.size > 0;
};

const validateColumnAndOutputSettings = () => {
  const selectedColumnFields = [
    ["Event ID column", outputSettings.value.eventIdColumn],
    ["Object ID column", outputSettings.value.objectIdColumn],
    ["Timestamp column", outputSettings.value.timestampColumn],
    ["Activity column", outputSettings.value.activityColumn],
  ];
  for (const [label, value] of selectedColumnFields) {
    if (!String(value).trim()) {
      return `${label} must be selected.`;
    }
    if (!availableColumnChoices.value.includes(String(value))) {
      return `${label} '${value}' is not available in the input table.`;
    }
  }

  const outputNames = [
    outputSettings.value.stateColumn,
    outputSettings.value.stateAwareActivityColumn,
    "is_state_change_event",
    "state_transition_from",
    "state_transition_to",
    "state_change_source_event_id",
    "state_change_affected_objects",
  ].map((name) => String(name).trim());

  if (outputNames.some((name) => !name)) {
    return "Output column names must not be empty.";
  }
  if (new Set(outputNames).size !== outputNames.length) {
    return "Output column names and reserved transition metadata columns must be unique.";
  }
  const inputCollisions = outputNames.filter((name) =>
    availableColumnChoices.value.includes(name),
  );
  if (inputCollisions.length > 0) {
    return `Output column '${inputCollisions[0]}' already exists in the input table.`;
  }
  if (
    Number.isNaN(Number(outputSettings.value.transitionEpsilonSeconds)) ||
    Number(outputSettings.value.transitionEpsilonSeconds) < 0
  ) {
    return "Transition epsilon must be a finite value greater than or equal to 0.";
  }
  if (!String(outputSettings.value.defaultState).trim()) {
    return "Default state must not be empty.";
  }
  return null;
};

const runDiagnostics = async (states: RuleEditorStates) => {
  const diagnostics = await runStateRuleDiagnostics(
    states.states.map((state) => state.monacoState),
    states.states.map((state) => state.stateName),
  );

  diagnostics.forEach(({ errorState, returnType }, index) => {
    const key = states.states[index].key;
    stateRuleContainerRef.value?.setEditorErrorState(key, errorState);
    stateRuleContainerRef.value?.setCurrentExpressionReturnType(key, returnType);
  });

  const hasStateNameErrors = validateStateNames(states);
  const hasSyntaxErrors = diagnostics.some(
    ({ errorState }) => errorState.level === "ERROR",
  );
  const connectionErrors = mapConnectionInfoToErrorMessage(
    initialData.inputConnectionInfo,
  );
  const configurationErrors = validateColumnAndOutputSettings();

  configurationError.value = connectionErrors ?? configurationErrors ??
    (hasSyntaxErrors
      ? "Resolve the expression errors before applying the configuration."
      : hasStateNameErrors
        ? "Resolve the state-name errors before applying the configuration."
        : null);
};

const onRulesChanged = (states: RuleEditorStates) => {
  runDiagnostics(states);
};

watch(
  outputSettings,
  () => {
    const states = stateRuleContainerRef.value?.getRuleEditorStates();
    if (states) {
      runDiagnostics(states);
    }
  },
  { deep: true },
);

const applyExample = async (example: StateAwareOCELExample) => {
  await stateRuleContainerRef.value?.replaceRules(example.rules);
};

onMounted(() => {
  if (initialData.inputConnectionInfo?.[0]?.status !== "OK") {
    consoleHandler.writeln({ warning: "No input available. Connect an executed table node." });
  }

  currentInputOutputItems.value = getInitialItems();
  registerKnimeExpressionLanguage({
    columnGetter: () => currentInputOutputItems.value?.[0].subItems ?? [],
    flowVariableGetter: () => [],
    functionData: initialData.functionCatalog.functions,
  });

  useReadonlyStore().value = initialSettings.settingsAreOverriddenByFlowVariable ?? false;
});

getStateAwareOCELSettingsService().registerSettingsGetterForApply(
  (): StateAwareOCELSettings => {
    const orderedStates = stateRuleContainerRef.value!.getOrderedRuleStates();
    const expressionVersion: ExpressionVersion = {
      languageVersion: initialSettings.languageVersion,
      builtinFunctionsVersion: initialSettings.builtinFunctionsVersion,
      builtinAggregationsVersion: initialSettings.builtinAggregationsVersion,
    };

    return {
      ...expressionVersion,
      eventIdColumn: outputSettings.value.eventIdColumn,
      objectIdColumn: outputSettings.value.objectIdColumn,
      timestampColumn: outputSettings.value.timestampColumn,
      activityColumn: outputSettings.value.activityColumn,
      stateColumn: outputSettings.value.stateColumn,
      stateAwareActivityColumn: outputSettings.value.stateAwareActivityColumn,
      defaultState: outputSettings.value.defaultState,
      addTransitionEvents: outputSettings.value.addTransitionEvents,
      coalesceTransitionEvents: outputSettings.value.coalesceTransitionEvents,
      transitionPrefix: outputSettings.value.transitionPrefix,
      transitionEventIdPrefix: outputSettings.value.transitionEventIdPrefix,
      transitionEpsilonSeconds: Number(outputSettings.value.transitionEpsilonSeconds),
      stateNames: orderedStates.map((state) => state.stateName),
      expressions: orderedStates.map((state) => state.monacoState.text.value),
    };
  },
);
</script>

<template>
  <main>
    <ScriptingEditor
      :show-control-bar="true"
      :language="LANGUAGE"
      :initial-pane-sizes="INITIAL_PANE_SIZES"
    >
      <template #editor>
        <section class="settings-panel">
          <h2>OCEL columns</h2>
          <div class="settings-grid">
            <label>
              Event ID column
              <select v-model="outputSettings.eventIdColumn">
                <option v-for="column in availableColumnChoices" :key="column" :value="column">
                  {{ column }}
                </option>
              </select>
            </label>
            <label>
              Object ID column
              <select v-model="outputSettings.objectIdColumn">
                <option v-for="column in availableColumnChoices" :key="column" :value="column">
                  {{ column }}
                </option>
              </select>
            </label>
            <label>
              Timestamp column
              <select v-model="outputSettings.timestampColumn">
                <option v-for="column in availableColumnChoices" :key="column" :value="column">
                  {{ column }}
                </option>
              </select>
            </label>
            <label>
              Activity column
              <select v-model="outputSettings.activityColumn">
                <option v-for="column in availableColumnChoices" :key="column" :value="column">
                  {{ column }}
                </option>
              </select>
            </label>
          </div>

          <h2>Generated columns</h2>
          <div class="settings-grid">
            <label>
              State column name
              <input v-model="outputSettings.stateColumn" />
            </label>
            <label>
              State-aware activity column name
              <input v-model="outputSettings.stateAwareActivityColumn" />
            </label>
            <label>
              Default state if no rule matches
              <input v-model="outputSettings.defaultState" />
            </label>
          </div>

          <details class="transition-details">
            <summary>Optional state transition events</summary>
            <div class="settings-grid transition-grid">
              <label class="checkbox-label">
                <input v-model="outputSettings.addTransitionEvents" type="checkbox" />
                Add explicit state transition rows
              </label>
              <label class="checkbox-label">
                <input v-model="outputSettings.coalesceTransitionEvents" type="checkbox" />
                Coalesce simultaneous equal transition labels
              </label>
              <label>
                Transition activity prefix
                <input v-model="outputSettings.transitionPrefix" />
              </label>
              <label>
                Transition event ID prefix
                <input v-model="outputSettings.transitionEventIdPrefix" />
              </label>
              <label>
                Epsilon seconds
                <input v-model.number="outputSettings.transitionEpsilonSeconds" type="number" min="0" step="0.001" />
              </label>
            </div>
          </details>

          <details class="examples-details">
            <summary>Insert example state rules</summary>
            <div class="examples">
              <article v-for="example in initialData.examples" :key="example.name">
                <div>
                  <strong>{{ example.name }}</strong>
                  <p>{{ example.description }}</p>
                </div>
                <button type="button" @click="applyExample(example)">Use example</button>
              </article>
            </div>
          </details>

          <p v-if="configurationError" class="configuration-error">
            {{ configurationError }}
          </p>
        </section>

        <StateRuleExpressionContainer
          ref="stateRuleContainerRef"
          :rules="initialRules"
          @on-change="onRulesChanged"
        />
      </template>

      <template #left-pane>
        <InputOutputPane :input-output-items="currentInputOutputItems" />
      </template>

      <template #right-pane>
        <FunctionCatalog
          v-if="initialData?.functionCatalog"
          :function-catalog-data="initialData.functionCatalog"
          :initially-expanded="false"
        />
      </template>
    </ScriptingEditor>
  </main>
</template>

<style lang="postcss">
@import url("@knime/styles/css");
@import url("@knime/kds-styles/kds-variables.css");
@import url("@knime/kds-styles/kds-legacy-theme.css");
</style>

<style scoped lang="postcss">
.settings-panel {
  margin: var(--space-12);
  padding: var(--space-12);
  background: var(--knime-porcelain);
  border: 1px solid var(--knime-silver-sand);
  box-shadow: var(--shadow-elevation-1);
}

.settings-panel h2 {
  margin: var(--space-8) 0;
  font-size: 14px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: var(--space-8) var(--space-16);
  margin-bottom: var(--space-8);
}

.settings-grid label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
}

.settings-grid input,
.settings-grid select {
  min-height: 26px;
  padding: 3px 6px;
  border: 1px solid var(--knime-silver-sand);
  border-radius: 2px;
  font: inherit;
  font-weight: 400;
  background: white;
}

.checkbox-label {
  flex-direction: row !important;
  align-items: center;
  font-weight: 400 !important;
}

.checkbox-label input {
  min-height: unset;
}

.transition-details,
.examples-details {
  margin-top: var(--space-8);
}

.transition-details summary,
.examples-details summary {
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
}

.transition-grid {
  margin-top: var(--space-8);
}

.examples {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-8);
  margin-top: var(--space-8);
}

.examples article {
  display: flex;
  gap: var(--space-8);
  justify-content: space-between;
  padding: var(--space-8);
  background: white;
  border: 1px solid var(--knime-silver-sand);
}

.examples p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--knime-dove-gray);
}

.examples button {
  align-self: center;
  white-space: nowrap;
}

.configuration-error {
  margin: var(--space-8) 0 0;
  font-size: 12px;
  color: var(--knime-coral-dark);
}
</style>
