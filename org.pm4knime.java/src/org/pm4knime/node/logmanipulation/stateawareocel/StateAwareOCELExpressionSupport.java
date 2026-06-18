package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.OptionalInt;
import java.util.function.Function;

import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.LongValue;
import org.knime.core.data.StringValue;
import org.knime.core.data.time.duration.DurationValue;
import org.knime.core.data.time.localdate.LocalDateValue;
import org.knime.core.data.time.localdatetime.LocalDateTimeValue;
import org.knime.core.data.time.localtime.LocalTimeValue;
import org.knime.core.data.time.period.PeriodValue;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeValue;
import org.knime.core.expressions.Ast;
import org.knime.core.expressions.ReturnResult;
import org.knime.core.expressions.ValueType;

/** Shared expression-language utilities used by the node model and WebUI diagnostics. */
@SuppressWarnings("restriction")
final class StateAwareOCELExpressionSupport {

    private StateAwareOCELExpressionSupport() {
        // utility class
    }

    static Function<String, ReturnResult<ValueType>> columnToTypeForTypeInference(final DataTableSpec spec) {
        return name -> ReturnResult.fromNullable(spec, "No input data is available.")
            .flatMap(s -> ReturnResult.fromNullable(s.getColumnSpec(name),
                "No column with the name '" + name + "' is available."))
            .map(DataColumnSpec::getType)
            .flatMap(type -> ReturnResult.fromNullable(mapDataTypeToValueType(type),
                "Columns of the type '" + type + "' are not supported in state expressions."));
    }

    static Function<String, ReturnResult<ValueType>> flowVariableToTypeForTypeInference() {
        return name -> ReturnResult.failure("Flow variables are not supported by the State-Aware OCEL Enricher. "
            + "Append the flow variable value as a table column before this node if it should be used in a state "
            + "expression.");
    }

    static ValueType mapDataTypeToValueType(final DataType type) {
        if (type == null) {
            return null;
        } else if (type.isCompatible(BooleanValue.class)) {
            return ValueType.OPT_BOOLEAN;
        } else if (type.isCompatible(LongValue.class)) {
            return ValueType.OPT_INTEGER;
        } else if (type.isCompatible(DoubleValue.class)) {
            return ValueType.OPT_FLOAT;
        } else if (type.isCompatible(LocalDateValue.class)) {
            return ValueType.OPT_LOCAL_DATE;
        } else if (type.isCompatible(LocalTimeValue.class)) {
            return ValueType.OPT_LOCAL_TIME;
        } else if (type.isCompatible(LocalDateTimeValue.class)) {
            return ValueType.OPT_LOCAL_DATE_TIME;
        } else if (type.isCompatible(ZonedDateTimeValue.class)) {
            return ValueType.OPT_ZONED_DATE_TIME;
        } else if (type.isCompatible(DurationValue.class)) {
            return ValueType.OPT_TIME_DURATION;
        } else if (type.isCompatible(PeriodValue.class)) {
            return ValueType.OPT_DATE_DURATION;
        } else if (type.isCompatible(StringValue.class)) {
            return ValueType.OPT_STRING;
        } else {
            return null;
        }
    }

    static String inputOutputModelTypeName(final DataType type) {
        final ValueType valueType = mapDataTypeToValueType(type);
        return valueType == null ? "UNKNOWN" : valueType.baseType().name();
    }

    static boolean containsAggregation(final Ast ast) {
        if (ast instanceof Ast.AggregationCall) {
            return true;
        }
        for (final Ast child : ast.children()) {
            if (containsAggregation(child)) {
                return true;
            }
        }
        return false;
    }

    static OptionalInt resolveColumnIndex(final DataTableSpec spec, final Ast.ColumnAccess columnAccess) {
        if (columnAccess.columnId().type() != Ast.ColumnId.ColumnIdType.NAMED) {
            return OptionalInt.of(0);
        }
        final int idx = spec.findColumnIndex(columnAccess.columnId().name());
        return idx < 0 ? OptionalInt.empty() : OptionalInt.of(idx);
    }
}
