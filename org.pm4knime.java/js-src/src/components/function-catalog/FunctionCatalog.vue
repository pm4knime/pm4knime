<script lang="ts">
export const FUNCTION_INSERTION_EVENT = "functionInsertion";
</script>

<script setup lang="ts">
import { type ComponentPublicInstance, computed, reactive, ref } from "vue";
import { useElementBounding } from "@vueuse/core";

import {
  EMPTY_DRAG_IMAGE,
  SearchInput,
  createDragGhosts,
} from "@knime/components";
import {
  insertionEventHelper,
  useReadonlyStore,
} from "@knime/scripting-editor";
import NextIcon from "@knime/styles/img/icons/arrow-next.svg";

import FunctionCatalogDescription from "@/components/function-catalog/FunctionCatalogDescription.vue";
import type {
  SelectableFunction,
  SelectableItem,
} from "@/components/function-catalog/catalogTypes";
import {
  MIN_WIDTH_FOR_SIDE_BY_SIZE_DESC_FUNC_CATALOG,
  MIN_WIDTH_FUNCTION_CATALOG,
} from "@/components/function-catalog/contraints";
import { filterCatalogData } from "@/components/function-catalog/filterCatalogData";
import { mapFunctionCatalogData } from "@/components/function-catalog/mapFunctionCatalogData";
import type {
  CategoryData,
  FunctionCatalogData,
  FunctionCatalogEntryData,
} from "@/components/functionCatalogTypes";
import { useDraggedFunctionStore } from "@/draggedFunctionStore";

const FUNCTION_CATALOG_WIDTH = `${MIN_WIDTH_FUNCTION_CATALOG}px`;

type PropType = {
  functionCatalogData: FunctionCatalogData;
  initiallyExpanded: boolean;
};
const props = defineProps<PropType>();

const readOnly = useReadonlyStore();

const catalogData = mapFunctionCatalogData(props.functionCatalogData);

const categoryNames = Object.keys(catalogData);
const categories = ref(
  Object.fromEntries(
    categoryNames.map((categoryName) => [
      categoryName,
      {
        expanded: props.initiallyExpanded,
        fullName: categoryName,
        description:
          props.functionCatalogData.categories.find(
            (category) => category.fullName === categoryName,
          )?.description ?? "No description for category available",
        metaCategory:
          props.functionCatalogData.categories.find(
            (category) => category.fullName === categoryName,
          )?.metaCategory ?? null,
        name: props.functionCatalogData.categories.find(
          (category) => category.fullName === categoryName,
        )?.name!,
      } satisfies Record<string, any> & CategoryData,
    ]),
  ),
);

const removeGhostsRef = ref<() => void>(() => {});

const draggedFunctionStore = useDraggedFunctionStore();

const onDragStart = (e: DragEvent, f: FunctionCatalogEntryData) => {
  if (readOnly.value) {
    e.preventDefault();
    return;
  }
  const { removeGhosts } = createDragGhosts({
    badgeCount: null, // don't show a badge at all
    dragStartEvent: e,
    selectedTargets: [
      {
        textContent: f.name,
        targetEl: e.target as HTMLElement,
      },
    ],
  });

  removeGhostsRef.value = removeGhosts;

  // We only insert the function name, not the arguments, as the arguments are
  // inserted as a snippet when the function is dropped. The snippet will be
  // handled by the editor.
  e.dataTransfer?.setData("text", f.name);
  e.dataTransfer?.setData("eventSource", "function-catalog");

  // Chrome doesn't let you use e.dataTransfer, so we have to use a global store
  // instead to pass the extra data.
  draggedFunctionStore.draggedFunctionData = {
    name: f.name,
    arguments:
      f.entryType === "constant" ? null : f.arguments?.map((arg) => arg.name)!,
  };

  e.dataTransfer?.setDragImage(EMPTY_DRAG_IMAGE!, 0, 0);
};

const onDragEnd = () => {
  removeGhostsRef.value();
};

const triggerFunctionInsertionEvent = (
  evt: Event,
  f: FunctionCatalogEntryData,
) => {
  if (readOnly.value) {
    evt.preventDefault();
    return;
  }
  insertionEventHelper
    .getInsertionEventHelper(FUNCTION_INSERTION_EVENT)
    .handleInsertion({
      textToInsert: f.name,
      extraArgs: {
        functionArgs:
          f.entryType === "constant"
            ? null
            : f.arguments?.map((arg) => arg.name),
      },
    });
};

const catalogRoot = ref();
const catalogRootRef = useElementBounding(catalogRoot);
const displayFunctionCatalogBelowList = computed(
  () =>
    catalogRootRef.width.value <= MIN_WIDTH_FOR_SIDE_BY_SIZE_DESC_FUNC_CATALOG,
);

const selectedEntry = ref<null | SelectableItem>(null);

const functionAndCategoryElementRefs = reactive<{ [key: string]: HTMLElement }>(
  {},
);

const createElementName = (type: "function" | "category", name: string) =>
  `ref-${type}-${name}`;

// You can pass a function to v-bind:ref and let it store the element in a
// custom way. We'll use this to keep track of all of our function and category
// headers by storing them by their type+name in
// `functionAndCategoryElementRefs`
const createElementReference = (
  type: "function" | "category",
  name: string,
) => {
  return (el: Element | ComponentPublicInstance | null) => {
    functionAndCategoryElementRefs[createElementName(type, name)] =
      el as HTMLElement;
  };
};

const selectEntry = (item: SelectableItem | null) => {
  selectedEntry.value = item;

  if (item?.type) {
    const refName = createElementName(
      item.type,
      item.type === "category" ? item.name : item.functionData.name,
    );

    functionAndCategoryElementRefs[refName].scrollIntoView({
      behavior: "smooth",
      block: "nearest",
    });
  }
};

const toggleCategoryExpansion = (categoryName: string) => {
  categories.value[categoryName].expanded =
    !categories.value[categoryName].expanded;
  if (
    selectedEntry.value?.type === "function" &&
    selectedEntry.value.functionData.category === categoryName &&
    !categories.value[categoryName].expanded
  ) {
    selectedEntry.value = null;
  }
};
const isSelected = (item: SelectableItem): boolean => {
  if (item.type === "category" && selectedEntry.value?.type === "category") {
    return selectedEntry.value?.name === item.name;
  }
  if (item.type === "function" && selectedEntry.value?.type === "function") {
    return selectedEntry.value?.functionData.name === item.functionData.name;
  }
  return false;
};

const searchQuery = ref("");
const filteredFunctionCatalog = computed(() => {
  return filterCatalogData(searchQuery.value, categories.value, catalogData);
});

const isFilteredCatalogDataEmpty = computed(() => {
  return filteredFunctionCatalog.value.orderOfItems.length === 0;
});

const moveSelectionToTop = () => {
  selectEntry(filteredFunctionCatalog.value.orderOfItems[0]);
};
const moveSelectionToEnd = () => {
  selectEntry(
    filteredFunctionCatalog.value.orderOfItems[
      filteredFunctionCatalog.value.orderOfItems.length - 1
    ],
  );
};
const moveSelectionUp = () => {
  const itemToSelect =
    filteredFunctionCatalog.value.orderOfItems.findIndex(isSelected);
  if (itemToSelect === -1) {
    moveSelectionToEnd();
  } else {
    selectEntry(
      filteredFunctionCatalog.value.orderOfItems[
        (itemToSelect - 1 + filteredFunctionCatalog.value.orderOfItems.length) %
          filteredFunctionCatalog.value.orderOfItems.length
      ],
    );
  }
};
const moveSelectionDown = () => {
  const itemToSelect =
    filteredFunctionCatalog.value.orderOfItems.findIndex(isSelected);
  if (itemToSelect === -1) {
    moveSelectionToTop();
  } else {
    selectEntry(
      filteredFunctionCatalog.value.orderOfItems[
        (itemToSelect + 1) % filteredFunctionCatalog.value.orderOfItems.length
      ],
    );
  }
};
const onKeyDown = (e: KeyboardEvent) => {
  switch (e.key) {
    case "End":
      moveSelectionToEnd();
      break;
    case "Home":
      moveSelectionToTop();
      break;
    case "ArrowDown":
      moveSelectionDown();
      e.preventDefault();
      break;
    case "ArrowUp":
      moveSelectionUp();
      e.preventDefault();
      break;
    case " ":
    case "Enter":
      if (selectedEntry.value?.type === "category") {
        toggleCategoryExpansion(selectedEntry.value.name);
      } else {
        triggerFunctionInsertionEvent(
          e,
          (selectedEntry.value as SelectableFunction)?.functionData,
        );
      }

      e.preventDefault(); // Stop accidental scrolling
      break;
    case "ArrowRight":
      if (
        selectedEntry.value?.type === "category" &&
        !categories.value[selectedEntry.value.name].expanded
      ) {
        toggleCategoryExpansion(selectedEntry.value.name);
      }

      e.preventDefault(); // Stop accidental scrolling
      break;
    case "ArrowLeft":
      if (
        selectedEntry.value?.type === "category" &&
        categories.value[selectedEntry.value.name].expanded
      ) {
        toggleCategoryExpansion(selectedEntry.value.name);
      }

      e.preventDefault(); // Stop accidental scrolling
      break;
  }
};

const functionListRef = ref<HTMLDivElement | null>(null);
const grabFocus = () => {
  if (selectedEntry.value === null) {
    moveSelectionToTop();
  }

  // This means that the focus is specifically on the function list,
  // not on any of its children, which gives us the <tab> navigation
  // behaviour we want.
  functionListRef.value?.focus();
};

const expandAll = () => {
  if (searchQuery.value.trim().length !== 0) {
    for (const categoryName in categories.value) {
      categories.value[categoryName].expanded = true;
    }
  }
};
</script>

<template>
  <div
    ref="catalogRoot"
    class="function-catalog-container"
    :class="{ 'slim-mode': displayFunctionCatalogBelowList }"
  >
    <div
      class="function-catalog"
      :class="{ 'slim-mode': displayFunctionCatalogBelowList }"
      :style="{ '--function-catalog-width': FUNCTION_CATALOG_WIDTH }"
      tabindex="-1"
    >
      <div class="sticky-search">
        <div class="search-input">
          <SearchInput
            v-model="searchQuery"
            placeholder="Search"
            compact
            @update:model-value="
              () => {
                selectEntry(null);
                expandAll();
              }
            "
          />
        </div>
      </div>

      <div v-if="isFilteredCatalogDataEmpty" class="no-search-match">
        There are no matching functions found
      </div>
      <!-- Make this panel not be a tab stop if filtered catalogue is empty -->
      <div
        ref="functionListRef"
        class="function-list"
        :tabindex="isFilteredCatalogDataEmpty ? -1 : 0"
        role="button"
        @keydown="onKeyDown"
        @focus="grabFocus"
        @click="grabFocus"
      >
        <div
          v-for="(
            categoryData, categoryName
          ) in filteredFunctionCatalog.filteredCatalogData"
          :key="categoryName"
          class="category-container"
        >
          <div
            v-if="categoryData.length > 0"
            :ref="createElementReference('category', categoryName)"
            class="category-header"
            role="button"
            :class="{
              expanded: categories[categoryName].expanded,
              selected: isSelected({
                type: 'category',
                name: categoryName,
              }),
              empty: categoryData.length === 0,
              'read-only': readOnly,
            }"
            tabindex="-1"
            @click="toggleCategoryExpansion(categoryName)"
            @focus="selectEntry({ type: 'category', name: categoryName })"
          >
            <span
              class="category-icon"
              :class="{
                expanded: categories[categoryName].expanded ? 'expanded' : '',
                'selected-icon': isSelected({
                  type: 'category',
                  name: categoryName,
                }),
                empty: categoryData.length === 0,
              }"
            >
              <NextIcon />
            </span>

            {{ categoryName }}
          </div>

          <div
            v-if="categories[categoryName].expanded"
            role="button"
            class="category-functions"
          >
            <div v-for="functionData in categoryData" :key="functionData.name">
              <div
                :ref="createElementReference('function', functionData.name)"
                class="function-header"
                :class="{
                  selected: isSelected({ type: 'function', functionData }),
                  'read-only': readOnly,
                }"
                tabindex="-1"
                :draggable="!readOnly"
                @click="
                  selectEntry({
                    type: 'function',
                    functionData,
                  })
                "
                @dragstart="
                  (event: DragEvent) => onDragStart(event, functionData)
                "
                @dragend="onDragEnd"
                @dblclick="
                  (event: MouseEvent) =>
                    triggerFunctionInsertionEvent(event, functionData)
                "
              >
                {{ functionData.name }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div
      class="info-panel"
      :class="{ 'slim-mode': displayFunctionCatalogBelowList }"
    >
      <div v-if="selectedEntry !== null">
        <div v-if="selectedEntry.type === 'function'">
          <FunctionCatalogDescription
            :data="selectedEntry.functionData"
            type="functionOrConstant"
          />
        </div>
        <div v-else>
          <FunctionCatalogDescription
            :data="categories[selectedEntry.name]"
            type="category"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="postcss" scoped>
@import url("@knime/styles/css/variables/spacings.css");

.function-catalog-container {
  display: flex;
  flex-direction: row;
  height: 100%;

  &.slim-mode {
    flex-direction: column;
  }
}

.read-only {
  opacity: 0.5;
}

.sticky-search {
  padding: var(--space-4);
  padding-bottom: var(--space-8);
  background-color: white;
}

.no-search-match {
  display: flex;
  justify-content: center;
  padding: var(--space-8);
  font-size: 13px;
  font-style: italic;
  font-weight: 500;
  line-height: 19.5px;
  color: var(--knime-masala);
}

.function-catalog {
  display: flex;
  flex-direction: column;
  /* stylelint-disable-next-line csstools/value-no-unknown-custom-properties */
  width: var(--function-catalog-width);
  padding-top: var(--space-4);
  background-color: white;

  &.slim-mode {
    width: 100%;
    height: 60%;
  }
}

.category-header,
.function-header {
  padding: 2px 0 2px var(--space-4);
  cursor: pointer;

  &.selected {
    position: relative;
    z-index: 0;
    color: var(--theme-dropdown-foreground-color-selected);
    background-color: transparent;

    &::before {
      position: absolute;
      inset: 0 100vw 0 -100vw;
      z-index: -1;
      width: 300vw;
      height: 105%;
      content: "";
      background-color: var(--theme-dropdown-background-color-selected);
    }
  }
}

.category-header {
  display: flex;
  align-items: center;
  padding-left: var(--space-4);
  font-size: 13px;
  font-weight: 500;

  &:focus {
    outline: none;
  }

  &.empty {
    color: var(--knime-silver-sand-semi);
    cursor: default;
  }
}

.category-icon {
  width: 13px;
  height: 13px;
  margin-right: var(--space-4);
  stroke: var(--knime-masala);
  transition: transform 0.3s ease;

  & svg {
    stroke-width: 2px;
  }

  &.selected-icon {
    & svg {
      stroke: white;
    }
  }

  &.expanded {
    transform: rotate(90deg);
  }

  &.empty {
    visibility: hidden;
    cursor: default;
  }
}

.function-list {
  flex: 1;
  padding-top: var(--space-4);
  overflow: hidden auto;

  &:focus {
    outline: none;
  }

  &:not(:focus-within) {
    & .category-icon.selected-icon svg {
      stroke: var(--knime-masala);
    }

    & .selected {
      color: var(--theme-dropdown-foreground-color-hover);

      &::before {
        background: var(--theme-dropdown-background-color-hover);
      }
    }
  }
}

.info-panel {
  flex: 1;
  padding: var(--space-8);
  overflow-y: auto;
  border-left: 1px solid var(--knime-silver-sand);

  &.slim-mode {
    width: 100%;
    height: 40%;
    border-top: 1px solid var(--knime-silver-sand);
    border-left: none;
  }
}

.function-header {
  margin-left: var(--space-24);
  font-size: 13px;
  font-weight: normal;
  color: var(--knime-dove-gray);
  overflow-wrap: normal;

  &:focus {
    outline: none;
  }
}

.category-functions:not(.selected) > div:hover,
.category-header:not(.selected):hover {
  color: var(--theme-dropdown-foreground-color-hover);
  background: var(--theme-dropdown-background-color-hover);
}
</style>
