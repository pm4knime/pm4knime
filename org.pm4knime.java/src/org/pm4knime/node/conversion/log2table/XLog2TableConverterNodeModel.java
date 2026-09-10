package org.pm4knime.node.conversion.log2table;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.deckfour.xes.model.impl.XAttributeBooleanImpl;
import org.deckfour.xes.model.impl.XAttributeContinuousImpl;
import org.deckfour.xes.model.impl.XAttributeDiscreteImpl;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeTimestampImpl;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCell;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.node.DefaultModel;
import org.pm4knime.portobject.XLogPortObject;
import org.pm4knime.portobject.XLogPortObjectSpec;
import org.pm4knime.util.XLogSpecUtil;

/**
 * <code>NodeModel</code> for the "Xlog2CSVConverter" node. If we could change XLog into DataTable,
 * we could convert it into CSV by using default CSV Writer in KNIME. Now, the thing here is how to convert
 * XLog into DataTable format.
 *
 * We need to have all the attributes of event class; It should include the attribute for trace, too.
 * If we use classifier, how could we store it into the DataTable??
 * -- get attributes and create table spec
 * -- fill each row for one event class
 *
 * ++ convert the DataTable into CSV files. But it is actually based on Datatable, we can operate on the DATATABEL.
 * TableSpec, but how to fill it there?? Without data, but only the possible data there, check the csv reader codes
 * In ProM codes, it convert log directly into CVS files. It converts event log into string and output strings
 *
 * Here, we need more extensions to say if we need this, or not. We just convert it directly to CSV file??
 * They are only DataTable conversion!! Rename the nodes, please!!!
 *
 * @author Kefang
 */
@SuppressWarnings("restriction")
final class XLog2TableConverterNodeModel {
    private static final NodeLogger LOGGER = NodeLogger.getLogger(XLog2TableConverterNodeModel.class);

    static final String CFG_TABLE_NAME = "Converted Data Table from Event Log";

    private XLog2TableConverterNodeModel() {
    }

    static void configure(final DefaultModel.ConfigureInput i, final DefaultModel.ConfigureOutput o)
        throws InvalidSettingsException {

        if (!(i.getInPortSpec(0) instanceof XLogPortObjectSpec)) {
            throw new InvalidSettingsException("Input is not a valid Event Log!");
        }

        final XLogPortObjectSpec spec = (XLogPortObjectSpec)i.getInPortSpec(0);
        if (!hasCompleteSpec(spec)) {
            o.setOutSpec(0, null);
            o.setOutSpec(1, null);
            return;
        }
        o.setOutSpec(0, createEventSpec(spec));
        o.setOutSpec(1, createCaseSpec(spec));
    }

    static void execute(final DefaultModel.ExecuteInput i, final DefaultModel.ExecuteOutput o) {
        try {
            LOGGER.info("Start : Convert Event log to DataTable");
            final XLogPortObject logPortObject = (XLogPortObject)i.getInPortObject(0);
            final XLogPortObjectSpec spec = ensureCompleteSpec(logPortObject);

            final DataTableSpec eventSpec = createEventSpec(spec);
            final DataTableSpec caseSpec = createCaseSpec(spec);

            final BufferedDataContainer eventBuf = i.getExecutionContext().createDataContainer(eventSpec, false);
            final BufferedDataContainer caseBuf = i.getExecutionContext().createDataContainer(caseSpec, false);

            FromXLogConverter.convert(logPortObject.getLog(), eventBuf, caseBuf, i.getExecutionContext());

            eventBuf.close();
            caseBuf.close();
            LOGGER.info("End : Convert Event log to DataTable");

            final BufferedDataTable eventTable = eventBuf.getTable();
            final BufferedDataTable caseTable = caseBuf.getTable();
            o.setOutData(0, eventTable);
            o.setOutData(1, caseTable);
            o.setInternalData(eventTable);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static boolean hasCompleteSpec(final XLogPortObjectSpec spec) {
        return spec != null && spec.getGTraceAttrMap() != null && spec.getGEventAttrMap() != null;
    }

    private static XLogPortObjectSpec ensureCompleteSpec(final XLogPortObject logPortObject) {
        final XLogPortObjectSpec spec = (XLogPortObjectSpec)logPortObject.getSpec();
        if (hasCompleteSpec(spec)) {
            return spec;
        }

        final XLogPortObjectSpec extractedSpec = XLogSpecUtil.extractSpec(logPortObject.getLog());
        logPortObject.setSpec(extractedSpec);
        return extractedSpec;
    }

    private static DataTableSpec createEventSpec(final XLogPortObjectSpec spec) {
        final List<String> attrNames = new ArrayList<>();
        final List<DataType> attrTypes = new ArrayList<>();

        for (final String attrKey : spec.getGTraceAttrMap().keySet()) {
            attrNames.add(attrKey);
            attrTypes.add(findDataType(spec.getGTraceAttrMap().get(attrKey).getSimpleName()));
        }

        for (final String attrKey : spec.getGEventAttrMap().keySet()) {
            attrNames.add(attrKey);
            attrTypes.add(findDataType(spec.getGEventAttrMap().get(attrKey).getSimpleName()));
        }

        return new DataTableSpec(
            CFG_TABLE_NAME,
            attrNames.toArray(new String[0]),
            attrTypes.toArray(new DataType[0]));
    }

    private static DataTableSpec createCaseSpec(final XLogPortObjectSpec spec) {
        final List<String> attrNames = new ArrayList<>();
        final List<DataType> attrTypes = new ArrayList<>();

        final Set<String> specTraceColumns = spec.getGTraceAttrMap().keySet();
        for (final String attrKey : specTraceColumns) {
            attrNames.add(attrKey);
            attrTypes.add(findDataType(spec.getGTraceAttrMap().get(attrKey).getSimpleName()));
        }

        return new DataTableSpec(
            "Case Table",
            attrNames.toArray(new String[0]),
            attrTypes.toArray(new DataType[0]));
    }

    private static DataType findDataType(final String cls) {
        if (cls.equals(XAttributeLiteralImpl.class.getSimpleName())) {
            return StringCell.TYPE;
        } else if (cls.equals(XAttributeBooleanImpl.class.getSimpleName())) {
            return BooleanCell.TYPE;
        } else if (cls.equals(XAttributeDiscreteImpl.class.getSimpleName())) {
            return IntCell.TYPE;
        } else if (cls.equals(XAttributeContinuousImpl.class.getSimpleName())) {
            return DoubleCell.TYPE;
        } else if (cls.equals(XAttributeTimestampImpl.class.getSimpleName())) {
            return DataType.getType(ZonedDateTimeCell.class);
        } else {
            System.out.println("The attribute is not recognized");
        }
        return null;
    }
}
