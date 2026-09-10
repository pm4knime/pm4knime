package org.pm4knime.node.logmanipulation.filter.knimetable;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;

public final class FilterByFrequencyTableNodeFactory extends DefaultNodeFactory {

    public FilterByFrequencyTableNodeFactory() {
        super(
            DefaultNode.create()
                .name("Filter Event Table by Frequency")
                .icon("../../category-manipulation.png")
                .shortDescription(
                    "Filters traces based on the frequency of their trace variants."
                )
                .fullDescription(
	                        "Filters an event table based on the frequency of its trace variants. "
	                        + "A trace variant represents all traces that contain the same ordered sequence of activities. "
                            + "The node can either select variants that occur at least a "
                            + "specified number of times, or select the most frequent "
                            + "variants until they cover a specified number or percentage "
                            + "of traces."
                    )            
                .sinceVersion(2, 0, 0)
                .ports(p -> p
                    .addInputTable("Event Table", "The event table to be filtered.")
                    .addOutputTable("Filtered Event Table", "The filtered event table.")
                )
                .model(m -> m
                    .parametersClass(FilterByFrequencyTableNodeSettings.class)
                    .configure((i, o) -> {
                        final var model = new FilterByFrequencyTableNodeModel(
                            FilterByFrequencyTableNodeSettings.class
                        );

                        o.setOutSpecs(
                            model.configureForDefaultNode(
                                i.getInPortSpecs(),
                                i.getParameters()
                            )
                        );
                    })
                    .execute((i, o) -> {
                        final var model = new FilterByFrequencyTableNodeModel(
                            FilterByFrequencyTableNodeSettings.class
                        );

                        try {
                            o.setOutData(
                                model.executeForDefaultNode(
                                    i.getInPortObjects(),
                                    i.getExecutionContext(),
                                    i.getParameters()
                                )
                            );
                        } catch (Exception ex) {
                            throw new RuntimeException(
                                ex
                            );
                        }
                    })
                )
                .nodeType(NodeType.Manipulator)
        );
    }
}