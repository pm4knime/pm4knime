package org.pm4knime.node.io.log.reader.XesTableImporter;

import org.apache.commons.lang3.StringUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.node.DefaultModel;

final class XesTableImporterNodeModel {

	private XesTableImporterNodeModel() {
	}

	static void configure(final DefaultModel.ConfigureInput i, final DefaultModel.ConfigureOutput o)
			throws InvalidSettingsException {
		validate(i.getParameters());
		o.setOutSpec(0, null);
		o.setOutSpec(1, null);
	}

	static void execute(final DefaultModel.ExecuteInput i, final DefaultModel.ExecuteOutput o) {
		try {
			final XesTableImporterNodeSettings settings = i.getParameters();
			validate(settings);

			final XesStreamingTableReader reader = new XesStreamingTableReader(settings.m_file);
			final XesStreamingTableReader.TableMetadata metadata =
					reader.scan(i.getExecutionContext());

			final DataTableSpec eventSpec = metadata.createEventSpec();
			final DataTableSpec caseSpec = metadata.createCaseSpec();

			final BufferedDataContainer eventContainer =
					i.getExecutionContext().createDataContainer(eventSpec, false);
			final BufferedDataContainer caseContainer =
					i.getExecutionContext().createDataContainer(caseSpec, false);

			reader.writeTables(metadata, eventContainer, caseContainer, i.getExecutionContext());

			eventContainer.close();
			caseContainer.close();

			final BufferedDataTable eventTable = eventContainer.getTable();
			final BufferedDataTable caseTable = caseContainer.getTable();
			o.setOutData(0, eventTable);
			o.setOutData(1, caseTable);
			o.setInternalData(eventTable);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static void validate(final XesTableImporterNodeSettings settings)
			throws InvalidSettingsException {
		final String path = settings.m_file.getFSLocation().getPath();
		if (StringUtils.isEmpty(path)) {
			throw new InvalidSettingsException("Please specify a path to the file to read.");
		}

		final String lowerPath = path.toLowerCase();
		if (!StringUtils.endsWithAny(lowerPath, ".xes", ".xes.gz")) {
			throw new InvalidSettingsException("Unsupported file type: Please select a .xes or .xes.gz file.");
		}
	}
}
