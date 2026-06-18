<script setup lang="ts">
import {
  type ComponentPublicInstance,
  type WatchHandle,
  computed,
  nextTick,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { onKeyStroke } from "@vueuse/core";
import { v4 as uuidv4 } from "uuid";

import { FunctionButton } from "@knime/components";
import {
  type SubItemType,
  type UseCodeEditorReturn,
  getScriptingService,
  getSettingsService,
  setActiveEditorStoreForAi,
  useReadonlyStore,
} from "@knime/scripting-editor";
import NextIcon from "@knime/styles/img/icons/arrow-next.svg";
import PlusIcon from "@knime/styles/img/icons/circle-plus.svg";
import type { SettingState } from "@knime/ui-extension-service";

import { LANGUAGE } from "@/common/constants";
import { registerInsertionListener } from "@/common/functions";
import ExpressionEditorPane, {
  type ExpressionEditorPaneExposes,
} from "@/components/ExpressionEditorPane.vue";
import OutputSelector, {
  type AllowedDropDownValue,
  type IdAndText,
  type ItemType,
  type SelectorState,
} from "@/components/OutputSelector.vue";
import ReturnTypeSelector from "@/components/ReturnTypeSelector.vue";
import {
  type ExpressionReturnType,
  type FlowVariableType,
  getDropDownValuesForCurrentType,
} from "@/flowVariableApp/flowVariableTypes";
import type {
  EditorErrorState,
  ExpressionDiagnostic,
} from "@/generalDiagnostics";

type EditorStateWithoutMonaco = {
  selectorState: SelectorState;
  editorErrorState: EditorErrorState;
  selectorErrorState: EditorErrorState;
  expressionReturnType: SubItemType<ExpressionReturnType>;
  selectedFlowVariableOutputType?: FlowVariableType;
};
export type EditorState = EditorStateWithoutMonaco & {
  monacoState: UseCodeEditorReturn;
  key: string;
};
export type EditorStates = {
  states: EditorState[];
  activeEditorKey: string | null;
};
type EditorStateWatchers = {
  columnStateUnwatchHandle: WatchHandle;
  editorStateUnwatchHandle: WatchHandle;
  flowVariableOutputTypeUnwatchHandle: WatchHandle;
};
export type MultiEditorContainerExposes = {
  getOrderedEditorStates: () => EditorState[];
  setEditorErrorState: (key: string, errorState: EditorErrorState) => void;
  setSelectorErrorState: (key: string, errorState: EditorErrorState) => void;
  setCurrentExpressionReturnType: (
    key: string,
    returnType: SubItemType<ExpressionReturnType>,
  ) => void;

  /**
   * Focus the editor with the given key such that the user can start typing
   * an expression into this editor. Scrolls the editor into view if necessary.
   * Note that an editor that has focus is also automatically the active editor.
   *
   * @param key The key of the editor to focus.
   */
  focusEditor: (key: string) => void;
};

const activeEditorFileName = ref<string | null>(null);

const props = defineProps<{
  settings: {
    initialScript: string;
    initialSelectorState: SelectorState;
    initialOutputReturnType?: FlowVariableType;
  }[];
  replaceableItemsInInputTable: AllowedDropDownValue[];
  defaultAppendItem: string;
  itemType: ItemType;
}>();

const getActiveEditorKey = (): string | null => activeEditorFileName.value;

/**
 * Generate a new key for a new editor. This is used to index the columnSelectorStates
 * and the multiEditorComponentRefs, plus it's used to keep track of the order of the editors.
 */
const generateNewKey = (): string => uuidv4();
const createEditorTitle = (index: number) => `Expression ${index + 1}`;

// The canonical source of ordering truth, used to index the editorStates.
// Things are run in the order defined here, saved in the order defined here, etc.
const orderedEditorKeys = reactive<string[]>([]);
const numberOfEditors = computed(() => orderedEditorKeys.length);

const editorReferences = reactive<{
  [key: string]: ExpressionEditorPaneExposes;
}>({});
const editorStates = reactive<{ [key: string]: EditorStateWithoutMonaco }>({});
const editorStateWatchers = reactive<{ [key: string]: EditorStateWatchers }>(
  {},
);
const editorControlsExpanded = ref<{ [key: string]: boolean }>({});

const getEditorStatesWithMonacoState = (): EditorState[] =>
  orderedEditorKeys.map((key) => ({
    ...editorStates[key],
    monacoState: editorReferences[key].getEditorState(),
    key,
  }));

const getEditorStates = (): EditorStates => ({
  states: getEditorStatesWithMonacoState(),
  activeEditorKey: getActiveEditorKey(),
});

/*
 * Get a minimal state representation of all changes that matter
 * for the settings service to evaluate if the current state is
 * different from the last saved settings state.
 */
const getState = () => {
  return orderedEditorKeys
    .map(
      (key) =>
        `${key} ${editorReferences[key].getEditorState().text.value}
    ${editorStates[key].selectorState.outputMode}
    ${editorStates[key].selectorState.create}
    ${editorStates[key].selectorState.replace}
    ${editorStates[key].selectedFlowVariableOutputType}`,
    )
    .join("\n");
};

const onChangedState = ref<SettingState<string> | null>(null);

const emit = defineEmits<{
  "on-change": [EditorStates];
  "run-expressions": [editorStates: EditorState[]];
}>();

const emitOnChange = () => {
  onChangedState.value?.setValue(getState());
  emit("on-change", getEditorStates());
};

/**
 * Sets the active editor to the editor with the given key. Note that this
 * does not set the focus.
 *
 * Called by the ExpressionEditorPane when an editor gains focus and by
 * onMounted to set the initial active editor.
 */
const setActiveEditor = (key: string) => {
  if (activeEditorFileName.value === key) {
    return;
  }
  activeEditorFileName.value = key;
  setActiveEditorStoreForAi(editorReferences[key].getEditorState());
  emitOnChange();
};

/**
 * Focus the editor with the given key. Note that the focus will trigger onFocusChanged,
 * which updates the active editor and emits the change event.
 *
 * @param key The key of the editor to focus.
 */
const focusEditor = (key: string) =>
  editorReferences[key].focusEditorProgrammatically();

defineExpose<MultiEditorContainerExposes>({
  getOrderedEditorStates: getEditorStatesWithMonacoState,
  setEditorErrorState(key: string, errorState: EditorErrorState) {
    editorStates[key].editorErrorState = errorState;
  },
  setSelectorErrorState(key: string, errorState: EditorErrorState) {
    editorStates[key].selectorErrorState = errorState;
  },
  setCurrentExpressionReturnType(
    key: string,
    returnType: SubItemType<ExpressionReturnType>,
  ) {
    editorStates[key].expressionReturnType = returnType;
  },
  focusEditor,
});

const pushNewEditorState = ({
  key,
  expandControls,
}: {
  key: string;
  expandControls: boolean;
}) => {
  const newState: EditorStateWithoutMonaco = {
    selectorState: {
      outputMode: "APPEND",
      create: props.defaultAppendItem,
      replace: "",
    },
    editorErrorState: { level: "OK" },
    selectorErrorState: { level: "OK" },
    expressionReturnType: { displayName: "UNKNOWN" },
  };
  editorControlsExpanded.value[key] = expandControls;
  editorStates[key] = newState;
};

/**
 * Called by components when they're created, creates a function that
 * stores a ref to the component in our dict.
 */
const createElementReferencePusher = (key: string) => {
  return (el: Element | ComponentPublicInstance | null) => {
    if (key in editorReferences || el === null) {
      // Otherwise we'll keep creating more and more watchers
      return;
    }

    const editorRef = el as unknown as ExpressionEditorPaneExposes;

    editorReferences[key] = editorRef;

    const newStateWatchers = {
      columnStateUnwatchHandle: watch(
        () => editorStates[key].selectorState,
        emitOnChange,
        { deep: true },
      ),
      editorStateUnwatchHandle: watch(
        editorRef.getEditorState().text,
        emitOnChange,
      ),
      flowVariableOutputTypeUnwatchHandle: watch(
        () => editorStates[key].selectedFlowVariableOutputType,
        emitOnChange,
      ),
    };
    editorStateWatchers[key] = newStateWatchers;
  };
};

const extractType = (editorState: EditorStateWithoutMonaco) =>
  editorState.expressionReturnType.id && editorState.expressionReturnType.title
    ? {
        id: editorState.expressionReturnType.id,
        text: editorState.expressionReturnType.title,
      }
    : null;

const getAppendedColumns = (index: number) =>
  orderedEditorKeys
    .slice(0, index)
    .filter((key) => editorStates[key].selectorState.outputMode === "APPEND")
    .map((key) => {
      const editorState = editorStates[key];
      const column = editorState.selectorState.create;
      const type = extractType(editorState);
      return type === null
        ? { id: column, text: column }
        : { id: column, text: column, type };
    });

const getReplacedColumns = (index: number) =>
  orderedEditorKeys
    .slice(0, index)
    .filter(
      (key) =>
        editorStates[key].selectorState.outputMode === "REPLACE_EXISTING",
    )
    .reduce<Record<string, IdAndText | null>>((acc, key) => {
      const editorState = editorStates[key];
      const column = editorState.selectorState.replace;
      const type = extractType(editorState);
      return { ...acc, [column]: type };
    }, {});

// Input columns helpers
const getAvailableColumnsForReplacement = (
  key: string,
): AllowedDropDownValue[] => {
  const index = orderedEditorKeys.indexOf(key);

  const appendedColumnsFromPreviousEditors = getAppendedColumns(index);
  const replacedColumnsFromPreviousEditors = getReplacedColumns(index);

  const replaceableItemsInInputTableWithAdaptedTypes =
    props.replaceableItemsInInputTable.map((item) => {
      const replacedType = replacedColumnsFromPreviousEditors[item.id];
      if (replacedType === undefined) {
        return item;
      }
      return replacedType === null
        ? { id: item.id, text: item.text }
        : { ...item, type: replacedType };
    });

  return [
    ...replaceableItemsInInputTableWithAdaptedTypes,
    ...appendedColumnsFromPreviousEditors,
  ]
    .filter((item) => {
      // Filter out flow variables starting with "knime" (reserved), but allow columns with that prefix
      const isReservedFlowVariable =
        props.itemType === "flow variable" && item.text.startsWith("knime");
      return !isReservedFlowVariable;
    })
    .filter((item) => item.text.trim() !== "");
};

/**
 * This function doesn't directly trigger an emit, because it is expected that
 * the caller will do so after the new editor is added.
 *
 * @param keyAbove
 */
const addNewEditorBelowExisting = async (keyAbove: string) => {
  const latestKey = generateNewKey();
  const desiredInsertionIndex = orderedEditorKeys.indexOf(keyAbove) + 1;
  orderedEditorKeys.splice(desiredInsertionIndex, 0, latestKey);

  pushNewEditorState({ key: latestKey, expandControls: true });

  // wait for the editor to be added to the DOM
  await nextTick();

  return latestKey;
};

const onRequestedAddEditorAtBottom = async () => {
  const latestKey = await addNewEditorBelowExisting(
    orderedEditorKeys[orderedEditorKeys.length - 1],
  );

  // focusEditor will emit the change event
  nextTick().then(() => focusEditor(latestKey));
};

const onEditorRequestedDelete = (key: string) => {
  if (numberOfEditors.value === 1) {
    return;
  }

  const indexOfDeletedEditor = orderedEditorKeys.indexOf(key);
  orderedEditorKeys.splice(indexOfDeletedEditor, 1);

  // Clean up state
  editorStateWatchers[key].columnStateUnwatchHandle();
  editorStateWatchers[key].editorStateUnwatchHandle();
  editorStateWatchers[key].flowVariableOutputTypeUnwatchHandle();
  delete editorStates[key];
  delete editorStateWatchers[key];
  delete editorReferences[key];
  delete editorControlsExpanded.value[key];

  if (key === getActiveEditorKey()) {
    const keyToFocus =
      orderedEditorKeys[
        Math.min(indexOfDeletedEditor, numberOfEditors.value - 1)
      ];

    // Now that the editor is gone, set the one below as active. Do not focus it.
    // setActiveEditor will emit the change event
    nextTick().then(() => setActiveEditor(keyToFocus));
  } else {
    // The active editor can stay the same but we need still need to emit the
    // change event
    emitOnChange();
  }
};

const onEditorRequestedMoveUp = (key: string) => {
  const index = orderedEditorKeys.indexOf(key);
  if (index <= 0) {
    return;
  }

  // Swap the entries at index and index - 1
  [orderedEditorKeys[index], orderedEditorKeys[index - 1]] = [
    orderedEditorKeys[index - 1],
    orderedEditorKeys[index],
  ];
  // Note: we do not change the focus
};

const onEditorRequestedMoveDown = (key: string) => {
  const index = orderedEditorKeys.indexOf(key);
  if (index >= orderedEditorKeys.length - 1) {
    return;
  }

  // Swap the entries at index and index + 1
  [orderedEditorKeys[index], orderedEditorKeys[index + 1]] = [
    orderedEditorKeys[index + 1],
    orderedEditorKeys[index],
  ];
  // Note: we do not change the focus
};

const onEditorRequestedCopyBelow = async (key: string) => {
  const newKey = await addNewEditorBelowExisting(key);

  // Copy state from the editor above to the new editor
  editorStates[newKey].selectorState = {
    ...editorStates[key].selectorState,
  };
  editorStates[newKey].expressionReturnType =
    editorStates[key].expressionReturnType;
  editorStates[newKey].selectedFlowVariableOutputType =
    editorStates[key].selectedFlowVariableOutputType;

  editorControlsExpanded.value[newKey] = true;

  editorReferences[newKey]
    .getEditorState()
    .setInitialText(editorReferences[key].getEditorState().text.value);

  // focusing the editor will cause the change event to be emitted
  // focusEditor will emit the change event
  nextTick().then(() => focusEditor(newKey));
};

registerInsertionListener(() =>
  getActiveEditorKey() === null
    ? null
    : editorReferences[getActiveEditorKey()!],
);

// Shift+Enter while editor has focus runs expressions
onKeyStroke("Enter", (evt: KeyboardEvent) => {
  const atLeastOneEditorHasFocus = Object.values(editorReferences).some(
    (state) => state.getEditorState().editor.value?.hasTextFocus(),
  );

  const allEditorsAreOk = Object.values(editorStates).every(
    (state) => state.editorErrorState.level === "OK",
  );

  if (evt.shiftKey && atLeastOneEditorHasFocus && allEditorsAreOk) {
    evt.preventDefault();
    emit("run-expressions", getEditorStatesWithMonacoState());
  }
});

const getMostConcerningErrorStateForEditor = (
  key: string,
): EditorErrorState => {
  const editorState = editorStates[key];
  if (editorState?.editorErrorState.level === "ERROR") {
    return editorState.editorErrorState;
  }
  if (editorState?.selectorErrorState.level === "ERROR") {
    return editorState.selectorErrorState;
  }
  if (editorState?.editorErrorState.level === "WARNING") {
    return editorState.editorErrorState;
  }
  if (editorState?.selectorErrorState.level === "WARNING") {
    return editorState.selectorErrorState;
  }
  return editorState?.editorErrorState;
};

const setWarningsHandler = (warnings: ExpressionDiagnostic[]) => {
  for (let i = 0; i < warnings.length; i++) {
    const warning = warnings[i];
    if (warning) {
      editorStates[orderedEditorKeys[i]].editorErrorState = {
        level: warning.severity,
        message: warnings[i].message,
      };
    }
  }
};

onMounted(async () => {
  for (let i = 0; i < props.settings.length; ++i) {
    const key = generateNewKey();
    orderedEditorKeys.push(key);

    // Expand controls if there's only one editor, collapse otherwise
    pushNewEditorState({ key, expandControls: props.settings.length === 1 });
  }

  await nextTick();

  getScriptingService().registerEventHandler(
    "updateWarnings",
    setWarningsHandler,
  );

  for (let i = 0; i < orderedEditorKeys.length; ++i) {
    const key = orderedEditorKeys[i];
    editorReferences[key]
      .getEditorState()
      .setInitialText(props.settings[i].initialScript);
    editorStates[key].selectorState = props.settings[i].initialSelectorState;
    editorStates[key].expressionReturnType = { displayName: "UNKNOWN" };
    editorStates[key].selectedFlowVariableOutputType =
      props.settings[i].initialOutputReturnType;
  }

  const register = await getSettingsService().registerSettings("model");
  onChangedState.value = register({ initialValue: getState() });

  // Note that we do call focusEditor here, because we do not want to steal focus
  // setActiveEditor emits the change event, so we don't need to do it here.
  setActiveEditor(orderedEditorKeys[0]);
});

const handleToggleExpand = (key: string) =>
  (editorControlsExpanded.value[key] = !editorControlsExpanded.value[key]);

const getOutputLabel = (key: string) => {
  const selectorState = editorStates[key].selectorState;
  const action = selectorState.outputMode === "APPEND" ? "creates" : "replaces";
  const target =
    selectorState.outputMode === "APPEND"
      ? selectorState.create
      : selectorState.replace;
  return `Output ${action} "${target}"`;
};
</script>

<template>
  <div class="multi-editor-container">
    <ExpressionEditorPane
      v-for="(key, index) in orderedEditorKeys"
      :key="key"
      :ref="createElementReferencePusher(key)"
      :title="createEditorTitle(index)"
      :file-name="key"
      :language="LANGUAGE"
      :ordering-options="{
        isFirst: index === 0,
        isLast: index === numberOfEditors - 1,
        isOnly: numberOfEditors === 1,
        isActive: activeEditorFileName === key,
      }"
      :error-state="getMostConcerningErrorStateForEditor(key)"
      @focus="setActiveEditor(key)"
      @delete="onEditorRequestedDelete"
      @move-down="onEditorRequestedMoveDown"
      @move-up="onEditorRequestedMoveUp"
      @copy-below="onEditorRequestedCopyBelow"
    >
      <!-- Controls displayed once per editor -->
      <template #multi-editor-controls>
        <button class="output-label" @click="() => handleToggleExpand(key)">
          <span
            class="output-expansion-icon"
            :class="{
              expanded: editorControlsExpanded[key],
            }"
          >
            <NextIcon />
          </span>
          {{ getOutputLabel(key) }}
        </button>
        <div
          class="editor-controls"
          :class="{
            hidden: !editorControlsExpanded[key],
          }"
        >
          <span class="output-settings-container">
            <OutputSelector
              v-model="editorStates[key].selectorState"
              :item-type="itemType"
              :is-valid="editorStates[key].selectorErrorState.level === 'OK'"
              :allowed-replacement-entities="
                getAvailableColumnsForReplacement(key)
              "
            />
            <span
              v-if="itemType === 'flow variable'"
              class="return-type-selector"
            >
              <ReturnTypeSelector
                v-model="editorStates[key].selectedFlowVariableOutputType"
                :allowed-return-types="
                  getDropDownValuesForCurrentType(
                    editorStates[key].expressionReturnType,
                  )
                "
              />
            </span>
          </span>
        </div>
      </template>
    </ExpressionEditorPane>

    <FunctionButton
      class="add-new-editor-button"
      data-testid="add-new-editor-button"
      :disabled="useReadonlyStore().value"
      @click="onRequestedAddEditorAtBottom"
    >
      <PlusIcon /><span>Add expression</span>
    </FunctionButton>
  </div>
</template>

<style lang="postcss">
@import url("@knime/styles/css");
</style>

<style lang="postcss" scoped>
.multi-editor-container {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  overflow: hidden scroll;
}

.add-new-editor-button {
  width: fit-content;
  margin: var(--space-16) auto;
  outline: 1px solid var(--knime-silver-sand);
}

.output-settings-container {
  display: flex;
  flex-direction: row;
  gap: var(--space-8);
  align-items: flex-end;
  width: 100%;
  margin-bottom: var(--space-8);
}

.editor-controls {
  width: 100%;
  max-height: 150px;
  padding: 0 var(--space-8);
  container-type: inline-size;
  opacity: 1;
  transition:
    max-height 0.4s ease-in-out,
    opacity 0.4s ease-in-out;

  &.hidden {
    /* Use visibility: hidden in addition to opacity to prevent
     * children from getting focus
     */
    visibility: hidden;
    max-height: 0;
    overflow: hidden;
    opacity: 0;

    /* When hiding, add an extra delayed transition for visibility */
    transition:
      opacity 0.4s ease-in-out,
      max-height 0.4s ease-in-out,
      visibility 0s step-end 0.4s;
  }
}

.return-type-selector {
  width: 100%;
  max-width: 400px;
}

.output-expansion-icon {
  width: 12px;
  height: 12px;
  stroke: var(--knime-masala);
  transition: transform 0.3s ease;

  & svg {
    stroke-width: 2px;
  }

  &.expanded {
    transform: rotate(90deg);
  }

  & :hover {
    background-color: var(--knime-stone-light);
    border-radius: 50%;
    stroke: var(--knime-cornflower);
  }
}

.output-label {
  all: unset; /* Removes default button styles */
  box-sizing: border-box;
  display: flex;
  flex-direction: row;
  gap: var(--space-4);
  width: 100%;
  padding: var(--space-8);
  font-size: 13px;
  font-weight: 500;
  line-height: 14px;
  text-align: left;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid var(--knime-cornflower);
    border-radius: var(--space-4);
  }
}

@container (width < 390px) {
  .output-settings-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-8);
    align-items: flex-end;
    width: 100%;
  }
}
</style>
