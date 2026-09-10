package org.pm4knime.node.visualizations.alignment;

import org.knime.node.DefaultModel;

final class AlignmentViewerModel {

    private AlignmentViewerModel() {
    }

    static void configure(final DefaultModel.ConfigureInput input, final DefaultModel.ConfigureOutput output) {
        // View-only node.
    }

    static void execute(final DefaultModel.ExecuteInput input, final DefaultModel.ExecuteOutput output) {
        output.setInternalData(input.getInPortObject(0));
    }
}
