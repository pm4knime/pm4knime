package org.pm4knime.node.logmanipulation.filter.knimetable;

import org.knime.node.parameters.Widget;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation
    .MinValidation.IsNonNegativeValidation;
import org.pm4knime.util.defaultnode.DefaultTableNodeSettings;

public final class FilterByFrequencyTableNodeSettings
        extends DefaultTableNodeSettings {

    public interface ExtendedDialogLayout extends DialogLayoutWithTime {

        @Section(title = "Frequency Filtering")
        @After(EventLogClassifiers.class)
        interface Settings {
        }
    }

    enum FilteringMode {

        @Label("Filter variants by minimum frequency")
        MINIMUM_VARIANT_FREQUENCY,

        @Label("Select the most frequent traces")
        MOST_FREQUENT_TRACES
    }

    enum FilteringAction {

        @Label("Keep selected traces")
        KEEP_SELECTED,

        @Label("Remove selected traces")
        REMOVE_SELECTED
    }

    enum ThresholdUnit {

        @Label("Number of traces")
        TRACE_COUNT,

        @Label("Percentage of traces")
        PERCENTAGE
    }

    @Widget(
        title = "Filtering mode",
        description =
            "Choose how traces are selected. "
                + "\"Filter variants by minimum frequency\" selects each variant "
                + "that occurs often enough. "
                + "\"Select the most frequent traces\" starts with the most frequent "
                + "variants and continues until the target is reached."
    )
    @Layout(ExtendedDialogLayout.Settings.class)
    FilteringMode m_filteringMode =
        FilteringMode.MINIMUM_VARIANT_FREQUENCY;

    @Widget(
        title = "Action",
        description =
            "Keep selected traces keeps only the traces selected by the filtering mode. "
                + "Remove selected traces removes them and keeps all other traces."
    )
    @Layout(ExtendedDialogLayout.Settings.class)
    FilteringAction m_action =
        FilteringAction.KEEP_SELECTED;

    @Widget(
        title = "Threshold unit",
        description =
            "Choose whether the threshold is entered as a trace count or a percentage."
    )
    @Layout(ExtendedDialogLayout.Settings.class)
    ThresholdUnit m_thresholdUnit =
        ThresholdUnit.PERCENTAGE;

    @Widget(
        title = "Threshold",
        description =
            "For minimum-frequency filtering, this is the minimum frequency required "
                + "for a trace variant. For most-frequent filtering, this is the target "
                + "number or percentage of traces to select. "
                + "Enter a percentage between 0 and 100, or a whole number of traces, "
                + "depending on the selected threshold unit.\""
    )
    @Layout(ExtendedDialogLayout.Settings.class)
    @NumberInputWidget(minValidation = IsNonNegativeValidation.class)
    double m_threshold = 20.0;
}