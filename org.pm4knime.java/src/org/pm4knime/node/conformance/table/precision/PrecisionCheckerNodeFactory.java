package org.pm4knime.node.conformance.table.precision;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.portobject.RepResultPortObjectTable;

public class PrecisionCheckerNodeFactory extends DefaultNodeFactory {

    public PrecisionCheckerNodeFactory() {
        super(
            DefaultNode.create()
                .name("Alignment-Based Precision Evaluator")
                .icon("../../category-conformance.png")
                .shortDescription("Based on the replay result, this node computes the statistical precision information.")
                .fullDescription("This node computes the statistical precision information based on the result of "
                    + "the Alignment-Based Replayer.\r\n"
                    + "        Conceptually, the precision of a process model compared to one event log is supposed "
                    + "to be (1) high when the model \r\n"
                    + "        allows for few traces not seen in the log; and (2) low when it allows for many traces "
                    + "not seen in the log.")
                .sinceVersion(2, 0, 0)
                .ports(p -> p
                    .addInputPort("Replay Result", "replay result", RepResultPortObjectTable.TYPE)
                    .addOutputTable("Precision Statinfo", "precision statistical information"))
                .model(m -> m
                    .parametersClass(PrecisionCheckerNodeSettings.class)
                    .configure((i, o) -> {
                        final var model = new PrecisionCheckerNodeModel(PrecisionCheckerNodeSettings.class);
                        model.m_settings = i.getParameters();
                        o.setOutSpecs(model.configure(i.getInPortSpecs()));
                    })
                    .execute((i, o) -> {
                        final var model = new PrecisionCheckerNodeModel(PrecisionCheckerNodeSettings.class);
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
