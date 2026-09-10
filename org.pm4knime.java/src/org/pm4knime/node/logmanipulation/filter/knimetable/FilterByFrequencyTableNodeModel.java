package org.pm4knime.node.logmanipulation.filter.knimetable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.sort.BufferedDataTableSorter;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.pm4knime.util.defaultnode.DefaultTableNodeModel;

public class FilterByFrequencyTableNodeModel
        extends DefaultTableNodeModel<FilterByFrequencyTableNodeSettings> {

    private static final NodeLogger LOGGER =
        NodeLogger.getLogger(FilterByFrequencyTableNodeFactory.class);

    protected FilterByFrequencyTableNodeModel(
            final Class<FilterByFrequencyTableNodeSettings> settingsClass) {

        super(
            new PortType[] {BufferedDataTable.TYPE},
            new PortType[] {BufferedDataTable.TYPE},
            settingsClass
        );
    }

    @Override
    protected BufferedDataTable[] execute(
            final PortObject[] inData,
            final ExecutionContext exec) throws Exception {

        LOGGER.info("Begin: filter event table by trace frequency");

        BufferedDataTable log = (BufferedDataTable)inData[0];

        if (log.size() == 0) {
            LOGGER.warn(
                "The event table is empty. Returning the original table."
            );

            return new BufferedDataTable[] {log};
        }

        final List<String> sortingColumns = Arrays.asList(
            m_settings.t_classifier,
            m_settings.time_classifier
        );

        final boolean[] ascending = {true, true};

        final BufferedDataTableSorter sorter =
            new BufferedDataTableSorter(
                log,
                sortingColumns,
                ascending
            );

        log = sorter.sort(exec);

        final int activityColumnIndex =
            log.getDataTableSpec().findColumnIndex(
                m_settings.e_classifier
            );

        final int traceIdColumnIndex =
            log.getDataTableSpec().findColumnIndex(
                m_settings.t_classifier
            );

        String currentTraceId = "";
        String currentVariant = "";

        int totalTraces = 0;

        /*
         * Maps a trace variant to all trace IDs that belong to it.
         */
        final HashMap<String, ArrayList<String>> variantToTraceIds =
            new HashMap<>();

        for (DataRow row : log) {
            final DataCell activityCell =
                row.getCell(activityColumnIndex);

            final DataCell traceIdCell =
                row.getCell(traceIdColumnIndex);

            final String traceId = traceIdCell.toString();
            final String activity = activityCell.toString();

            if (!traceId.equals(currentTraceId)) {
                if (!currentTraceId.isEmpty()) {
                    addTraceToVariant(
                        variantToTraceIds,
                        currentVariant,
                        currentTraceId
                    );

                    totalTraces++;
                }

                currentTraceId = traceId;
                currentVariant = activity;
            } else {
                currentVariant =
                    currentVariant + "," + activity;
            }
        }

        /*
         * Add the last trace because there is no following trace ID
         * that would cause it to be added inside the loop.
         */
        addTraceToVariant(
            variantToTraceIds,
            currentVariant,
            currentTraceId
        );

        totalTraces++;

        /*
         * Each list contains the IDs of all traces belonging to one
         * trace variant.
         */
        final ArrayList<ArrayList<String>> variants =
            new ArrayList<>(variantToTraceIds.values());

        final Comparator<ArrayList<String>> frequencyComparator =
            new Comparator<ArrayList<String>>() {

                @Override
                public int compare(
                        final ArrayList<String> first,
                        final ArrayList<String> second) {

                    return Integer.compare(
                        first.size(),
                        second.size()
                    );
                }
            };

        /*
         * Most frequent variants must come first.
         */
        Collections.sort(variants, frequencyComparator);
        Collections.reverse(variants);

        final HashMap<String, Boolean> retainedTraceIds =
            getContainedCases(
                variants,
                totalTraces
            );

        final BufferedDataContainer container =
            exec.createDataContainer(
                log.getDataTableSpec(),
                false
            );

        for (DataRow row : log) {
            final String traceId =
                row.getCell(traceIdColumnIndex).toString();

            if (retainedTraceIds.containsKey(traceId)) {
                container.addRowToTable(row);
            }
        }

        container.close();

        LOGGER.info("End: filter event table by trace frequency");

        return new BufferedDataTable[] {
            container.getTable()
        };
    }

    protected HashMap<String, Boolean> getContainedCases(
            final ArrayList<ArrayList<String>> variants,
            final int totalTraces) throws InvalidSettingsException {

        final int threshold =
            getAbsoluteThreshold(totalTraces);

        final HashMap<String, Boolean> selectedTraceIds =
            new HashMap<>();

        if (m_settings.m_filteringMode
                == FilterByFrequencyTableNodeSettings
                    .FilteringMode.MINIMUM_VARIANT_FREQUENCY) {

            selectVariantsByMinimumFrequency(
                variants,
                threshold,
                selectedTraceIds
            );

        } else {
            selectMostFrequentVariants(
                variants,
                threshold,
                selectedTraceIds
            );
        }

        if (m_settings.m_action
                == FilterByFrequencyTableNodeSettings
                    .FilteringAction.KEEP_SELECTED) {

            return selectedTraceIds;
        }

        return invertSelection(
            variants,
            selectedTraceIds
        );
    }

    private int getAbsoluteThreshold(final int totalTraces)
            throws InvalidSettingsException {

        if (m_settings.m_thresholdUnit
                == FilterByFrequencyTableNodeSettings.ThresholdUnit.PERCENTAGE) {

            if (m_settings.m_threshold < 0 || m_settings.m_threshold > 100) {
                throw new InvalidSettingsException(
                    "The percentage threshold must be between 0 and 100."
                );
            }

            return (int)Math.ceil(
                totalTraces * m_settings.m_threshold / 100.0
            );
        }

        if (m_settings.m_threshold < 0) {
            throw new InvalidSettingsException(
                "The trace-count threshold must not be negative."
            );
        }

        if (m_settings.m_threshold != Math.floor(m_settings.m_threshold)) {
            throw new InvalidSettingsException(
                "The trace-count threshold must be a whole number."
            );
        }

        return (int)m_settings.m_threshold;
    }

    private static void selectVariantsByMinimumFrequency(
            final ArrayList<ArrayList<String>> variants,
            final int minimumFrequency,
            final HashMap<String, Boolean> selectedTraceIds) {

        for (ArrayList<String> variant : variants) {
            if (variant.size() >= minimumFrequency) {
                addTraceIds(
                    selectedTraceIds,
                    variant
                );
            }
        }
    }

    private static void selectMostFrequentVariants(
            final ArrayList<ArrayList<String>> variants,
            final int targetTraceCount,
            final HashMap<String, Boolean> selectedTraceIds) {

        if (targetTraceCount <= 0) {
            return;
        }

        int selectedTraceCount = 0;

        for (ArrayList<String> variant : variants) {
            if (selectedTraceCount >= targetTraceCount) {
                break;
            }

            addTraceIds(
                selectedTraceIds,
                variant
            );

            selectedTraceCount += variant.size();
        }
    }

    private static HashMap<String, Boolean> invertSelection(
            final ArrayList<ArrayList<String>> variants,
            final HashMap<String, Boolean> selectedTraceIds) {

        final HashMap<String, Boolean> remainingTraceIds =
            new HashMap<>();

        for (ArrayList<String> variant : variants) {
            for (String traceId : variant) {
                if (!selectedTraceIds.containsKey(traceId)) {
                    remainingTraceIds.put(
                        traceId,
                        true
                    );
                }
            }
        }

        return remainingTraceIds;
    }

    private static void addTraceToVariant(
            final HashMap<String, ArrayList<String>> variantToTraceIds,
            final String variant,
            final String traceId) {

        variantToTraceIds
            .computeIfAbsent(
                variant,
                ignored -> new ArrayList<>()
            )
            .add(traceId);
    }

    private static void addTraceIds(
            final HashMap<String, Boolean> target,
            final ArrayList<String> traceIds) {

        for (String traceId : traceIds) {
            target.put(
                traceId,
                true
            );
        }
    }

    @Override
    protected PortObjectSpec[] configureOutSpec(
            final DataTableSpec logSpec) {

        return new PortObjectSpec[] {
            logSpec
        };
    }

    @Override
    protected void validateSpecificSettings(
            final NodeSettingsRO settings)
            throws InvalidSettingsException {

        // No additional validation.
    }
}