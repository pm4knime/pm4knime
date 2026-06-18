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
  getSettingsService,
  setActiveEditorStoreForAi,
  useReadonlyStore,
} from "@knime/scripting-editor";
import PlusIcon from "@knime/styles/img/icons/circle-plus.svg";
import type { SettingState } from "@knime/ui-extension-service";

import { LANGUAGE } from "@/common/constants";
import { registerInsertionListener } from "@/common/functions";
import ExpressionEditorPane, {
  type ExpressionEditorPaneExposes,
} from "@/components/ExpressionEditorPane.vue";
import type { ExpressionReturnType } from "@/flowVariableApp/flowVariableTypes";
import type { EditorErrorState } from "@/generalDiagnostics";
import type { StateRule } from "@/stateAwareOcelApp/stateAwareOcelTypes";

type RuleEditorStateWithoutMonaco = {
  stateName: string;
  editorErrorState: EditorErrorState;
  stateNameErrorState: EditorErrorState;
  expressionReturnType: SubItemType<ExpressionReturnType>;
};

export type RuleEditorState = RuleEditorStateWithoutMonaco & {
  monacoState: UseCodeEditorReturn;
  key: string;
};

export type RuleEditorStates = {
  states: RuleEditorState[];
  activeEditorKey: string | null;
};

export type StateRuleExpressionContainerExposes = {
  getOrderedRuleStates: () => RuleEditorState[];
  getRuleEditorStates: () => RuleEditorStates;
  setEditorErrorState: (key: string, errorState: EditorErrorState) => void;
  setStateNameErrorState: (key: string, errorState: EditorErrorState) => void;
  setCurrentExpressionReturnType: (
    key: string,
    returnType: SubItemType<ExpressionReturnType>,
  ) => void;
  focusEditor: (key: string) => void;
  replaceRules: (rules: StateRule[]) => Promise<void>;
};

const props = defineProps<{
  rules: StateRule[];
}>();

const emit = defineEmits<{
  "on-change": [RuleEditorStates];
}>();

const readOnly = useReadonlyStore();
const activeEditorKey = ref<string | null>(null);
const orderedEditorKeys = reactive<string[]>([]);
const numberOfEditors = computed(() => orderedEditorKeys.length);
const editorReferences = reactive<Record<string, ExpressionEditorPaneExposes>>({});
const editorStates = reactive<Record<string, RuleEditorStateWithoutMonaco>>({});
const editorWatchers = reactive<Record<string, WatchHandle[]>>({});
const changedState = ref<SettingState<string> | null>(null);

const generateNewKey = () => uuidv4();

const getRuleStatesWithMonacoState = (): RuleEditorState[] =>
  orderedEditorKeys.map((key) => ({
    ...editorStates[key],
    monacoState: editorReferences[key].getEditorState(),
    key,
  }));

const getRuleEditorStates = (): RuleEditorStates => ({
  states: getRuleStatesWithMonacoState(),
  activeEditorKey: activeEditorKey.value,
});

const getChangeState = () =>
  orderedEditorKeys
    .map(
      (key) =>
        `${key}\n${editorStates[key]?.stateName ?? ""}\n${
          editorReferences[key]?.getEditorState().text.value ?? ""
        }`,
    )
    .join("\n---\n");

const emitOnChange = () => {
  changedState.value?.setValue(getChangeState());
  emit("on-change", getRuleEditorStates());
};

const setActiveEditor = (key: string) => {
  if (activeEditorKey.value === key || typeof editorReferences[key] === "undefined") {
    return;
  }
  activeEditorKey.value = key;
  setActiveEditorStoreForAi(editorReferences[key].getEditorState());
  emitOnChange();
};

const focusEditor = (key: string) =>
  editorReferences[key]?.focusEditorProgrammatically();

const createInitialState = (stateName = "New State") => ({
  stateName,
  editorErrorState: { level: "OK" } as EditorErrorState,
  stateNameErrorState: { level: "OK" } as EditorErrorState,
  expressionReturnType: { displayName: "UNKNOWN" } as SubItemType<ExpressionReturnType>,
});

const createEditorRefPusher = (key: string) => {
  return (el: Element | ComponentPublicInstance | null) => {
    if (key in editorReferences || el === null) {
      return;
    }
    const editorRef = el as unknown as ExpressionEditorPaneExposes;
    editorReferences[key] = editorRef;
    editorWatchers[key] = [
      watch(() => editorStates[key].stateName, emitOnChange),
      watch(editorRef.getEditorState().text, emitOnChange),
    ];
  };
};

const addRuleBelow = async (keyAbove: string, rule?: StateRule) => {
  const key = generateNewKey();
  const insertionIndex =
    keyAbove === "" ? orderedEditorKeys.length : orderedEditorKeys.indexOf(keyAbove) + 1;
  orderedEditorKeys.splice(insertionIndex, 0, key);
  editorStates[key] = createInitialState(rule?.stateName ?? "New State");
  await nextTick();
  editorReferences[key].getEditorState().setInitialText(rule?.expression ?? "true");
  return key;
};

const addRuleAtBottom = async () => {
  const key = await addRuleBelow(orderedEditorKeys[orderedEditorKeys.length - 1] ?? "");
  nextTick().then(() => focusEditor(key));
};

const removeRule = (key: string) => {
  if (numberOfEditors.value === 1 || readOnly.value) {
    return;
  }
  const index = orderedEditorKeys.indexOf(key);
  orderedEditorKeys.splice(index, 1);
  editorWatchers[key]?.forEach((stop) => stop());
  delete editorWatchers[key];
  delete editorStates[key];
  delete editorReferences[key];
  if (key === activeEditorKey.value) {
    const nextKey = orderedEditorKeys[Math.min(index, orderedEditorKeys.length - 1)];
    nextTick().then(() => setActiveEditor(nextKey));
  } else {
    emitOnChange();
  }
};

const moveRuleUp = (key: string) => {
  const index = orderedEditorKeys.indexOf(key);
  if (index <= 0 || readOnly.value) {
    return;
  }
  [orderedEditorKeys[index - 1], orderedEditorKeys[index]] = [
    orderedEditorKeys[index],
    orderedEditorKeys[index - 1],
  ];
  emitOnChange();
};

const moveRuleDown = (key: string) => {
  const index = orderedEditorKeys.indexOf(key);
  if (index >= orderedEditorKeys.length - 1 || readOnly.value) {
    return;
  }
  [orderedEditorKeys[index], orderedEditorKeys[index + 1]] = [
    orderedEditorKeys[index + 1],
    orderedEditorKeys[index],
  ];
  emitOnChange();
};

const copyRuleBelow = async (key: string) => {
  if (readOnly.value) {
    return;
  }
  const newKey = await addRuleBelow(key, {
    stateName: `${editorStates[key].stateName} copy`,
    expression: editorReferences[key].getEditorState().text.value,
  });
  nextTick().then(() => focusEditor(newKey));
};

const getMostConcerningErrorState = (key: string): EditorErrorState => {
  const state = editorStates[key];
  if (state.editorErrorState.level === "ERROR") {
    return state.editorErrorState;
  }
  if (state.stateNameErrorState.level === "ERROR") {
    return state.stateNameErrorState;
  }
  if (state.editorErrorState.level === "WARNING") {
    return state.editorErrorState;
  }
  if (state.stateNameErrorState.level === "WARNING") {
    return state.stateNameErrorState;
  }
  return state.editorErrorState;
};

const replaceRules = async (rules: StateRule[]) => {
  [...orderedEditorKeys].forEach((key) => {
    editorWatchers[key]?.forEach((stop) => stop());
    delete editorWatchers[key];
    delete editorStates[key];
    delete editorReferences[key];
  });
  orderedEditorKeys.splice(0, orderedEditorKeys.length);
  const nextRules = rules.length ? rules : [{ stateName: "Normal", expression: "true" }];
  await nextTick();
  for (const rule of nextRules) {
    const key = generateNewKey();
    orderedEditorKeys.push(key);
    editorStates[key] = createInitialState(rule.stateName);
  }
  await nextTick();
  orderedEditorKeys.forEach((key, index) => {
    editorReferences[key].getEditorState().setInitialText(nextRules[index]?.expression ?? "true");
  });
  if (orderedEditorKeys.length) {
    setActiveEditor(orderedEditorKeys[0]);
  }
  emitOnChange();
};

defineExpose<StateRuleExpressionContainerExposes>({
  getOrderedRuleStates: getRuleStatesWithMonacoState,
  getRuleEditorStates,
  setEditorErrorState(key, errorState) {
    if (editorStates[key]) {
      editorStates[key].editorErrorState = errorState;
    }
  },
  setStateNameErrorState(key, errorState) {
    if (editorStates[key]) {
      editorStates[key].stateNameErrorState = errorState;
    }
  },
  setCurrentExpressionReturnType(key, returnType) {
    if (editorStates[key]) {
      editorStates[key].expressionReturnType = returnType;
    }
  },
  focusEditor,
  replaceRules,
});

registerInsertionListener(() =>
  activeEditorKey.value === null ? null : editorReferences[activeEditorKey.value],
);

onKeyStroke("Enter", (evt: KeyboardEvent) => {
  const anyEditorFocused = Object.values(editorReferences).some((ref) =>
    ref.getEditorState().editor.value?.hasTextFocus(),
  );
  if (evt.shiftKey && anyEditorFocused) {
    evt.preventDefault();
    emitOnChange();
  }
});

onMounted(async () => {
  const initialRules = props.rules.length
    ? props.rules
    : [{ stateName: "Normal", expression: "true" }];
  initialRules.forEach((rule) => {
    const key = generateNewKey();
    orderedEditorKeys.push(key);
    editorStates[key] = createInitialState(rule.stateName);
  });
  await nextTick();
  orderedEditorKeys.forEach((key, index) => {
    editorReferences[key].getEditorState().setInitialText(initialRules[index].expression);
  });
  const register = await getSettingsService().registerSettings("model");
  changedState.value = register({ initialValue: getChangeState() });
  if (orderedEditorKeys.length) {
    setActiveEditor(orderedEditorKeys[0]);
  }
});
</script>

<template>
  <div class="state-rule-editor-container">
    <ExpressionEditorPane
      v-for="(key, index) in orderedEditorKeys"
      :key="key"
      :ref="createEditorRefPusher(key)"
      :title="`State rule ${index + 1}: ${editorStates[key]?.stateName || 'Unnamed state'}`"
      :file-name="key"
      :language="LANGUAGE"
      :ordering-options="{
        isFirst: index === 0,
        isLast: index === numberOfEditors - 1,
        isOnly: numberOfEditors === 1,
        isActive: activeEditorKey === key,
      }"
      :error-state="getMostConcerningErrorState(key)"
      @focus="setActiveEditor(key)"
      @delete="removeRule"
      @move-down="moveRuleDown"
      @move-up="moveRuleUp"
      @copy-below="copyRuleBelow"
    >
      <template #multi-editor-controls>
        <label class="state-name-control">
          <span>State name</span>
          <input
            v-model="editorStates[key].stateName"
            :class="{ invalid: editorStates[key].stateNameErrorState.level === 'ERROR' }"
            :disabled="readOnly"
            placeholder="e.g. Understock"
            @focus="setActiveEditor(key)"
          />
        </label>
        <span class="rule-hint">
          First matching rule wins. This expression must evaluate to BOOLEAN.
        </span>
      </template>
    </ExpressionEditorPane>

    <FunctionButton
      class="add-state-button"
      :disabled="readOnly"
      @click="addRuleAtBottom"
    >
      <PlusIcon /><span>Add state rule</span>
    </FunctionButton>
  </div>
</template>

<style lang="postcss">
@import url("@knime/styles/css");
</style>

<style scoped lang="postcss">
.state-rule-editor-container {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  overflow: hidden scroll;
}

.state-name-control {
  display: flex;
  gap: var(--space-8);
  align-items: center;
  padding: var(--space-8) var(--space-12);
  font-size: 13px;
}

.state-name-control span {
  min-width: 88px;
  font-weight: 500;
}

.state-name-control input {
  min-width: 220px;
  padding: 4px 6px;
  border: 1px solid var(--knime-silver-sand);
  border-radius: 2px;
  font: inherit;
}

.state-name-control input.invalid {
  border-color: var(--knime-coral-dark);
}

.rule-hint {
  display: inline-flex;
  align-items: center;
  padding: 0 var(--space-12) var(--space-8);
  font-size: 11px;
  color: var(--knime-dove-gray);
}

.add-state-button {
  width: fit-content;
  margin: var(--space-16) auto;
  outline: 1px solid var(--knime-silver-sand);
}
</style>
