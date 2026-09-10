package org.pm4knime.node.io.log.reader.XesTableImporter;

import org.knime.node.DefaultNode;
import org.knime.node.DefaultNodeFactory;

public class XesTableImporterNodeFactory extends DefaultNodeFactory {

	public XesTableImporterNodeFactory() {
		super(
			DefaultNode.create()
				.name("XES Reader")
				.icon("../../../read.png")
				.shortDescription("This node imports an XES event log directly as KNIME tables.")
				.fullDescription("This node imports an XES event log directly as KNIME event and case tables. ")
				.sinceVersion(3, 0, 0)
				.ports(p -> p
					.addOutputTable("Event Table", "events in the same order as they appear in the XES file")
					.addOutputTable("Case Table", "cases in the same order as traces appear in the XES file"))
				.model(m -> m
					.parametersClass(XesTableImporterNodeSettings.class)
					.configure(XesTableImporterNodeModel::configure)
					.execute(XesTableImporterNodeModel::execute))
				.nodeType(NodeType.Source));
	}
}
