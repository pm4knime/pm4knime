import { shallowRef } from "vue";

import type {
  InputOutputModel,
  SubItem,
  SubItemType,
} from "@knime/scripting-editor";

import type { IconRendererProps } from "@/components/IconRenderer.vue";
import IconRenderer from "@/components/IconRenderer.vue";
import type { SelectorState } from "@/components/OutputSelector.vue";

export type SubItemState = {
  selectorState: SelectorState;
  key: string;
  returnType: SubItemType;
};

export const replaceSubItems = (
  subItem: SubItem<IconRendererProps>,
  states: SubItemState[],
  actionBuilder: (editorKey: string) => () => void,
): SubItem<IconRendererProps> => {
  const lastReplacement = states.findLast(
    (state) =>
      state.selectorState.outputMode === "REPLACE_EXISTING" &&
      state.selectorState.replace === subItem.name,
  );

  if (lastReplacement) {
    return {
      name: subItem.name,
      type: lastReplacement.returnType,
      icon: {
        component: shallowRef(IconRenderer),
        props: {
          action: actionBuilder(lastReplacement.key),
        },
      },
      supported: true,
    };
  }

  return subItem;
};

export const buildAppendedOutput = (
  states: SubItemState[],
  actionBuilder: (editorKey: string) => () => void,
  metaData: Pick<
    InputOutputModel,
    "name" | "portType" | "subItemCodeAliasTemplate"
  >,
): InputOutputModel => ({
  name: metaData.name,
  portType: metaData.portType,
  subItemCodeAliasTemplate: metaData.subItemCodeAliasTemplate,
  subItems: states
    .map((state): SubItem<IconRendererProps> | null =>
      state.selectorState.outputMode === "APPEND"
        ? {
            name: state.selectorState.create,
            type: state.returnType,
            supported: true,
          }
        : null,
    )
    .filter((item) => item !== null)
    .map((subItem) => replaceSubItems(subItem, states, actionBuilder)),
});
