package org.pm4knime.node.conformance.table.performance;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.portobject.RepResultPortObjectTable;

public class PerformanceCheckerNodeFactory extends DefaultNodeFactory {

    public PerformanceCheckerNodeFactory() {
        super(
            DefaultNode.create()
                .name("Alignment-Based Performance Evaluator")
                .icon("../../category-conformance.png")
                .shortDescription(
                    "Based on the replay result, this node computes the statistical performance information.")
                .fullDescription("This node computes statistical performance information based on the result of "
                    + "the Alignment-Based Replayer.\r\n"
                    + "        The output tables report global, transition-level, and place-level performance "
                    + "statistics such as waiting time, synchronization time, and sojourn time.")
                .sinceVersion(2, 0, 0)
                .ports(p -> p
                    .addInputPort("Replay Result", "replay result", RepResultPortObjectTable.TYPE)
                    .addOutputTable("Global Performance StatInfo",
                        "global performance statistical information.")
                    .addOutputTable("Transition Performance Statistics",
                        "performance statistical information for the different transitions in the Petri net "
                            + "(waiting time, synchronization time, and sojourn time).")
                    .addOutputTable("Place Performance Statistics",
                        "performance statistical information for the different places in the Petri net "
                            + "(waiting time, synchronization time, and sojourn time)."))
                .model(m -> m
                    .parametersClass(PerformanceCheckerNodeSettings.class)
                    .configure((i, o) -> {
                        final var model = new PerformanceCheckerNodeModel(PerformanceCheckerNodeSettings.class);
                        model.m_settings = i.getParameters();
                        o.setOutSpecs(model.configure(i.getInPortSpecs()));
                    })
                    .execute((i, o) -> {
                        final var model = new PerformanceCheckerNodeModel(PerformanceCheckerNodeSettings.class);
                        model.m_settings = i.getParameters();
                        try {
                            o.setOutData(model.execute(i.getInPortObjects(), i.getExecutionContext()));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }))
                .nodeType(NodeType.Other));
    }
}
