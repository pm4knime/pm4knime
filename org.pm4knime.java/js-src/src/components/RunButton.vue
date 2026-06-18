<script setup lang="ts">
import { computed } from "vue";

import { Button, SplitButton, SubMenu, Tooltip } from "@knime/components";
import DropdownIcon from "@knime/styles/img/icons/arrow-dropdown.svg";
import PlayIcon from "@knime/styles/img/icons/play.svg";

import { DEFAULT_NUMBER_OF_ROWS_TO_RUN } from "@/common/constants";

interface PropType {
  runButtonDisabledErrorReason: string | null;
  showButtonText: boolean;
}

const emit = defineEmits<(event: "run-expressions", rows: number) => void>();

const handleRunExpressions = (rows: number) => {
  emit("run-expressions", rows);
};

const props = defineProps<PropType>();

const buttonText = computed(() =>
  props.showButtonText
    ? `Evaluate first ${DEFAULT_NUMBER_OF_ROWS_TO_RUN} rows`
    : "",
);
</script>

<template>
  <Tooltip class="error-tooltip" :text="runButtonDisabledErrorReason ?? ''">
    <SplitButton>
      <Button
        primary
        compact
        :disabled="runButtonDisabledErrorReason !== null"
        @click="handleRunExpressions(DEFAULT_NUMBER_OF_ROWS_TO_RUN)"
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

      <SubMenu
        :items="[
          { text: 'Evaluate first 100 rows', metadata: 100 },
          { text: 'Evaluate first 1000 rows', metadata: 1000 },
        ]"
        button-title="Run more rows"
        class="more-rows-submenu"
        orientation="top"
        :disabled="runButtonDisabledErrorReason !== null"
        @item-click="
          (_evt: any, item: any) => handleRunExpressions(item.metadata)
        "
      >
        <DropdownIcon />
      </SubMenu>
    </SplitButton>
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
