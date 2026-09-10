package org.pm4knime.node.io.log.reader.XesTableImporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.BooleanCell.BooleanCellFactory;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.DoubleCell.DoubleCellFactory;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.IntCell.IntCellFactory;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.def.StringCell.StringCellFactory;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCell;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCellFactory;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionMonitor;
import org.knime.filehandling.core.connections.FSConnection;
import org.knime.filehandling.core.connections.FSFileSystem;
import org.knime.filehandling.core.connections.FSFiles;
import org.knime.filehandling.core.connections.FSLocation;
import org.knime.filehandling.core.defaultnodesettings.FileSystemHelper;
import org.knime.node.parameters.widget.file.FileSelection;
import org.pm4knime.util.XLogSpecUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

final class XesStreamingTableReader {

	private static final String EVENT_TABLE_NAME = "Event Table from XES";
	private static final String CASE_TABLE_NAME = "Case Table from XES";
	private static final int TRACE_PROGRESS_INTERVAL = 1_000;
	private static final long EVENT_PROGRESS_INTERVAL = 100_000L;

	private final FSLocation m_location;

	XesStreamingTableReader(final FileSelection fileSelection) {
		m_location = fileSelection.getFSLocation();
	}

	TableMetadata scan(final ExecutionMonitor exec) throws Exception {
		final TableMetadata metadata = new TableMetadata();
		exec.setProgress("Scanning XES attributes");
		parse(new ScanHandler(metadata, exec));
		return metadata;
	}

	void writeTables(final TableMetadata metadata, final BufferedDataContainer eventContainer,
			final BufferedDataContainer caseContainer, final ExecutionMonitor exec) throws Exception {
		exec.setProgress("Reading XES events");
		parse(new WriteHandler(metadata, eventContainer, caseContainer, exec));
	}

	private void parse(final DefaultHandler handler) throws Exception {
		final SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(false);
		setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
		setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
		setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
		setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		final SAXParser parser = factory.newSAXParser();
		try (InputStream input = openInputStream()) {
			parser.parse(input, handler);
		}
	}

	private static void setFeature(final SAXParserFactory factory, final String feature, final boolean value) {
		try {
			factory.setFeature(feature, value);
		} catch (Exception ignored) {
			// Some JAXP implementations do not recognize every hardening feature.
		}
	}

	private InputStream openInputStream() throws IOException {
		final FSConnection connection = FileSystemHelper.retrieveFSConnection(Optional.empty(), m_location)
				.orElseThrow(() -> new IOException("File system is not available"));
		final FSFileSystem<?> fileSystem = connection.getFileSystem();
		final Path path = fileSystem.getPath(m_location);
		final InputStream input = FSFiles.newInputStream(path);
		final String lowerPath = m_location.getPath().toLowerCase();
		if (lowerPath.endsWith(".gz") || lowerPath.endsWith(".xez")) {
			return new GZIPInputStream(input);
		}
		return input;
	}

	static final class TableMetadata {
		private final LinkedHashMap<String, XesAttributeType> m_traceColumns = new LinkedHashMap<>();
		private final LinkedHashMap<String, XesAttributeType> m_eventColumns = new LinkedHashMap<>();
		private long m_traceCount;
		private long m_eventCount;

		void addTraceAttribute(final String key, final XesAttributeType type) {
			addAttribute(m_traceColumns, XLogSpecUtil.TRACE_ATTRIBUTE_PREFIX + key, type);
		}

		void addEventAttribute(final String key, final XesAttributeType type) {
			addAttribute(m_eventColumns, XLogSpecUtil.EVENT_ATTRIBUTE_PREFIX + key, type);
		}

		private static void addAttribute(final LinkedHashMap<String, XesAttributeType> columns,
				final String columnName, final XesAttributeType type) {
			columns.merge(columnName, type, XesAttributeType::merge);
		}

		DataTableSpec createEventSpec() {
			final String[] names = new String[m_traceColumns.size() + m_eventColumns.size()];
			final DataType[] types = new DataType[names.length];
			int index = 0;
			for (Map.Entry<String, XesAttributeType> entry : m_traceColumns.entrySet()) {
				names[index] = entry.getKey();
				types[index] = entry.getValue().toDataType();
				index++;
			}
			for (Map.Entry<String, XesAttributeType> entry : m_eventColumns.entrySet()) {
				names[index] = entry.getKey();
				types[index] = entry.getValue().toDataType();
				index++;
			}
			return new DataTableSpec(EVENT_TABLE_NAME, names, types);
		}

		DataTableSpec createCaseSpec() {
			final String[] names = new String[m_traceColumns.size()];
			final DataType[] types = new DataType[names.length];
			int index = 0;
			for (Map.Entry<String, XesAttributeType> entry : m_traceColumns.entrySet()) {
				names[index] = entry.getKey();
				types[index] = entry.getValue().toDataType();
				index++;
			}
			return new DataTableSpec(CASE_TABLE_NAME, names, types);
		}

		Map<String, Integer> createTraceIndexMap() {
			return createIndexMap(m_traceColumns);
		}

		Map<String, Integer> createEventIndexMap() {
			final LinkedHashMap<String, XesAttributeType> eventTableColumns = new LinkedHashMap<>();
			eventTableColumns.putAll(m_traceColumns);
			eventTableColumns.putAll(m_eventColumns);
			return createIndexMap(eventTableColumns);
		}

		private static Map<String, Integer> createIndexMap(final LinkedHashMap<String, XesAttributeType> columns) {
			final Map<String, Integer> indices = new LinkedHashMap<>();
			int index = 0;
			for (String column : columns.keySet()) {
				indices.put(column, index++);
			}
			return indices;
		}
	}

	private static final class ScanHandler extends XesHandler {
		private final TableMetadata m_metadata;
		private final ExecutionMonitor m_exec;
		private long m_seenEvents;

		ScanHandler(final TableMetadata metadata, final ExecutionMonitor exec) {
			m_metadata = metadata;
			m_exec = exec;
		}

		@Override
		protected void onTraceStart() {
			m_metadata.m_traceCount++;
		}

		@Override
		protected void onEventStart() throws SAXException {
			m_metadata.m_eventCount++;
			m_seenEvents++;
			if (m_seenEvents % EVENT_PROGRESS_INTERVAL == 0) {
				try {
					m_exec.checkCanceled();
					m_exec.setProgress("Scanned " + m_seenEvents + " events");
				} catch (CanceledExecutionException ex) {
					throw new SAXException(ex);
				}
			}
		}

		@Override
		protected void onTraceAttribute(final String key, final XesAttributeType type, final String value) {
			m_metadata.addTraceAttribute(key, type);
		}

		@Override
		protected void onEventAttribute(final String key, final XesAttributeType type, final String value) {
			m_metadata.addEventAttribute(key, type);
		}

		@Override
		protected void onGlobalTraceAttribute(final String key, final XesAttributeType type, final String value) {
			m_metadata.addTraceAttribute(key, type);
		}

		@Override
		protected void onGlobalEventAttribute(final String key, final XesAttributeType type, final String value) {
			m_metadata.addEventAttribute(key, type);
		}
	}

	private static final class WriteHandler extends XesHandler {
		private final TableMetadata m_metadata;
		private final BufferedDataContainer m_eventContainer;
		private final BufferedDataContainer m_caseContainer;
		private final ExecutionMonitor m_exec;
		private final Map<String, Integer> m_traceIndices;
		private final Map<String, Integer> m_eventIndices;
		private final DataCell[] m_emptyCaseCells;
		private final DataCell[] m_emptyEventCells;

		private DataCell[] m_caseCells;
		private DataCell[] m_eventTraceCells;
		private DataCell[] m_eventCells;
		private long m_caseCount;
		private long m_eventCount;

		WriteHandler(final TableMetadata metadata, final BufferedDataContainer eventContainer,
				final BufferedDataContainer caseContainer, final ExecutionMonitor exec) {
			m_metadata = metadata;
			m_eventContainer = eventContainer;
			m_caseContainer = caseContainer;
			m_exec = exec;
			m_traceIndices = metadata.createTraceIndexMap();
			m_eventIndices = metadata.createEventIndexMap();

			final DataCell missing = DataType.getMissingCell();
			m_emptyCaseCells = new DataCell[m_traceIndices.size()];
			Arrays.fill(m_emptyCaseCells, missing);
			m_emptyEventCells = new DataCell[m_eventIndices.size()];
			Arrays.fill(m_emptyEventCells, missing);
		}

		@Override
		protected void onTraceStart() throws SAXException {
			try {
				m_exec.checkCanceled();
			} catch (CanceledExecutionException ex) {
				throw new SAXException(ex);
			}
			m_caseCells = m_emptyCaseCells.clone();
			m_eventTraceCells = m_emptyEventCells.clone();
		}

		@Override
		protected void onEventStart() {
			m_eventCells = m_eventTraceCells.clone();
		}

		@Override
		protected void onTraceEnd() {
			m_caseContainer.addRowToTable(new DefaultRow("Case " + (m_caseCount++), m_caseCells));
			m_caseCells = null;
			m_eventTraceCells = null;

			if (m_caseCount % TRACE_PROGRESS_INTERVAL == 0 || m_caseCount == m_metadata.m_traceCount) {
				final double progress = m_metadata.m_traceCount > 0
						? (double)m_caseCount / m_metadata.m_traceCount
						: 1.0;
				m_exec.setProgress(progress, "Read " + m_caseCount + " of "
						+ m_metadata.m_traceCount + " traces");
			}
		}

		@Override
		protected void onEventEnd() throws SAXException {
			m_eventContainer.addRowToTable(new DefaultRow("Event " + (m_eventCount++), m_eventCells));
			m_eventCells = null;

			if (m_eventCount % EVENT_PROGRESS_INTERVAL == 0) {
				try {
					m_exec.checkCanceled();
					m_exec.setProgress("Read " + m_eventCount + " events");
				} catch (CanceledExecutionException ex) {
					throw new SAXException(ex);
				}
			}
		}

		@Override
		protected void onTraceAttribute(final String key, final XesAttributeType type, final String value) {
			final String columnName = XLogSpecUtil.TRACE_ATTRIBUTE_PREFIX + key;
			final Integer caseIndex = m_traceIndices.get(columnName);
			final Integer eventIndex = m_eventIndices.get(columnName);
			if (caseIndex == null && eventIndex == null) {
				return;
			}

			final XesAttributeType columnType = m_metadata.m_traceColumns.get(columnName);
			final DataCell cell = createCell(value, columnType);
			if (caseIndex != null) {
				m_caseCells[caseIndex] = cell;
			}
			if (eventIndex != null) {
				m_eventTraceCells[eventIndex] = cell;
			}
		}

		@Override
		protected void onEventAttribute(final String key, final XesAttributeType type, final String value) {
			final String columnName = XLogSpecUtil.EVENT_ATTRIBUTE_PREFIX + key;
			final Integer index = m_eventIndices.get(columnName);
			if (index == null) {
				return;
			}

			final XesAttributeType columnType = m_metadata.m_eventColumns.get(columnName);
			m_eventCells[index] = createCell(value, columnType);
		}
	}

	private abstract static class XesHandler extends DefaultHandler {
		private static final String GLOBAL = "global";
		private static final String TRACE = "trace";
		private static final String EVENT = "event";

		private final java.util.ArrayDeque<String> m_stack = new java.util.ArrayDeque<>();
		private String m_globalScope;

		@Override
		public final void startElement(final String uri, final String localName, final String qName,
				final Attributes attributes) throws SAXException {
			final String element = elementName(localName, qName);
			final String parent = m_stack.peek();

			if (GLOBAL.equals(element)) {
				m_globalScope = attributes.getValue("scope");
			} else if (TRACE.equals(element)) {
				onTraceStart();
			} else if (EVENT.equals(element)) {
				onEventStart();
			}

			final XesAttributeType type = XesAttributeType.fromElement(element);
			if (type != null && isTopLevelAttribute(parent)) {
				final String key = attributes.getValue("key");
				if (key != null) {
					final String value = attributes.getValue("value");
					if (TRACE.equals(parent)) {
						onTraceAttribute(key, type, value);
					} else if (EVENT.equals(parent)) {
						onEventAttribute(key, type, value);
					} else if (GLOBAL.equals(parent) && TRACE.equals(m_globalScope)) {
						onGlobalTraceAttribute(key, type, value);
					} else if (GLOBAL.equals(parent) && EVENT.equals(m_globalScope)) {
						onGlobalEventAttribute(key, type, value);
					}
				}
			}

			m_stack.push(element);
		}

		@Override
		public final void endElement(final String uri, final String localName, final String qName)
				throws SAXException {
			final String element = elementName(localName, qName);
			if (EVENT.equals(element)) {
				onEventEnd();
			} else if (TRACE.equals(element)) {
				onTraceEnd();
			} else if (GLOBAL.equals(element)) {
				m_globalScope = null;
			}
			m_stack.pop();
		}

		private static boolean isTopLevelAttribute(final String parent) {
			return TRACE.equals(parent) || EVENT.equals(parent) || GLOBAL.equals(parent);
		}

		private static String elementName(final String localName, final String qName) {
			return localName == null || localName.isEmpty() ? qName : localName;
		}

		protected void onTraceStart() throws SAXException {
		}

		protected void onTraceEnd() throws SAXException {
		}

		protected void onEventStart() throws SAXException {
		}

		protected void onEventEnd() throws SAXException {
		}

		protected void onTraceAttribute(final String key, final XesAttributeType type, final String value)
				throws SAXException {
		}

		protected void onEventAttribute(final String key, final XesAttributeType type, final String value)
				throws SAXException {
		}

		protected void onGlobalTraceAttribute(final String key, final XesAttributeType type, final String value)
				throws SAXException {
		}

		protected void onGlobalEventAttribute(final String key, final XesAttributeType type, final String value)
				throws SAXException {
		}
	}

	private enum XesAttributeType {
		STRING,
		BOOLEAN,
		INTEGER,
		DOUBLE,
		DATE;

		static XesAttributeType fromElement(final String element) {
			if ("string".equals(element) || "id".equals(element) || "list".equals(element)) {
				return STRING;
			} else if ("boolean".equals(element)) {
				return BOOLEAN;
			} else if ("int".equals(element)) {
				return INTEGER;
			} else if ("float".equals(element)) {
				return DOUBLE;
			} else if ("date".equals(element)) {
				return DATE;
			}
			return null;
		}

		static XesAttributeType merge(final XesAttributeType first, final XesAttributeType second) {
			if (first == second) {
				return first;
			}
			if ((first == INTEGER && second == DOUBLE) || (first == DOUBLE && second == INTEGER)) {
				return DOUBLE;
			}
			return STRING;
		}

		DataType toDataType() {
			switch (this) {
				case BOOLEAN:
					return BooleanCell.TYPE;
				case INTEGER:
					return IntCell.TYPE;
				case DOUBLE:
					return DoubleCell.TYPE;
				case DATE:
					return DataType.getType(ZonedDateTimeCell.class);
				case STRING:
				default:
					return StringCell.TYPE;
			}
		}
	}

	private static DataCell createCell(final String value, final XesAttributeType type) {
		if (value == null) {
			return DataType.getMissingCell();
		}

		try {
			switch (type) {
				case BOOLEAN:
					return BooleanCellFactory.create(Boolean.parseBoolean(value));
				case INTEGER:
					return IntCellFactory.create(Math.toIntExact(Long.parseLong(value)));
				case DOUBLE:
					return DoubleCellFactory.create(Double.parseDouble(value));
				case DATE:
					return ZonedDateTimeCellFactory.create(parseDate(value));
				case STRING:
				default:
					return StringCellFactory.create(value);
			}
		} catch (RuntimeException ex) {
			return DataType.getMissingCell();
		}
	}

	private static ZonedDateTime parseDate(final String value) {
		try {
			return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toZonedDateTime();
		} catch (DateTimeParseException ignored) {
			// Try a local timestamp below.
		}
		return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).atZone(ZoneId.systemDefault());
	}
}
