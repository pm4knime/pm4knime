package org.pm4knime.node.conformance.replayer.table.helper;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.portobject.PetriNetPortObject;
import org.pm4knime.portobject.RepResultPortObjectTable;

public class PNReplayerTableNodeFactory extends DefaultNodeFactory {

    public PNReplayerTableNodeFactory() {
        super(
            DefaultNode.create()
                .name("Alignment-Based Replayer")
                .icon("../../../category-conformance.png")
                .shortDescription("This node implements the alignment-based replayer for constructing the optimal "
                    + "alignments by replaying an event log on a Petri net.")
                .fullDescription("This node implements the alignment-based replayer, which accepts an event log and "
                    + "a Petri net as input and outputs the optimal alignments of the traces after replaying the event "
                    + "log on the Petri net.\r\n"
                    + "Alignment-based replay is the state-of-the-art technique in conformance checking.\r\n"
                    + "Alignments provide a robust and detailed view on the deviations between the log and the model.\r\n"
                    + "\r\n"
                    + "Three types of moves are considered while constructing the alignments:\r\n"
                    + "<ul>\r\n"
                    + "<li>Sync Move: The classification of the current event corresponds to the firing transitions "
                    + "in Petri net. During replaying, both the trace and the process model move to next comparison."
                    + "</li>\r\n"
                    + "<li>Log Move: The classification of the current event doesn't have any corresponding firing "
                    + "transitions in Petri net. During replaying, the trace moves forward but the state of process "
                    + "model doesn't change.</li>\r\n"
                    + "<li>Model Move: The firing transitions in Petri net have no corresponding event in the trace. "
                    + "During replaying, the model moves forwards but the state of the event log doesn't change.</li>\r\n"
                    + "</ul>")
                .sinceVersion(2, 0, 0)
                .ports(p -> p
                    .addInputTable("Event Log", "an event log")
                    .addInputPort("Petri Net", "a Petri net", PetriNetPortObject.TYPE)
                    .addOutputPort("Replay Result", "replay result", RepResultPortObjectTable.TYPE))
                .model(m -> m
                    .parametersClass(PNReplayerTableNodeSettings.class)
                    .configure((i, o) -> {
                        final var model = new DefaultPNReplayerTableModel(PNReplayerTableNodeSettings.class);
                        model.setSettings(i.getParameters());
                        o.setOutSpecs(model.configure(i.getInPortSpecs()));
                    })
                    .execute((i, o) -> {
                        final var model = new DefaultPNReplayerTableModel(PNReplayerTableNodeSettings.class);
                        model.setSettings(i.getParameters());
                        try {
                            o.setOutData(model.execute(i.getInPortObjects(), i.getExecutionContext()));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }))
                .addExternalResource(
                	    "https://doi.org/10.1109/EDOC.2011.12",
                	    "Conformance Checking Using Cost-Based Fitness Analysis"
                	)
                .nodeType(NodeType.Other));
    }
}
