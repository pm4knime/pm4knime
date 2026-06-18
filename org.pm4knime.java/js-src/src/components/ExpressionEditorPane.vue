<script setup lang="ts">
import { type Component, computed, nextTick, onMounted, ref, watch } from "vue";
import { onKeyStroke } from "@vueuse/core";
import { editor as monaco } from "monaco-editor";

import { FunctionButton } from "@knime/components";
import {
  type UseCodeEditorReturn,
  editor,
  useReadonlyStore,
} from "@knime/scripting-editor";
import DownArrowIcon from "@knime/styles/img/icons/arrow-down.svg";
import UpArrowIcon from "@knime/styles/img/icons/arrow-up.svg";
import WarningIcon from "@knime/styles/img/icons/circle-warning.svg";
import CopyIcon from "@knime/styles/img/icons/copy.svg";
import TrashIcon from "@knime/styles/img/icons/trash.svg";

import { insertFunctionCall } from "@/common/functions";
import {
  resetDraggedFunctionStore,
  useDraggedFunctionStore,
} from "@/draggedFunctionStore";
import type { EditorErrorState } from "@/generalDiagnostics";

import EditorOption = monaco.EditorOption;

export type ExpressionEditorPaneExposes = {
  getEditorState: () => UseCodeEditorReturn;
  /**
   * Focus this editor. This will scroll the editor into view and set the
   * active cursor into the editor.
   */
  focusEditorProgrammatically: () => void;
};

const emit = defineEmits<{
  focus: [filname: string];
  "move-up": [filename: string];
  "move-down": [filename: string];
  delete: [filename: string];
  "copy-below": [filename: string];
}>();

type EditorOrderingOptions = {
  isFirst?: boolean;
  isLast?: boolean;
  isOnly?: boolean;
  isActive?: boolean;
  disableMultiEditorControls?: boolean;
};

interface Props {
  language: string;
  fileName: string;
  title: string;
  orderingOptions?: EditorOrderingOptions;
  errorState?: EditorErrorState;
}

const props = withDefaults(defineProps<Props>(), {
  orderingOptions: () => ({
    isFirst: false,
    isLast: false,
    isOnly: false,
    isActive: false,
    disableMultiEditorControls: false,
  }),
  errorState: () => ({ level: "OK" }),
});

const readOnly = useReadonlyStore();

type ButtonItem = {
  text: string;
  icon: Component;
  eventName: string;
  disabled?: boolean;
};

const buttonActions = computed<ButtonItem[]>(() => [
  {
    text: "Move upwards",
    icon: UpArrowIcon,
    eventName: "move-up",
    disabled: props.orderingOptions.isFirst || readOnly.value,
  },
  {
    text: "Move downwards",
    icon: DownArrowIcon,
    eventName: "move-down",
    disabled: props.orderingOptions.isLast || readOnly.value,
  },
  {
    text: "Duplicate below",
    icon: CopyIcon,
    eventName: "copy-below",
    disabled: readOnly.value,
  },
  {
    text: "Delete",
    icon: TrashIcon,
    eventName: "delete",
    disabled: props.orderingOptions.isOnly || readOnly.value,
  },
]);

// Main editor
const monacoEditorContainerRef = ref<HTMLDivElement>();
const editorState = editor.useCodeEditor({
  language: props.language,
  fileName: props.fileName,
  container: monacoEditorContainerRef,
  extraEditorOptions: {
    scrollBeyondLastLine: false,
    automaticLayout: true,
    minimap: { enabled: false },
    overviewRulerLanes: 0,
    overviewRulerBorder: false,
    readOnly: useReadonlyStore().value,
    readOnlyMessage: {
      value: "Read-Only-Mode: configuration is set by flow variables.",
    },
    renderValidationDecorations: "on",
  },
});

const containerRef = ref<HTMLDivElement>();
defineExpose<ExpressionEditorPaneExposes>({
  getEditorState: () => editorState,
  focusEditorProgrammatically: () => {
    // Note: We must call focus before scrolling into view, otherwise the
    // editor will not scroll into view.
    editorState.editor.value?.focus();
    containerRef.value?.scrollIntoView({
      behavior: "smooth",
      block: "nearest",
    });
  },
});

onKeyStroke("Escape", () => {
  if (editorState.editor.value?.hasTextFocus()) {
    (document.activeElement as HTMLElement)?.blur();
  }
});

const onFocus = () => {
  emit("focus", props.fileName);
};

const draggedFunctionStore = useDraggedFunctionStore();

const onDropEvent = (e: DragEvent) => {
  if (e.dataTransfer?.getData("eventSource") === "function-catalog") {
    // The text changes once the browser has processed the drop event
    // (which includes only the function name), so we watch the text
    // here and then put the arguments in.
    const unwatch = watch(editorState.text, () => {
      if (draggedFunctionStore.draggedFunctionData) {
        // Pop the stack element inserting the function name
        // insertFunction will push a new stack element to have
        // a single undo step for the whole function insertion
        editorState.editorModel.popStackElement();

        // Insert the arguments parenteces
        const inserted = insertFunctionCall({
          editorState,
          functionName: "", // The function name is inserted by the drop event
          functionArgs: draggedFunctionStore.draggedFunctionData?.arguments,
        });
        if (!inserted) {
          // Add the undo stack element back if the insertion failed
          editorState.editorModel.pushStackElement();
        }

        resetDraggedFunctionStore();
      }

      unwatch();
    });
  }
};

// register undo changes from outside the editor
onKeyStroke("z", (e) => {
  const key = navigator.userAgent.toLowerCase().includes("mac")
    ? e.metaKey
    : e.ctrlKey;

  // If we have multiple editors, only trigger undo if the editor has focus
  if (key && editorState.editor.value?.hasTextFocus()) {
    editorState.editor.value?.trigger("window", "undo", {});
  }
});

const onMenuItemClicked = (item: ButtonItem) => {
  // @ts-expect-error TS doesn't like dyanmic event names
  emit(item.eventName, props.fileName);
};

const updateEditorHeight = async () => {
  // If the query for the line height fails, we use this as a fallback
  const DEFAULT_LINE_HEIGHT_IN_PIXEL = 13;
  const lineHeight =
    editorState.editor.value?.getOptions().get(EditorOption.lineHeight) ??
    DEFAULT_LINE_HEIGHT_IN_PIXEL;

  const MINIMUM_LINES_IN_EDITOR = 5;
  const contentHeight = Math.max(
    MINIMUM_LINES_IN_EDITOR * lineHeight,
    editorState.editor.value?.getContentHeight() ?? 0,
  );
  if (monacoEditorContainerRef.value && editor) {
    monacoEditorContainerRef.value.style.height = `${contentHeight}px`;
  }
  await nextTick();
  editorState.editor.value?.layout();
};

onMounted(() => {
  updateEditorHeight();
  editorState.editor.value?.onDidChangeModelContent(updateEditorHeight);
});

// TODO AP-23655 This is a workaround. Empty expressions are currently errors with this message.
// However, we want to display them differenetly. This should be handled properly in the future.
const isEmptyExpr = computed(
  () =>
    props.errorState.level === "ERROR" &&
    props.errorState.message ===
      "The expression is empty. Enter an expression that evaluates to a value.",
);
</script>

<template>
  <div
    ref="containerRef"
    class="editor-and-controls-container"
    :class="{
      'has-error': errorState.level === 'ERROR' && !isEmptyExpr,
      'has-warning': errorState.level === 'WARNING',
      'is-empty-expr': isEmptyExpr,
      active: props.orderingOptions.isActive,
    }"
  >
    <div class="everything-except-error">
      <span class="editor-title-bar">
        <span class="title-text">{{ props.title }}</span>
        <span
          v-if="!props.orderingOptions.disableMultiEditorControls"
          class="title-menu"
        >
          <FunctionButton
            v-for="item in buttonActions"
            :key="item.text"
            :disabled="item.disabled"
            class="menu-button"
            compact
            @click="onMenuItemClicked(item)"
          >
            <component :is="item.icon" />
          </FunctionButton>
        </span>
      </span>

      <div
        ref="monacoEditorContainerRef"
        class="code-editor"
        @drop="onDropEvent"
        @focusin="onFocus"
      />

      <span class="editor-control-bar">
        <slot name="multi-editor-controls" />
      </span>
    </div>
    <div class="error-container">
      <span v-if="errorState.level !== 'OK'">
        <WarningIcon class="icon error-icon" />
        <span class="error-message"> {{ errorState.message }} </span>
      </span>
      <span v-else>&nbsp;</span>
    </div>
  </div>
</template>

<style lang="postcss" scoped>
.editor-and-controls-container {
  position: relative;
  display: flex;
  flex-shrink: 1;
  flex-direction: column;
  height: fit-content;
  margin: var(--space-4) var(--space-12);

  --border-colour: var(--knime-cornflower);

  &.has-error {
    --border-colour: var(--knime-coral-dark);
    --error-text-colour: var(--knime-coral-dark);
  }

  &.has-warning {
    --border-colour: var(--knime-carrot);
    --error-text-colour: var(--knime-carrot);
  }

  /* TODO(AP-23655) remove the is-empty-expr class and handle empty expressions properly */
  &.is-empty-expr {
    --error-text-colour: var(--knime-masala);
  }

  &.has-warning,
  &.has-error,
  &.is-empty-expr {
    & .everything-except-error::after {
      position: absolute;
      inset: -1px;
      z-index: 1;
      pointer-events: none;
      content: "";
    }
  }

  &.has-warning,
  &.has-error {
    & .everything-except-error::after {
      border: 1px solid var(--border-colour);
    }
  }

  &:first-child {
    /* Editor gets an extra margin iff it's the first one of its type. */
    margin-top: var(--space-12);
  }

  & .everything-except-error {
    position: relative;
    display: flex;
    flex-grow: 1;
    flex-shrink: 1;
    flex-direction: column;
    min-height: 70px;
    box-shadow: var(--shadow-elevation-1);

    &:hover {
      box-shadow: var(--shadow-elevation-2);
    }

    & .editor-title-bar {
      display: flex;
      flex-shrink: 0;
      align-items: center;
      justify-content: space-between;
      height: var(--space-32);
      padding: 0 var(--space-16);
      font-family: Roboto, sans-serif;
      font-weight: 400;
      background-color: var(--knime-porcelain);

      & .title-menu {
        display: flex;
        gap: var(--space-8);
        background-color: transparent;
      }

      & .title-text {
        font-size: 13px;
      }
    }

    & .code-editor {
      position: relative;
      display: flex;
      width: 100%;
    }

    & .editor-control-bar {
      height: fit-content;
      background-color: var(--knime-gray-light-semi);
    }
  }

  &.active {
    & .everything-except-error::after {
      position: absolute;
      inset: -1px;
      z-index: -1;
      pointer-events: none;
      content: "";
      border: 1px solid var(--border-colour);
    }

    & .editor-title-bar {
      color: var(--knime-porcelain);
      background-color: var(--knime-cornflower);

      & .title-menu .menu-button :deep(svg) {
        stroke: var(--knime-porcelain);
      }
    }
  }

  & .error-container {
    display: flex;
    flex-flow: row nowrap;
    align-items: flex-start;
    min-height: 15px;
    margin-top: 2px;
    margin-bottom: -1px;
    margin-left: var(--space-4);

    & .error-message {
      font-size: 10px;
      line-height: 12px;
      color: var(--error-text-colour);
      overflow-wrap: anywhere;
    }

    & .error-icon {
      width: 12px;
      min-width: 12px;
      height: 12px;
      margin-right: var(--space-4);
      stroke: var(--error-text-colour);
      translate: 0 2px;
    }
  }
}
</style>
