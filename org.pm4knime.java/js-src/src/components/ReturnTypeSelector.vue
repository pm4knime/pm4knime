<script setup lang="ts">
import { computed, ref, watch } from "vue";

import { Dropdown } from "@knime/components";
import { KdsDataType } from "@knime/kds-components";
import { useReadonlyStore } from "@knime/scripting-editor";

import { UNKNOWN_VARIABLE_TYPE } from "@/common/constants";
import { type FlowVariableType } from "@/flowVariableApp/flowVariableTypes";

import type { IdAndText } from "./OutputSelector.vue";

export type AllowedReturnTypes = {
  id: FlowVariableType;
  text: string;
  type?: IdAndText;
};

type PropType = { allowedReturnTypes: AllowedReturnTypes[] };

const props = defineProps<PropType>();

const readOnly = useReadonlyStore();

const selectorModel = defineModel<FlowVariableType>({
  default: "Unknown",
});

// This takes advantage of the fact that there are currently
// only a choice between IntType and LongType
// All other types have a 1-to-1 correspondence with the selected type
const lastIntegerValue = ref<FlowVariableType | null>(null);
watch(selectorModel, (selectorModel) => {
  if (selectorModel === "Integer" || selectorModel === "Long") {
    lastIntegerValue.value = selectorModel;
  }
});

watch(
  () => props.allowedReturnTypes,
  (newValue, oldValue) => {
    const allowedValues = newValue.map((item) => item.id);

    // check if the allowed values have changed by value
    // if they have not changed, we do not need to update the value
    // this prevents recursive update loops
    if (oldValue && newValue && oldValue.length === newValue.length) {
      if (oldValue.every((value, index) => value.id === allowedValues[index])) {
        return;
      }
    }

    if (
      lastIntegerValue.value &&
      lastIntegerValue.value !== selectorModel.value &&
      allowedValues.includes(lastIntegerValue.value)
    ) {
      selectorModel.value = lastIntegerValue.value;
    } else if (
      !selectorModel.value ||
      !allowedValues.includes(selectorModel.value)
    ) {
      selectorModel.value = allowedValues[0];
    }
  },
);

const possibleValues = computed(() =>
  props.allowedReturnTypes.map((item) => ({
    ...item,
    slotData: {
      text: item.text,
      typeId: item.type?.id,
      typeText: item.type?.text,
    },
  })),
);
</script>

<template>
  <!-- eslint-disable vue/attribute-hyphenation typescript complains with ':aria-label' instead of ':ariaLabel'-->
  <Dropdown
    id="dropdown-box-to-select-entity"
    v-model="selectorModel"
    ariaLabel="return type selection"
    :possible-values="possibleValues"
    direction="up"
    :disabled="readOnly || allowedReturnTypes.length <= 1"
    compact
    ><template #option="{ slotData = {} }">
      <div class="with-type">
        <KdsDataType
          :icon-name="slotData.typeId ?? UNKNOWN_VARIABLE_TYPE.id"
          :icon-title="slotData.typeText ?? UNKNOWN_VARIABLE_TYPE.text"
          size="small"
        />
        <span>{{ slotData.text }}</span>
      </div>
    </template>
  </Dropdown>
</template>

<style scoped lang="postcss">
.with-type {
  display: flex;
  gap: var(--space-4);
  align-items: center;
}
</style>
