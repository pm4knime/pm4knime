package org.pm4knime.node.visualizations.alignment;

import java.util.Map;

import org.knime.node.DefaultView;
import org.pm4knime.portobject.RepResultPortObjectTable;
import org.pm4knime.portobject.factories.AlignmentViewData;

final class AlignmentViewerView {

    private AlignmentViewerView() {
    }

    static DefaultView.DefaultInitialData<Map<String, Object>>
        createInitialData(final DefaultView.RequireInitialData request) {

        return request.data(viewInput -> AlignmentViewData.create(
            (RepResultPortObjectTable)viewInput.getInternalPortObjects()[0]
        ));
    }
}
