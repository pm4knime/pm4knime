package org.pm4knime.node.logmanipulation.stateawareocel;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.knime.core.data.BooleanValue;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataColumnSpecCreator;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.LongValue;
import org.knime.core.data.RowKey;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.BooleanCell;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.time.duration.DurationValue;
import org.knime.core.data.time.localdate.LocalDateValue;
import org.knime.core.data.time.localdatetime.LocalDateTimeCellFactory;
import org.knime.core.data.time.localdatetime.LocalDateTimeValue;
import org.knime.core.data.time.localtime.LocalTimeValue;
import org.knime.core.data.time.period.PeriodValue;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCellFactory;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeValue;
import org.knime.core.expressions.Ast;
import org.knime.core.expressions.Computer;
import org.knime.core.expressions.Computer.BooleanComputer;
import org.knime.core.expressions.Computer.BooleanComputerResultSupplier;
import org.knime.core.expressions.Computer.DateDurationComputer;
import org.knime.core.expressions.Computer.FloatComputer;
import org.knime.core.expressions.Computer.IntegerComputer;
import org.knime.core.expressions.Computer.LocalDateComputer;
import org.knime.core.expressions.Computer.LocalDateTimeComputer;
import org.knime.core.expressions.Computer.LocalTimeComputer;
import org.knime.core.expressions.Computer.StringComputer;
import org.knime.core.expressions.Computer.TimeDurationComputer;
import org.knime.core.expressions.Computer.ZonedDateTimeComputer;
import org.knime.core.expressions.EvaluationContext;
import org.knime.core.expressions.ExpressionCompileException;
import org.knime.core.expressions.ExpressionEvaluationException;
import org.knime.core.expressions.Expressions;
import org.knime.core.expressions.ReturnResult;
import org.knime.core.expressions.ValueType;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * Computes object states with ordered KNIME Expression Language rules, appends a state-aware activity column, and can
 * optionally insert explicit object state transition events in a combined OCEL event-object table.
 */
@SuppressWarnings("restriction") // org.knime.core.expressions is currently marked as friend API by KNIME
public final class StateAwareOCELNodeModel extends NodeModel {

    private static final NodeLogger LOGGER = NodeLogger.getLogger(StateAwareOCELNodeModel.class);

    private final StateAwareOCELNodeSettings m_settings = new StateAwareOCELNodeSettings();

    public StateAwareOCELNodeModel() {
        super(1, 1);
    }

    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs) throws InvalidSettingsException {
        if (inSpecs[0] == null) {
            return new DataTableSpec[]{null};
        }
        m_settings.validateAgainstSpec(inSpecs[0]);
        compileRuleAsts(m_settings, inSpecs[0]);
        return new DataTableSpec[]{createOutputSpec(inSpecs[0], m_settings)};
    }

    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData, final ExecutionContext exec)
        throws Exception {
        final BufferedDataTable input = inData[0];
        final DataTableSpec inputSpec = input.getDataTableSpec();
        m_settings.validateAgainstSpec(inputSpec);

        LOGGER.info("Start: enrich OCEL table with object states");
        final var sourceRows = materializeRows(input, exec.createSubExecutionContext(0.15));
        final var compiledRules = compileRulesForExecution(m_settings, inputSpec, sourceRows);
        final DataTableSpec outputSpec = createOutputSpec(inputSpec, m_settings);

        final int eventIdIdx = inputSpec.findColumnIndex(m_settings.m_eventIdColumn);
        final int objectIdIdx = inputSpec.findColumnIndex(m_settings.m_objectIdColumn);
        final int timestampIdx = inputSpec.findColumnIndex(m_settings.m_timestampColumn);
        final int activityIdx = inputSpec.findColumnIndex(m_settings.m_activityColumn);

        final List<EnrichedRow> enrichedRows = evaluateStates(sourceRows, compiledRules, m_settings, inputSpec,
            eventIdIdx, objectIdIdx, timestampIdx, activityIdx, exec.createSubExecutionContext(0.55));

        final List<OutputRecord> outputRows = new ArrayList<>(enrichedRows.size());
        for (final var row : enrichedRows) {
            outputRows.add(createOriginalOutputRecord(row, inputSpec, timestampIdx, eventIdIdx, objectIdIdx,
                activityIdx, m_settings));
        }

        if (m_settings.m_addTransitionEvents) {
            outputRows.addAll(createTransitionRecords(enrichedRows, inputSpec, timestampIdx, eventIdIdx, objectIdIdx,
                activityIdx, m_settings));
            outputRows.sort(StateAwareOCELNodeModel::compareOutputRecords);
        }

        final BufferedDataContainer container = exec.createDataContainer(outputSpec, false);
        final ExecutionMonitor writeExec = exec.createSubExecutionContext(0.30);
        long rowCounter = 0;
        for (final var outputRecord : outputRows) {
            container.addRowToTable(new DefaultRow(outputRecord.rowKey(), outputRecord.cells()));
            rowCounter++;
            if (rowCounter % 1000 == 0) {
                writeExec.setProgress(rowCounter / (double)Math.max(1, outputRows.size()),
                    "Writing enriched OCEL row " + rowCounter + " of " + outputRows.size());
                writeExec.checkCanceled();
            }
        }
        container.close();
        LOGGER.info("End: enrich OCEL table with object states");
        return new BufferedDataTable[]{container.getTable()};
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_settings.saveSettingsTo(settings);
    }

    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_settings.loadSettingsFrom(settings);
    }

    @Override
    protected void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        final var tmp = new StateAwareOCELNodeSettings();
        tmp.loadSettingsFrom(settings);
        tmp.validateBasic();
    }

    @Override
    protected void loadInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // No internals are persisted.
    }

    @Override
    protected void saveInternals(final File nodeInternDir, final ExecutionMonitor exec)
        throws IOException, CanceledExecutionException {
        // No internals are persisted.
    }

    @Override
    protected void reset() {
        // Nothing to reset.
    }

    static DataTableSpec createOutputSpec(final DataTableSpec inputSpec, final StateAwareOCELNodeSettings settings) {
        final List<DataColumnSpec> columns = new ArrayList<>();
        for (int i = 0; i < inputSpec.getNumColumns(); i++) {
            columns.add(inputSpec.getColumnSpec(i));
        }
        columns.add(new DataColumnSpecCreator(settings.m_stateColumn, StringCell.TYPE).createSpec());
        columns.add(new DataColumnSpecCreator(settings.m_stateAwareActivityColumn, StringCell.TYPE).createSpec());
        columns.add(new DataColumnSpecCreator(StateAwareOCELNodeSettings.COL_IS_STATE_CHANGE_EVENT,
            BooleanCell.TYPE).createSpec());
        columns.add(new DataColumnSpecCreator(StateAwareOCELNodeSettings.COL_TRANSITION_FROM,
            StringCell.TYPE).createSpec());
        columns.add(new DataColumnSpecCreator(StateAwareOCELNodeSettings.COL_TRANSITION_TO, StringCell.TYPE)
            .createSpec());
        columns.add(new DataColumnSpecCreator(StateAwareOCELNodeSettings.COL_TRANSITION_SOURCE_EVENT,
            StringCell.TYPE).createSpec());
        columns.add(new DataColumnSpecCreator(StateAwareOCELNodeSettings.COL_TRANSITION_AFFECTED_OBJECTS,
            LongCell.TYPE).createSpec());
        return new DataTableSpec("State-Aware OCEL Table", columns.toArray(DataColumnSpec[]::new));
    }

    private static List<SourceRow> materializeRows(final BufferedDataTable input, final ExecutionMonitor exec)
        throws CanceledExecutionException {
        final List<SourceRow> rows = new ArrayList<>((int)Math.min(Integer.MAX_VALUE, input.size()));
        long index = 0;
        for (final DataRow row : input) {
            final DataCell[] cells = new DataCell[row.getNumCells()];
            for (int i = 0; i < row.getNumCells(); i++) {
                cells[i] = row.getCell(i);
            }
            rows.add(new SourceRow(row.getKey(), cells));
            index++;
            if (index % 1000 == 0) {
                exec.setProgress(index / (double)Math.max(1, input.size()),
                    "Reading input row " + index + " of " + input.size());
                exec.checkCanceled();
            }
        }
        return rows;
    }

    private static List<EnrichedRow> evaluateStates(final List<SourceRow> sourceRows,
        final List<CompiledRule> compiledRules, final StateAwareOCELNodeSettings settings, final DataTableSpec inputSpec,
        final int eventIdIdx, final int objectIdIdx, final int timestampIdx, final int activityIdx,
        final ExecutionMonitor exec) throws ExpressionEvaluationException, CanceledExecutionException {

        final RuntimeRowContext runtimeContext = compiledRules.get(0).runtimeContext();
        final WarningCollector warnings = new WarningCollector();
        final EvaluationContext evaluationContext = EvaluationContext.of(ZonedDateTime.now(), warnings::addWarning);
        final List<EnrichedRow> rows = new ArrayList<>(sourceRows.size());

        for (int rowIndex = 0; rowIndex < sourceRows.size(); rowIndex++) {
            runtimeContext.setRowIndex(rowIndex);
            String state = settings.m_defaultState;
            for (final var rule : compiledRules) {
                final BooleanComputer computer = rule.computer();
                final boolean missing = computer.isMissing(evaluationContext);
                if (!missing && computer.compute(evaluationContext)) {
                    state = rule.stateName();
                    break;
                }
            }

            final var source = sourceRows.get(rowIndex);
            final String activity = cellToString(source.cells()[activityIdx]);
            final String stateAwareActivity = activity == null ? null : activity + " (" + state + ")";
            rows.add(new EnrichedRow(source, rowIndex, state, stateAwareActivity,
                cellToString(source.cells()[eventIdIdx]), cellToString(source.cells()[objectIdIdx]),
                timestampSortKey(source.cells()[timestampIdx]), cellToString(source.cells()[activityIdx])));

            if (rowIndex % 1000 == 0) {
                exec.setProgress(rowIndex / (double)Math.max(1, sourceRows.size()),
                    "Evaluating state rules for row " + rowIndex + " of " + sourceRows.size());
                exec.checkCanceled();
            }
        }
        warnings.reportToLogger();
        return rows;
    }

    private static List<OutputRecord> createTransitionRecords(final List<EnrichedRow> enrichedRows,
        final DataTableSpec inputSpec, final int timestampIdx, final int eventIdIdx, final int objectIdIdx,
        final int activityIdx, final StateAwareOCELNodeSettings settings) {

        final List<EnrichedRow> sorted = new ArrayList<>(enrichedRows);
        sorted.sort(Comparator.comparing(EnrichedRow::objectId, StateAwareOCELNodeModel::compareNullableString)
            .thenComparing(EnrichedRow::timestampKey, StateAwareOCELNodeModel::compareNullableString)
            .thenComparing(EnrichedRow::eventId, StateAwareOCELNodeModel::compareNullableString)
            .thenComparingInt(EnrichedRow::sourceIndex));

        final List<TransitionCandidate> candidates = new ArrayList<>();
        final Map<String, EnrichedRow> previousByObject = new HashMap<>();
        for (final var current : sorted) {
            if (current.objectId() == null) {
                continue;
            }
            final var previous = previousByObject.put(current.objectId(), current);
            if (previous == null) {
                continue;
            }
            if (!previous.state().equals(current.state())) {
                final String label = settings.m_transitionPrefix + previous.state() + " to " + current.state();
                final DataCell shiftedTimestamp = shiftedTimestampCell(current.source().cells()[timestampIdx],
                    settings.m_transitionEpsilonSeconds);
                final String timestampKey = timestampSortKey(shiftedTimestamp);
                candidates.add(new TransitionCandidate(current, previous.state(), current.state(), label,
                    shiftedTimestamp, timestampKey));
            }
        }

        final Map<String, Long> groupCounts = new HashMap<>();
        final Map<String, String> groupIds = new HashMap<>();
        if (settings.m_coalesceTransitionEvents) {
            int groupIndex = 1;
            for (final var candidate : candidates) {
                final String groupKey = candidate.timestampKey() + "|" + candidate.label();
                groupCounts.merge(groupKey, 1L, Long::sum);
                if (!groupIds.containsKey(groupKey)) {
                    groupIds.put(groupKey, settings.m_transitionEventIdPrefix + "_COAL_" + groupIndex++);
                }
            }
        }

        final List<OutputRecord> records = new ArrayList<>(candidates.size());
        int transitionIndex = 1;
        for (final var candidate : candidates) {
            final String groupKey = candidate.timestampKey() + "|" + candidate.label();
            final String eventId;
            final long affectedObjects;
            if (settings.m_coalesceTransitionEvents) {
                eventId = groupIds.get(groupKey);
                affectedObjects = groupCounts.get(groupKey);
            } else {
                eventId = settings.m_transitionEventIdPrefix + "_" + transitionIndex + "_"
                    + safeIdentifier(candidate.current().objectId());
                affectedObjects = 1L;
            }

            records.add(createTransitionOutputRecord(candidate, eventId, affectedObjects, inputSpec, timestampIdx,
                eventIdIdx, objectIdIdx, activityIdx, settings, transitionIndex));
            transitionIndex++;
        }
        return records;
    }

    private static OutputRecord createOriginalOutputRecord(final EnrichedRow row, final DataTableSpec inputSpec,
        final int timestampIdx, final int eventIdIdx, final int objectIdIdx, final int activityIdx,
        final StateAwareOCELNodeSettings settings) {
        final DataCell[] output = baseOutputCells(row.source().cells(), row.state(), row.stateAwareActivity(), false,
            null, null, null, null);
        return new OutputRecord(row.source().key(), output, row.timestampKey(), row.eventId(), row.objectId(),
            row.sourceIndex(), false);
    }

    private static OutputRecord createTransitionOutputRecord(final TransitionCandidate candidate, final String eventId,
        final long affectedObjects, final DataTableSpec inputSpec, final int timestampIdx, final int eventIdIdx,
        final int objectIdIdx, final int activityIdx, final StateAwareOCELNodeSettings settings,
        final int transitionIndex) {
        final var source = candidate.current().source();
        final DataCell[] output = baseOutputCells(source.cells(), candidate.toState(), candidate.label(), true,
            candidate.fromState(), candidate.toState(), candidate.current().eventId(), affectedObjects);
        output[eventIdIdx] = new StringCell(eventId);
        output[activityIdx] = new StringCell(candidate.label());
        output[timestampIdx] = candidate.shiftedTimestampCell();
        final RowKey rowKey = new RowKey("StateChange_" + transitionIndex + "_" + safeIdentifier(candidate.current()
            .objectId()) + "_" + safeIdentifier(candidate.current().eventId()));
        return new OutputRecord(rowKey, output, candidate.timestampKey(), eventId, candidate.current().objectId(),
            candidate.current().sourceIndex(), true);
    }

    private static DataCell[] baseOutputCells(final DataCell[] sourceCells, final String state,
        final String stateAwareActivity, final boolean isTransition, final String fromState, final String toState,
        final String sourceEventId, final Long affectedObjects) {
        final DataCell[] output = new DataCell[sourceCells.length + 7];
        System.arraycopy(sourceCells, 0, output, 0, sourceCells.length);
        int idx = sourceCells.length;
        output[idx++] = stringOrMissing(state);
        output[idx++] = stringOrMissing(stateAwareActivity);
        output[idx++] = BooleanCell.get(isTransition);
        output[idx++] = stringOrMissing(fromState);
        output[idx++] = stringOrMissing(toState);
        output[idx++] = stringOrMissing(sourceEventId);
        output[idx] = affectedObjects == null ? DataType.getMissingCell() : new LongCell(affectedObjects.longValue());
        return output;
    }

    private static List<Ast> compileRuleAsts(final StateAwareOCELNodeSettings settings, final DataTableSpec inputSpec)
        throws InvalidSettingsException {
        final List<Ast> asts = new ArrayList<>();
        for (int i = 0; i < settings.m_rules.size(); i++) {
            final var rule = settings.m_rules.get(i);
            try {
                final Ast ast = Expressions.parse(rule.expression());
                if (containsAggregation(ast)) {
                    throw new InvalidSettingsException("State rule '" + rule.stateName() + "' uses a COLUMN_* "
                        + "aggregation. Aggregations are not supported directly in this node. Precompute the aggregate "
                        + "upstream, append it as a column, and refer to that column in the state expression.");
                }
                final ValueType type = Expressions.inferTypes(ast, columnToTypeForTypeInference(inputSpec),
                    flowVariableToTypeForTypeInference());
                if (!ValueType.BOOLEAN.equals(type.baseType())) {
                    throw new InvalidSettingsException("State rule '" + rule.stateName() + "' must return BOOLEAN, "
                        + "but it returns " + type.name() + ".");
                }
                asts.add(ast);
            } catch (ExpressionCompileException ex) {
                throw new InvalidSettingsException("State rule '" + rule.stateName() + "' contains an invalid "
                    + "expression: " + ex.getMessage(), ex);
            }
        }
        return asts;
    }

    private static List<CompiledRule> compileRulesForExecution(final StateAwareOCELNodeSettings settings,
        final DataTableSpec inputSpec, final List<SourceRow> sourceRows) throws InvalidSettingsException {
        final var asts = compileRuleAsts(settings, inputSpec);
        final RuntimeRowContext runtimeContext = new RuntimeRowContext(inputSpec, sourceRows);
        final List<CompiledRule> compiled = new ArrayList<>();
        for (int i = 0; i < asts.size(); i++) {
            final var rule = settings.m_rules.get(i);
            try {
                Expressions.resolveColumnIndices(asts.get(i), columnAccess -> resolveColumnIndex(inputSpec,
                    columnAccess));
                final Computer computer = Expressions.evaluate(asts.get(i),
                    columnAccess -> Optional.of(createComputer(runtimeContext, columnAccess)),
                    flowVarAccess -> Optional.empty(), aggregationCall -> Optional.empty());
                if (!(computer instanceof BooleanComputer booleanComputer)) {
                    throw new InvalidSettingsException("State rule '" + rule.stateName()
                        + "' did not compile to a boolean expression.");
                }
                compiled.add(new CompiledRule(rule.stateName(), booleanComputer, runtimeContext));
            } catch (ExpressionCompileException ex) {
                throw new InvalidSettingsException("Could not prepare state rule '" + rule.stateName()
                    + "' for execution: " + ex.getMessage(), ex);
            }
        }
        return compiled;
    }

    private static Function<String, ReturnResult<ValueType>> columnToTypeForTypeInference(final DataTableSpec spec) {
        return name -> ReturnResult.fromNullable(spec, "No input data is available.")
            .flatMap(s -> ReturnResult.fromNullable(s.getColumnSpec(name),
                "No column with the name '" + name + "' is available."))
            .map(DataColumnSpec::getType)
            .flatMap(type -> ReturnResult.fromNullable(mapDataTypeToValueType(type),
                "Columns of the type '" + type + "' are not supported in state expressions."));
    }

    private static Function<String, ReturnResult<ValueType>> flowVariableToTypeForTypeInference() {
        return name -> ReturnResult.failure("Flow variables are not supported by the State-Aware OCEL Enricher. "
            + "Append the flow variable value as a table column before this node if it should be used in a state "
            + "expression.");
    }

    private static ValueType mapDataTypeToValueType(final DataType type) {
        if (type.isCompatible(BooleanValue.class)) {
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

    private static OptionalInt resolveColumnIndex(final DataTableSpec spec, final Ast.ColumnAccess columnAccess) {
        if (columnAccess.columnId().type() != Ast.ColumnId.ColumnIdType.NAMED) {
            // ROW_ID and ROW_INDEX do not correspond to a physical input column. The value is ignored later because
            // createComputer handles these special column IDs before asking for a resolved physical column index.
            return OptionalInt.of(0);
        }
        final int idx = spec.findColumnIndex(columnAccess.columnId().name());
        return idx < 0 ? OptionalInt.empty() : OptionalInt.of(idx);
    }

    private static Computer createComputer(final RuntimeRowContext context, final Ast.ColumnAccess columnAccess) {
        return switch (columnAccess.columnId().type()) {
            case ROW_INDEX -> IntegerComputer.of(ctx -> context.offsetRowIndex(columnAccess.offset()),
                ctx -> !context.hasOffsetRow(columnAccess.offset()));
            case ROW_ID -> StringComputer.of(ctx -> context.offsetRowKey(columnAccess.offset()),
                ctx -> !context.hasOffsetRow(columnAccess.offset()));
            case NAMED -> cellComputer(context, Expressions.getResolvedColumnIdx(columnAccess), columnAccess.offset());
        };
    }

    private static Computer cellComputer(final RuntimeRowContext context, final int columnIndex, final long offset) {
        final DataType type = context.spec().getColumnSpec(columnIndex).getType();
        final BooleanComputerResultSupplier isMissing = ctx -> context.isMissing(columnIndex, offset);
        if (type.isCompatible(BooleanValue.class)) {
            return BooleanComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return !cell.isMissing() && ((BooleanValue)cell).getBooleanValue();
            }, isMissing);
        } else if (type.isCompatible(LongValue.class)) {
            return IntegerComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? 0L : ((LongValue)cell).getLongValue();
            }, isMissing);
        } else if (type.isCompatible(DoubleValue.class)) {
            return FloatComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? 0.0d : ((DoubleValue)cell).getDoubleValue();
            }, isMissing);
        } else if (type.isCompatible(LocalDateValue.class)) {
            return LocalDateComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? LocalDate.MIN : ((LocalDateValue)cell).getLocalDate();
            }, isMissing);
        } else if (type.isCompatible(LocalTimeValue.class)) {
            return LocalTimeComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? LocalTime.MIN : ((LocalTimeValue)cell).getLocalTime();
            }, isMissing);
        } else if (type.isCompatible(LocalDateTimeValue.class)) {
            return LocalDateTimeComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? LocalDateTime.MIN : ((LocalDateTimeValue)cell).getLocalDateTime();
            }, isMissing);
        } else if (type.isCompatible(ZonedDateTimeValue.class)) {
            return ZonedDateTimeComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault())
                    : ((ZonedDateTimeValue)cell).getZonedDateTime();
            }, isMissing);
        } else if (type.isCompatible(DurationValue.class)) {
            return TimeDurationComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? Duration.ZERO : ((DurationValue)cell).getDuration();
            }, isMissing);
        } else if (type.isCompatible(PeriodValue.class)) {
            return DateDurationComputer.of(ctx -> {
                final var cell = context.cell(columnIndex, offset);
                return cell.isMissing() ? Period.ZERO : ((PeriodValue)cell).getPeriod();
            }, isMissing);
        } else if (type.isCompatible(StringValue.class)) {
            return StringComputer.of(ctx -> cellToString(context.cell(columnIndex, offset)), isMissing);
        }
        throw new IllegalArgumentException("Unsupported column type: " + type);
    }

    private static boolean containsAggregation(final Ast ast) {
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

    private static DataCell shiftedTimestampCell(final DataCell timestampCell, final double epsilonSeconds) {
        if (timestampCell == null || timestampCell.isMissing() || epsilonSeconds <= 0.0d) {
            return timestampCell == null ? DataType.getMissingCell() : timestampCell;
        }
        final long nanos = Math.round(epsilonSeconds * 1_000_000_000.0d);
        if (timestampCell instanceof LocalDateTimeValue value) {
            return LocalDateTimeCellFactory.create(value.getLocalDateTime().minusNanos(nanos));
        }
        if (timestampCell instanceof ZonedDateTimeValue value) {
            return ZonedDateTimeCellFactory.create(value.getZonedDateTime().minusNanos(nanos));
        }
        if (timestampCell instanceof StringValue value) {
            final String text = value.getStringValue();
            try {
                return new StringCell(ZonedDateTime.parse(text).minusNanos(nanos).toString());
            } catch (RuntimeException ex) { // NOSONAR: try local date time next
                try {
                    return new StringCell(LocalDateTime.parse(text).minusNanos(nanos).toString());
                } catch (RuntimeException ignored) { // NOSONAR: keep the original timestamp if parsing is not possible
                    return timestampCell;
                }
            }
        }
        return timestampCell;
    }

    private static DataCell stringOrMissing(final String value) {
        return value == null ? DataType.getMissingCell() : new StringCell(value);
    }

    private static String cellToString(final DataCell cell) {
        if (cell == null || cell.isMissing()) {
            return null;
        }
        if (cell instanceof StringValue stringValue) {
            return stringValue.getStringValue();
        }
        return cell.toString();
    }

    private static String timestampSortKey(final DataCell cell) {
        if (cell == null || cell.isMissing()) {
            return null;
        }
        if (cell instanceof ZonedDateTimeValue zonedDateTimeValue) {
            return zonedDateTimeValue.getZonedDateTime().toInstant().toString();
        }
        if (cell instanceof LocalDateTimeValue localDateTimeValue) {
            return localDateTimeValue.getLocalDateTime().toString();
        }
        if (cell instanceof LocalDateValue localDateValue) {
            return localDateValue.getLocalDate().toString();
        }
        return cellToString(cell);
    }

    private static int compareOutputRecords(final OutputRecord left, final OutputRecord right) {
        int result = compareNullableString(left.timestampKey(), right.timestampKey());
        if (result != 0) {
            return result;
        }
        if (left.sourceIndex() == right.sourceIndex() && left.isTransition() != right.isTransition()) {
            return left.isTransition() ? -1 : 1;
        }
        result = compareNullableString(left.eventId(), right.eventId());
        if (result != 0) {
            return result;
        }
        result = compareNullableString(left.objectId(), right.objectId());
        if (result != 0) {
            return result;
        }
        return Integer.compare(left.sourceIndex(), right.sourceIndex());
    }

    private static int compareNullableString(final String left, final String right) {
        if (left == null && right == null) {
            return 0;
        } else if (left == null) {
            return 1;
        } else if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static String safeIdentifier(final String value) {
        if (value == null || value.isBlank()) {
            return "missing";
        }
        final String cleaned = value.replaceAll("[^A-Za-z0-9_\\-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private record SourceRow(RowKey key, DataCell[] cells) {
    }

    private record EnrichedRow(SourceRow source, int sourceIndex, String state, String stateAwareActivity,
        String eventId, String objectId, String timestampKey, String activity) {
    }

    private record TransitionCandidate(EnrichedRow current, String fromState, String toState, String label,
        DataCell shiftedTimestampCell, String timestampKey) {
    }

    private record OutputRecord(RowKey rowKey, DataCell[] cells, String timestampKey, String eventId, String objectId,
        int sourceIndex, boolean isTransition) {
    }

    private record CompiledRule(String stateName, BooleanComputer computer, RuntimeRowContext runtimeContext) {
    }

    private static final class RuntimeRowContext {
        private final DataTableSpec m_spec;

        private final List<SourceRow> m_rows;

        private int m_rowIndex;

        RuntimeRowContext(final DataTableSpec spec, final List<SourceRow> rows) {
            m_spec = spec;
            m_rows = rows;
        }

        DataTableSpec spec() {
            return m_spec;
        }

        void setRowIndex(final int rowIndex) {
            m_rowIndex = rowIndex;
        }

        boolean hasOffsetRow(final long offset) {
            final long target = (long)m_rowIndex + offset;
            return target >= 0L && target < m_rows.size();
        }

        long offsetRowIndex(final long offset) throws ExpressionEvaluationException {
            final long target = (long)m_rowIndex + offset;
            if (target < 0L || target >= m_rows.size()) {
                throw new ExpressionEvaluationException("Row offset " + offset + " points outside the input table.");
            }
            return target;
        }

        String offsetRowKey(final long offset) throws ExpressionEvaluationException {
            final long target = (long)m_rowIndex + offset;
            if (target < 0L || target >= m_rows.size()) {
                throw new ExpressionEvaluationException("Row offset " + offset + " points outside the input table.");
            }
            return m_rows.get((int)target).key().getString();
        }

        boolean isMissing(final int columnIndex, final long offset) {
            if (!hasOffsetRow(offset)) {
                return true;
            }
            return cell(columnIndex, offset).isMissing();
        }

        DataCell cell(final int columnIndex, final long offset) {
            if (!hasOffsetRow(offset)) {
                return DataType.getMissingCell();
            }
            return m_rows.get((int)((long)m_rowIndex + offset)).cells()[columnIndex];
        }
    }

    private static final class WarningCollector {
        private final AtomicInteger m_warningCount = new AtomicInteger();

        private final List<String> m_firstWarnings = new ArrayList<>();

        void addWarning(final String warning) {
            final int count = m_warningCount.incrementAndGet();
            if (count <= 10) {
                m_firstWarnings.add(warning);
            }
        }

        void reportToLogger() {
            if (m_warningCount.get() == 0) {
                return;
            }
            LOGGER.warn("State expression evaluation produced " + m_warningCount.get()
                + " warning(s). First warnings: " + String.join(" | ", m_firstWarnings));
        }
    }
}
