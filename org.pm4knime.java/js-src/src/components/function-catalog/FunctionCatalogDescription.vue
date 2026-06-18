<script setup lang="ts">
import { computed } from "vue";

import { Description } from "@knime/components";

import {
  categoryDataToHtml,
  functionDataToHtml,
} from "@/components/function-catalog/functionDescriptionToMarkdown";
import type {
  CategoryData,
  FunctionCatalogEntryData,
} from "@/components/functionCatalogTypes";

type PropType =
  | {
      data: FunctionCatalogEntryData;
      type: "functionOrConstant";
    }
  | {
      data: CategoryData;
      type: "category";
    };

const props = defineProps<PropType>();

const descriptionAsHTML = computed(() =>
  props.type === "functionOrConstant"
    ? functionDataToHtml(props.data)
    : categoryDataToHtml(props.data),
);
const upperHeader = computed(() => {
  if (props.type === "category") {
    return props.data.metaCategory ?? "";
  } else {
    return props.data.name;
  }
});
const lowerHeader = computed(() => {
  if (props.type === "category") {
    return props.data.name;
  } else if (props.data.entryType === "function") {
    return `(${props.data.arguments.map((arg) => arg.name).join(", ")})`;
  } else {
    return "";
  }
});
</script>

<template>
  <div class="node-description">
    <div
      class="titles"
      :class="{
        'is-category': props.type === 'category',
        'is-function': props.type === 'functionOrConstant',
      }"
    >
      <h3 class="description-title">{{ upperHeader }}</h3>
      <h3 class="description-title">{{ lowerHeader }}</h3>
    </div>
    <Description
      :text="descriptionAsHTML"
      render-as-html
      class="markdown-function-desc"
    />
  </div>
</template>

<style lang="postcss">
.markdown-function-desc {
  --description-font-size: 13px;

  font-size: 13px;
  line-height: 150%;

  /* Copy/pasted from NodeDescriptionContent.vue, changes how code looks */
  & :deep(tt),
  & :deep(pre),
  & :deep(code),
  & :deep(samp) {
    font-size: 11px;
    line-height: 150%;
  }
}
</style>

<style lang="postcss" scoped>
.description-title {
  min-height: 18px;
  margin-top: 0;
  margin-bottom: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 18px;
  white-space: nowrap;
}

.titles {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  margin-bottom: 10px;

  &.is-function {
    & .description-title:last-child {
      font-size: 13px;
      font-weight: 300;
      overflow-wrap: anywhere;
      white-space: wrap;
    }
  }

  &.is-category {
    & .description-title:first-child {
      font-size: 13px;
      font-weight: 300;
    }
  }
}
</style>
