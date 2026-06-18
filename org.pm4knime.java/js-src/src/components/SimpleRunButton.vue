<script setup lang="ts">
import { computed } from "vue";

import { Button, Tooltip } from "@knime/components";
import PlayIcon from "@knime/styles/img/icons/play.svg";

interface PropType {
  runButtonDisabledErrorReason: string | null;
  showButtonText: boolean;
}

const emit = defineEmits<(event: "run-expressions") => void>();

const handleRunExpressions = () => {
  emit("run-expressions");
};

const props = defineProps<PropType>();

const buttonText = computed(() => (props.showButtonText ? "Evaluate" : ""));
</script>

<template>
  <Tooltip class="error-tooltip" :text="runButtonDisabledErrorReason ?? ''">
    <Button
      primary
      compact
      :disabled="runButtonDisabledErrorReason !== null"
      @click="handleRunExpressions()"
    >
      <div
        class="run-button"
        :class="{
          'hide-button-text': !showButtonText,
        }"
      >
        <PlayIcon />
      </div>
      {{ buttonText }}
    </Button>
  </Tooltip>
</template>

<style lang="postcss" scoped>
.hide-button-text {
  margin-right: calc(-1 * var(--space-16));
}

.run-button {
  display: inline;
}

/* Needed to be on top of the splitpane splitters */
.error-tooltip :deep(.text) {
  z-index: 1;
}

.more-rows-submenu {
  background-color: var(--knime-yellow);

  &:focus-within,
  &:hover {
    background-color: var(--knime-masala);

    & :deep(.submenu-toggle svg) {
      stroke: var(--knime-white);
    }
  }
}
</style>
