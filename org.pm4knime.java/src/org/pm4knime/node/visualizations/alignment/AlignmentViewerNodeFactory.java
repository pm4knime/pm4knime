package org.pm4knime.node.visualizations.alignment;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.node.visualizations.common.BundlePageResources;
import org.pm4knime.portobject.RepResultPortObjectTable;

/** Modern node viewer for table-based alignment replay results. */
public final class AlignmentViewerNodeFactory extends DefaultNodeFactory {

    private static final String ALIGNMENT_PAGE = "src/views/alignment/index.html";

    public AlignmentViewerNodeFactory() {
        super(
            DefaultNode.create()
                .name("Alignment Viewer")
                .icon("../logviews/tracevariant/trace.png")
                .shortDescription("Explore log-model alignments in an interactive view.")
                .fullDescription("""
                    <p>
                    This node displays the replay result as interactive log-model alignments.
                    </p>
                    <p>
                    The view supports case search, reliability and move-type filters, sorting, replay statistics,
                    and color-coded synchronous, log, and model moves.
                    </p>
                    """)
                .sinceVersion(2, 0, 0)
                .ports(p -> p.addInputPort(
                    "Replay Result",
                    "an alignment replay result",
                    RepResultPortObjectTable.TYPE
                ))
                .model(m -> m
                    .withoutParameters()
                    .configure(AlignmentViewerModel::configure)
                    .execute(AlignmentViewerModel::execute))
                .addView(v -> v
                    .withoutParameters()
                    .description("Alignment Viewer")
                    .page(p -> BundlePageResources.createPage(ALIGNMENT_PAGE))
                    .initialData(AlignmentViewerView::createInitialData))
                .nodeType(NodeType.Visualizer)
        );
    }
}
