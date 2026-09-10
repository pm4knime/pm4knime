package org.pm4knime.node.conformance.table.fitness;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.portobject.RepResultPortObjectTable;

public class FitnessCheckerNodeFactory extends DefaultNodeFactory {

    public FitnessCheckerNodeFactory() {
        super(
            DefaultNode.create()
                .name("Alignment-Based Fitness Evaluator")
                .icon("../../category-conformance.png")
                .shortDescription("Based on the replay result, this node outputs the statistical fitness information.")
                .fullDescription(
                    "This node computes the statistical fitness information based on the result of the "
                        + "Alignment-Based Replayer.")
                .sinceVersion(2, 0, 0)
                .ports(p -> p
                    .addInputPort("Replay Result", "replay result", RepResultPortObjectTable.TYPE)
                    .addOutputTable("Fitness Statinfo", "fitness statistical information"))
                .model(m -> m
                    .withoutParameters()
                    .configure(FitnessCheckerNodeModel::configure)
                    .execute(FitnessCheckerNodeModel::execute))
                .nodeType(NodeType.Other));
    }
}
