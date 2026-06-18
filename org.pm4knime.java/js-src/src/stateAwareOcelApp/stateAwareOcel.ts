import { createApp } from "vue";

import { useKdsLegacyMode } from "@knime/kds-components";
import { init, initMocked } from "@knime/scripting-editor";
import { LoadingApp } from "@knime/scripting-editor/loading";

import { setupConsola } from "@/common/functions";
import StateAwareOCELApp from "@/stateAwareOcelApp/StateAwareOCELApp.vue";

setupConsola();
useKdsLegacyMode(true);

const loadingApp = createApp(LoadingApp);
loadingApp.mount("#app");

if (import.meta.env.MODE === "development.browser") {
  initMocked((await import("./stateAwareOcelBrowserMockServices")).default);
} else {
  await init();
}

loadingApp.unmount();
createApp(StateAwareOCELApp).mount("#app");
