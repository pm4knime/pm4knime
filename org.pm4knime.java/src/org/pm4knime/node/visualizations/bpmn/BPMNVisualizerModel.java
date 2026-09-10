package org.pm4knime.node.visualizations.bpmn;

import org.knime.node.DefaultModel;

public final class BPMNVisualizerModel {

    private BPMNVisualizerModel() {
    }

    static void configure(final DefaultModel.ConfigureInput i, final DefaultModel.ConfigureOutput o) {
        // View-only node.
    }

    static void execute(final DefaultModel.ExecuteInput i, final DefaultModel.ExecuteOutput o) {
        o.setInternalData(i.getInPortObject(0));
    }
}
