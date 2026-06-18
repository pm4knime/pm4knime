package org.pm4knime.node.logmanipulation.stateawareocel;

import org.knime.core.webui.node.dialog.scripting.OutputTablePreviewUtils;
import org.knime.core.webui.page.Page;

/** Loads the bundled WebUI dialog page for the State-Aware OCEL Enricher. */
@SuppressWarnings("restriction")
final class StateAwareOCELPageResources {

    private StateAwareOCELPageResources() {
        // utility class
    }

    static Page createPage(final String relativePagePath) {
        return Page.create().fromFile().bundleClass(StateAwareOCELPageResources.class).basePath("js-src/dist")
            .relativeFilePath(relativePagePath)
            .addResourceDirectory("assets")
            .addResourceDirectory("monacoeditorwork")
            .addResources(OutputTablePreviewUtils::getCoreUIResource, "core-ui", true);
    }
}
