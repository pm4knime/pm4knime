<script setup lang="ts">
import { onMounted, ref } from "vue";

import { getScriptingService } from "@knime/scripting-editor";
import {
  type ExtensionConfig,
  UIExtension,
  type UIExtensionAPILayer,
} from "@knime/ui-extension-renderer/vue";
import {
  AlertingService,
  JsonDataService,
  type UIExtensionService,
} from "@knime/ui-extension-service";

const baseService = ref<UIExtensionService<UIExtensionAPILayer> | null>(null);
const extensionConfig = ref<ExtensionConfig | null>(null);
const resourceLocation = ref<string>("");
const dataAvailable = ref<boolean>(false);
const noDataMessage = ref<string | null>(null);

const emit = defineEmits(["output-preview-updated"]);

const getInitialData = async () => {
  const dataService = await JsonDataService.getInstance();
  try {
    const initialData = await dataService.data<string>({
      method: "FlowVariablePreviewInitialDataSupplier.getInitialData",
    });

    return initialData === undefined ? initialData : JSON.parse(initialData);
  } catch (error) {
    consola.error("OutputPreviewFlowVariable:: Failed to get initial data", {
      method: "FlowVariablePreviewInitialDataSupplier.getInitialData",
      error,
    });
    return undefined;
  }
};

const makeExtensionConfig = async (
  nodeId: string,
  projectId: string,
  workflowId: string,
  baseUrl: string,
): Promise<ExtensionConfig> => {
  const initialData = await getInitialData();

  return {
    nodeId,
    extensionType: "dialog",
    projectId,
    workflowId,
    hasNodeView: false,
    resourceInfo: {
      id: "someId",
      type: "SHADOW_APP",
      path: `${baseUrl}uiext/tableview/TableView.js`,
    },
    initialData,
  };
};

const noop = () => {
  /* mock unused api fields */
};

const apiLayer: UIExtensionAPILayer = {
  registerPushEventService: () => noop,
  callNodeDataService: async () => {},
  updateDataPointSelection: () => Promise.resolve({ result: null }),
  // we can just forward to the baseService, because we assume that
  // the backend provided baseUrl (e.g., org.knime.core.ui.dialog, only relevant for desktop)
  // is same for the parent ui-extension and this nested ui-extension
  getResourceLocation: (path) => baseService.value!.getResourceLocation(path),
  imageGenerated: noop,
  onApplied: noop,
  onDirtyStateChange: noop,
  publishData: noop,
  sendAlert: (alert) => {
    AlertingService.getInstance().then((service) =>
      // @ts-expect-error baseService is not API but it exists
      service.baseService.sendAlert(alert),
    );
  },
  setControlsVisibility: noop,
  setReportingContent: noop,
  showDataValueView: noop,
  closeDataValueView: noop,
};

const updateExtensionConfig = async (config: ExtensionConfig) => {
  const path = `${config.resourceInfo.path?.split("/").slice(0, -1).join("/")}/core-ui/TableView.js`;
  resourceLocation.value = await apiLayer.getResourceLocation(path);

  extensionConfig.value = await makeExtensionConfig(
    config.nodeId,
    config.projectId,
    config.workflowId,
    // @ts-expect-error baseUrl is not part of the type but it exists
    config.resourceInfo.baseUrl,
  );
};

onMounted(async () => {
  const jsonService = await JsonDataService.getInstance();
  // @ts-expect-error baseService is not API but a property of the service
  baseService.value = jsonService.baseService;

  const extensionConfigLoaded: ExtensionConfig =
    // @ts-expect-error baseService is not API but a property of the service
    await baseService.value.getConfig();
  await updateExtensionConfig(extensionConfigLoaded);

  getScriptingService().registerEventHandler(
    "updatePreview",
    async (errorMessage: null | string) => {
      if (errorMessage) {
        dataAvailable.value = false;
        noDataMessage.value = errorMessage;
        emit("output-preview-updated");
        return;
      }

      if (!extensionConfig.value) {
        return;
      }

      dataAvailable.value = true;
      noDataMessage.value = null;
      emit("output-preview-updated");
      await updateExtensionConfig(extensionConfigLoaded);
    },
  );
});
</script>

<template>
  <div
    v-if="dataAvailable === true && extensionConfig !== null"
    class="output-table"
  >
    <div class="output-table-preview">
      <div class="preview-background">
        <div class="preview-warning-text">Preview</div>
      </div>
      <UIExtension
        :api-layer="apiLayer"
        :extension-config="extensionConfig"
        :resource-location="resourceLocation"
        :shadow-app-style="{ height: '100%', width: '100%' }"
      />
    </div>
  </div>
  <div
    v-else-if="noDataMessage !== null"
    class="output-table-preview"
    data-testid="no-data-placeholder"
  >
    {{ noDataMessage }}
  </div>
  <div v-else class="output-table-preview pre-evaluation-sign">
    To see the preview, evaluate the expression using the button above.
  </div>
</template>

<style lang="postcss" scoped>
.output-table-preview {
  display: flex;
  flex-direction: column;
  gap: 0;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.pre-evaluation-sign {
  display: flex;
  justify-content: center;
  height: 35px;
  border-radius: 10px;
}

.output-table {
  width: 100%;
  height: 100%;
}

.preview-background {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: var(--space-4) 0;
  background-color: var(--knime-cornflower-semi);
}

.preview-warning-text {
  padding: var(--space-4) var(--space-8);
  font-size: small;
  vertical-align: middle;
  color: black;
  text-align: center;
  background-color: white;
  border-radius: 999vw;
  box-shadow: 0 4px 8px rgb(0 0 0 / 10%);
}
</style>
