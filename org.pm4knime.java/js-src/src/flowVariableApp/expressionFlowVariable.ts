import "./__mocks__/browser-mock-flow-variable-services";
import { createApp } from "vue";

import { useKdsLegacyMode } from "@knime/kds-components";
import { init, initMocked } from "@knime/scripting-editor";
import { LoadingApp } from "@knime/scripting-editor/loading";

import { setupConsola } from "@/common/functions";
import ExpressionFlowVariableApp from "@/flowVariableApp/ExpressionFlowVariableApp.vue";

setupConsola();

// NOTE: For development, the legacy mode can be disabled and dark mode can be forced here
// const { currentMode } = useKdsDarkMode();
// currentMode.value = "dark"
useKdsLegacyMode(true);

// Show loading app while initializing
const loadingApp = createApp(LoadingApp);
loadingApp.mount("#app");

// Initialize application (e.g., load initial data, set up services)
if (import.meta.env.MODE === "development.browser") {
  // Mock the initial data and services
  initMocked(
    (await import("./__mocks__/browser-mock-flow-variable-services")).default,
  );
} else {
  await init();
}

// Unmount loading app and mount the main app
loadingApp.unmount();
createApp(ExpressionFlowVariableApp).mount("#app");
