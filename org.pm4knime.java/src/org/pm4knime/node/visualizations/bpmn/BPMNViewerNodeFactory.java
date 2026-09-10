package org.pm4knime.node.visualizations.bpmn;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.node.visualizations.common.ModernViews;
import org.pm4knime.portobject.BpmnPortObject;

public class BPMNViewerNodeFactory extends DefaultNodeFactory {

    public BPMNViewerNodeFactory() {
        super(
            DefaultNode.create()
                .name("BPMN Viewer")
                .icon("../jsgraphviz/process.png")
                .shortDescription("Open an interactive viewer for BPMN models.")
                .fullDescription("""
                    <p>
                    This node opens an interactive viewer for BPMN models.
                    </p>
                    <p>
                    The viewer supports zooming, resetting the view, fitting the diagram to the viewport, and downloading the diagram as SVG.
                    </p>
                    """)
                .sinceVersion(2, 0, 0)
                .ports(p -> p
                    .addInputPort("BPMN", "a BPMN model", BpmnPortObject.TYPE))
                .model(m -> m
                    .withoutParameters()
                    .configure(BPMNVisualizerModel::configure)
                    .execute(BPMNVisualizerModel::execute))
                .addView(v -> ModernViews.bpmn(v, BPMNViewerNodeFactory.class, "BPMN view"))
                .nodeType(NodeType.Visualizer));
    }
}
