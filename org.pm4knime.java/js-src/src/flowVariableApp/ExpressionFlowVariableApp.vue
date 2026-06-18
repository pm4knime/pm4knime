<script setup lang="ts">
import { onMounted, ref } from "vue";

import {
  type InputOutputModel,
  InputOutputPane,
  ScriptingEditor,
  type SubItem,
  type SubItemType,
  getScriptingService,
  useReadonlyStore,
} from "@knime/scripting-editor";

import { INITIAL_PANE_SIZES, LANGUAGE } from "@/common/constants";
import {
  buildAppendedOutput,
  replaceSubItems,
} from "@/common/inputOutputUtils";
import type { ExpressionVersion } from "@/common/types";
import type { IconRendererProps } from "@/components/IconRenderer.vue";
import MultiEditorContainer, {
  type EditorState,
  type EditorStates,
} from "@/components/MultiEditorContainer.vue";
import type {
  AllowedDropDownValue,
  SelectorState,
} from "@/components/OutputSelector.vue";
import SimpleRunButton from "@/components/SimpleRunButton.vue";
import FunctionCatalog from "@/components/function-catalog/FunctionCatalog.vue";
import { getFlowVariableInitialDataService } from "@/expressionInitialDataService";
import {
  type ExpressionFlowVariableNodeSettings,
  getFlowVariableSettingsService,
} from "@/expressionSettingsService";
import OutputPreviewFlowVariable from "@/flowVariableApp/OutputPreviewFlowVariable.vue";
import { runFlowVariableDiagnostics } from "@/flowVariableApp/expressionFlowVariableDiagnostics";
import { runOutputDiagnostics } from "@/generalDiagnostics";
import registerKnimeExpressionLanguage from "@/languageSupport/registerKnimeExpressionLanguage";

// Input flowVariables helpers
const runButtonDisabledErrorReason = ref<string | null>(null);
const initialData = getFlowVariableInitialDataService().getInitialData();
const initialSettings = getFlowVariableSettingsService().getSettings();
const multiEditorContainerRef =
  ref<InstanceType<typeof MultiEditorContainer>>();

const currentInputOutputItems = ref<InputOutputModel[]>();

const appendedSubItems = ref<SubItem<Record<string, any>>[]>([]);

const getInitialItems = (): InputOutputModel[] => {
  return [structuredClone(initialData.flowVariables)];
};

const refreshInputOutputItems = (
  { states, activeEditorKey }: EditorStates,
  returnTypes: SubItemType[],
) => {
  if (!activeEditorKey) {
    return;
  }

  currentInputOutputItems.value = getInitialItems();

  const lastIndexToConsider = states.findIndex(
    (state) => state.key === activeEditorKey,
  );

  if (lastIndexToConsider === -1) {
    return;
  }

  const statesUntilActiveWithReturnTypes = states
    .slice(0, lastIndexToConsider)
    .map((state, index) => ({
      selectorState: state.selectorState,
      key: state.key,
      returnType: returnTypes[index],
    }));

  const focusEditorActionBuilder = (editorKey: string) => () => {
    multiEditorContainerRef.value?.focusEditor(editorKey);
  };

  currentInputOutputItems.value[0].subItems =
    currentInputOutputItems.value[0].subItems?.map((subItem) =>
      replaceSubItems(
        subItem as SubItem<IconRendererProps>,
        statesUntilActiveWithReturnTypes,
        focusEditorActionBuilder,
      ),
    );

  if (
    statesUntilActiveWithReturnTypes.some(
      (state) => state.selectorState.outputMode === "APPEND",
    )
  ) {
    const appendedInputOutputItem = buildAppendedOutput(
      statesUntilActiveWithReturnTypes,
      focusEditorActionBuilder,
      {
        name: "Created flow variables",
        portType: "flowVariable",
        subItemCodeAliasTemplate:
          currentInputOutputItems.value[0].subItemCodeAliasTemplate,
      },
    );

    currentInputOutputItems.value.push(appendedInputOutputItem);
    appendedSubItems.value = appendedInputOutputItem.subItems ?? [];
  } else {
    appendedSubItems.value = [];
  }
};

const runDiagnosticsFunction = async ({
  states,
}: EditorStates): Promise<SubItemType[]> => {
  const diagnostics = await runFlowVariableDiagnostics(
    states.map((state) => state.monacoState),
    states.map((state) =>
      state.selectorState.outputMode === "APPEND"
        ? state.selectorState.create
        : state.selectorState.replace,
    ),
  );

  const flowVariableErrorMessages = runOutputDiagnostics(
    "flow variable",
    states.map((state) => state.selectorState),
    initialData.flowVariables.subItems?.map((c) => c.name) ?? [],
  );

  for (const [index, state] of states.entries()) {
    if (flowVariableErrorMessages[index] === null) {
      multiEditorContainerRef.value?.setSelectorErrorState(state.key, {
        level: "OK",
      });
    } else {
      multiEditorContainerRef.value?.setSelectorErrorState(state.key, {
        level: "ERROR",
        message: flowVariableErrorMessages[index],
      });
    }

    multiEditorContainerRef.value?.setEditorErrorState(
      state.key,
      diagnostics[index].errorState,
    );

    multiEditorContainerRef.value?.setCurrentExpressionReturnType(
      state.key,
      diagnostics[index].returnType,
    );
  }

  const haveFlowVariableErrors = flowVariableErrorMessages.some(
    (error) => error !== null,
  );
  const haveSyntaxErrors = diagnostics.some(
    ({ errorState }) => errorState.level === "ERROR",
  );
  if (haveSyntaxErrors) {
    runButtonDisabledErrorReason.value =
      "To evaluate your expression, first resolve syntax errors.";
  } else if (haveFlowVariableErrors) {
    runButtonDisabledErrorReason.value =
      "To evaluate your expression, first resolve flow variable output errors.";
  } else {
    runButtonDisabledErrorReason.value = null;
  }

  return diagnostics.map(({ returnType }) => returnType);
};

const onChange = async (editorStates: EditorStates) => {
  const returnTypes = await runDiagnosticsFunction(editorStates);
  refreshInputOutputItems(editorStates, returnTypes);
};

onMounted(() => {
  registerKnimeExpressionLanguage({
    columnGetter: () => [],
    flowVariableGetter: () => [
      ...(currentInputOutputItems.value?.[0].subItems ?? []),
      ...appendedSubItems.value,
    ],
    functionData: initialData.functionCatalog.functions,
  });

  useReadonlyStore().value =
    initialSettings.settingsAreOverriddenByFlowVariable ?? false;

  currentInputOutputItems.value = getInitialItems();
});

const runFlowVariableExpressions = (states: EditorState[]) => {
  getScriptingService().sendToService("runFlowVariableExpression", [
    states.map((state) => state.monacoState.text.value),
    states.map((state) =>
      state.selectorState.outputMode === "APPEND"
        ? state.selectorState.create
        : state.selectorState.replace,
    ),
    states.map((state) => state.selectedFlowVariableOutputType ?? "Unknown"),
  ]);
};

getFlowVariableSettingsService().registerSettingsGetterForApply(
  (): ExpressionFlowVariableNodeSettings => {
    const orderedEditorStates =
      multiEditorContainerRef.value!.getOrderedEditorStates();

    const expressionVersion: ExpressionVersion = {
      languageVersion: initialSettings.languageVersion,
      builtinFunctionsVersion: initialSettings.builtinFunctionsVersion,
      builtinAggregationsVersion: initialSettings.builtinAggregationsVersion,
    };

    return {
      ...expressionVersion,
      flowVariableOutputModes: orderedEditorStates.map(
        (state) => state.selectorState.outputMode,
      ),
      createdFlowVariables: orderedEditorStates.map(
        (state) => state.selectorState.create,
      ),
      replacedFlowVariables: orderedEditorStates.map(
        (state) => state.selectorState.replace,
      ),
      flowVariableReturnTypes: orderedEditorStates.map(
        (state) => state.selectedFlowVariableOutputType ?? "Unknown",
      ),
      scripts: orderedEditorStates.map((state) => state.monacoState.text.value),
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
      :additional-bottom-pane-tab-content="[
        {
          label: 'Preview',
          slotName: 'bottomPaneTabSlot:outputPreview',
        },
      ]"
    >
      <!-- Extra content in the bottom tab pane -->
      <template #bottomPaneTabSlot:outputPreview="{ grabFocus }">
        <OutputPreviewFlowVariable @output-preview-updated="grabFocus()" />
      </template>

      <template #editor>
        <MultiEditorContainer
          ref="multiEditorContainerRef"
          item-type="flow variable"
          :default-append-item="'New Flow Variable'"
          :settings="
            initialSettings.scripts.map((script, index) => ({
              initialScript: script,
              initialSelectorState: {
                outputMode: initialSettings.flowVariableOutputModes[index],
                create: initialSettings.createdFlowVariables[index],
                replace: initialSettings.replacedFlowVariables[index],
              } satisfies SelectorState,
              initialOutputReturnType:
                initialSettings.flowVariableReturnTypes[index],
            }))
          "
          :replaceable-items-in-input-table="
            initialData.flowVariables.subItems
              ?.filter((c) => c.supported)
              .map(
                (c): AllowedDropDownValue => ({
                  id: c.name,
                  text: c.name,
                  ...(c.type.id &&
                    c.type.title && {
                      type: { id: c.type.id, text: c.type.title },
                    }),
                }),
              ) ?? []
          "
          @on-change="onChange"
          @run-expressions="runFlowVariableExpressions"
        />
      </template>

      <template #left-pane>
        <InputOutputPane :input-output-items="currentInputOutputItems" />
      </template>

      <template #right-pane>
        <template v-if="initialData.functionCatalog">
          <FunctionCatalog
            :function-catalog-data="initialData.functionCatalog"
            :initially-expanded="false"
          />
        </template>
      </template>

      <!-- Controls displayed once only -->
      <template #code-editor-controls="{ showButtonText }">
        <SimpleRunButton
          :run-button-disabled-error-reason="runButtonDisabledErrorReason"
          :show-button-text="showButtonText"
          @run-expressions="
            () =>
              runFlowVariableExpressions(
                multiEditorContainerRef!.getOrderedEditorStates(),
              )
          "
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

<style lang="postcss" scoped>
.no-editors {
  position: absolute;
  inset: calc(50% - 25px);
  width: 50px;
  height: 50px;
}
</style>
