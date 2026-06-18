import { getInitialData, getSettingsService } from "@knime/scripting-editor";

import type {
  StateAwareOCELInitialData,
  StateAwareOCELSettings,
} from "@/stateAwareOcelApp/stateAwareOcelTypes";

export const getStateAwareOCELInitialDataService = () => ({
  getInitialData: (): StateAwareOCELInitialData =>
    getInitialData() as StateAwareOCELInitialData,
});

export const getStateAwareOCELSettingsService = () => ({
  ...getSettingsService(),
  getSettings: (): StateAwareOCELSettings =>
    getSettingsService().getSettings() as StateAwareOCELSettings,
  registerSettingsGetterForApply: (settingsGetter: () => StateAwareOCELSettings) =>
    getSettingsService().registerSettingsGetterForApply(settingsGetter),
});
