import { reactive } from "vue";

/**
 * Store for data of functions currently being dragged from the function catalog
 * into the editor. Needed because Chrome doesn't properly support the `dataTransfer`
 * field of the drag event, and we want to insert the arguments of the dragged function
 * as monaco snippets, which can't be handled automatically by the browser.
 */

export type StoreType = {
  draggedFunctionData: { name: string; arguments: string[] | null } | null;
};

const store = reactive<StoreType>({
  draggedFunctionData: null,
});

export const useDraggedFunctionStore = () => store;
export const resetDraggedFunctionStore = () => {
  store.draggedFunctionData = null;
};
