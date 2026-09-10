package org.pm4knime.node.io.log.reader.XesImporter;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;
import org.pm4knime.portobject.XLogPortObject;

public class XesImporterNodeFactory extends DefaultNodeFactory {

    public XesImporterNodeFactory() {
        super(
            DefaultNode.create()
                .name("XES Reader (Legacy Event Log)")
                .icon("../../../read.png")
                .shortDescription("Deprecated: imports an XES file as an event-log port object.")
                .fullDescription("Deprecated: this node imports an XES file as an event-log port object for legacy "
                    + "workflows and ProM-based views. Use the table-based XES Reader for large logs.")
                .sinceVersion(2, 0, 0)
                .ports(p -> p.addOutputPort("Event Log", "an event log", XLogPortObject.TYPE))
                .model(m -> m
                    .parametersClass(XesImporterNodeSettings.class)
                    .configure((i, o) -> {
                        final var model = new XesImporterNodeModel(XesImporterNodeSettings.class);
                        model.setSettings(i.getParameters());
                        o.setOutSpecs(model.configure(i.getInPortSpecs()));
                    })
                    .execute((i, o) -> {
                        final var model = new XesImporterNodeModel(XesImporterNodeSettings.class);
                        model.setSettings(i.getParameters());
                        try {
                            o.setOutData(model.execute(i.getInPortObjects(), i.getExecutionContext()));
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }))
                .nodeType(NodeType.Source));
    }
}
