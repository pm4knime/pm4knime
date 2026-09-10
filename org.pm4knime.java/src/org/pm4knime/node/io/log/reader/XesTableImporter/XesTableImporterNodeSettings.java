package org.pm4knime.node.io.log.reader.XesTableImporter;

import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.widget.file.FileSelection;

public class XesTableImporterNodeSettings implements NodeParameters {

	@Widget(title = "File Location", description = "Path to the XES file to read.")
	public FileSelection m_file = new FileSelection();
}
