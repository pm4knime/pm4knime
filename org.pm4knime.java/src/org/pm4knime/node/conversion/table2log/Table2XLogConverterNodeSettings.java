package org.pm4knime.node.conversion.table2log;

import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.StringValue;
import org.knime.core.data.time.localdatetime.LocalDateTimeCellFactory;
import org.knime.core.data.time.zoneddatetime.ZonedDateTimeCellFactory;
import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.NodeParametersInput;
import org.knime.node.parameters.layout.Layout;
import org.knime.node.parameters.layout.After;
import org.knime.node.parameters.layout.Section;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.updates.Effect;
import org.knime.node.parameters.updates.Effect.EffectType;
import org.knime.node.parameters.updates.EffectPredicate;
import org.knime.node.parameters.updates.EffectPredicateProvider;
import org.knime.node.parameters.updates.ParameterReference;
import org.knime.node.parameters.updates.ValueReference;
import org.knime.node.parameters.widget.choices.ChoicesProvider;
import org.knime.node.parameters.widget.choices.StringChoicesProvider;
import org.knime.node.parameters.widget.choices.filter.ColumnFilter;
import org.knime.node.parameters.widget.choices.filter.TwinlistWidget;
import org.knime.node.parameters.widget.choices.util.AllColumnsProvider;
import org.processmining.log.csvimport.config.CSVConversionConfig.CSVEmptyCellHandlingMode;
import org.processmining.log.csvimport.config.CSVConversionConfig.CSVErrorHandlingMode;
import org.pm4knime.node.discovery.defaultminer.DefaultTableMinerSettings.StringCellColumnsProvider;
import org.pm4knime.settingsmodel.SMTable2XLogConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public final class Table2XLogConverterNodeSettings implements NodeParameters {

	static final String NO_COLUMN_SELECTION = "";

	static boolean isNoColumnSelected(final String value) {
		return value == null
				|| value.isBlank()
				|| SMTable2XLogConfig.CFG_NO_OPTION.equals(value);
	}

	static final class UseLifeCycleRef implements ParameterReference<Boolean> {
	}

	static final class IsLifeCycleEnabled implements EffectPredicateProvider {
		@Override
		public EffectPredicate init(final PredicateInitializer i) {
			return i.getBoolean(UseLifeCycleRef.class).isTrue();
		}
	}

	public static interface Table2XLogDialogLayout {

		@Section(title = "Event Log Identifiers")
		interface StandardOptions {

		}

		@Section(title = "Column Mapping")
		@After(StandardOptions.class)
		interface ChooseAttributesSet {

		}

		@Section(title = "Advanced Options")
		@After(ChooseAttributesSet.class)
		interface ExpertChoice {

		}
	}

	

	public static final class StringColumnChoices implements StringChoicesProvider {

		@Override
		public List<String> choices(final NodeParametersInput context) {

			final DataTableSpec specs = context.getInTableSpecs()[0];

			if (specs == null) {
				return Collections.emptyList();
			} else {
				return specs.stream().filter(s -> s.getType().isCompatible(StringValue.class))
						.map(DataColumnSpec::getName).collect(Collectors.toList());
			}
		}
	}
	
	
	public static final class TimeColumnChoices implements StringChoicesProvider {
		
		
		
		@Override
		public List<String> choices(final NodeParametersInput context) {

			Object specObj = context.getInPortSpecs()[0];
			
			if (specObj == null) {
	            return Collections.emptyList(); 
	        }

			if (specObj instanceof DataTableSpec) {
				DataTableSpec specs = (DataTableSpec) specObj;

				return specs.stream()
						.filter(s -> s.getType().equals(ZonedDateTimeCellFactory.TYPE)
								|| s.getType().equals(LocalDateTimeCellFactory.TYPE))
						.map(DataColumnSpec::getName).collect(Collectors.toList());
			} else {
				System.err.println("Expected a DataTableSpec but received a different type: "
						+ specObj.getClass().getSimpleName());
				return Collections.emptyList();
			}
		}
	}
	

	static ExpertConfigPanel ecPanel = new ExpertConfigPanel();
	
	public static final List<String> xFactoryVariantList = ExpertConfigPanel.getAvailableXFactories()
		    .stream()
		    .map(factory -> factory.toString().contains("org.deckfour.xes.factory.XFactoryNaiveImpl") 
		        ? "Standard / naïve" 
		        : factory.toString())
		    .collect(Collectors.toList());
					

	public static class XFactoryChoicesProvider implements StringChoicesProvider {
		@Override
		public List<String> choices(final NodeParametersInput context) {
			return xFactoryVariantList;
		}
	}

	public static final List<String> errorHandlingVariantList = Arrays.stream(CSVErrorHandlingMode.values())
			.map(mode -> (mode.toString()))
			.collect(Collectors.toList());

	public static class ErrorHandlingChoicesProvider implements StringChoicesProvider {
		@Override
		public List<String> choices(final NodeParametersInput context) {
			return errorHandlingVariantList;
		}
	}

	public static final List<String> sparseLogVariantList = Arrays.stream(CSVEmptyCellHandlingMode.values())
			.map(mode -> (mode.toString()))
			.collect(Collectors.toList());

	public static class SparseLogChoicesProvider implements StringChoicesProvider {
		@Override
		public List<String> choices(final NodeParametersInput context) {
			return sparseLogVariantList;
		}
	}

	@Widget(title = "Case", description = "Column to be used as a caseID in the event log")
	@Layout(Table2XLogDialogLayout.StandardOptions.class)
	@ChoicesProvider(value = StringCellColumnsProvider.class)
	String case_id;

	@Widget(title = "Event", description = "Column to be used as an eventID in the event log")
	@Layout(Table2XLogDialogLayout.StandardOptions.class)
	@ChoicesProvider(value = StringCellColumnsProvider.class)
	String event_class;

	@Widget(title = "Use Life Cycle", description = "Enable this option to map a column to the life cycle attribute in the event log.")
	@Layout(Table2XLogDialogLayout.StandardOptions.class)
	@ValueReference(UseLifeCycleRef.class)
	boolean use_life_cycle = false;

	@Widget(title = "Life Cycle", description = "Optional column to be used for the life cycle attribute in the event log. Leave empty if no life cycle column should be used.")
	@Layout(Table2XLogDialogLayout.StandardOptions.class)
	@Effect(predicate = IsLifeCycleEnabled.class, type = EffectType.SHOW)
	@ChoicesProvider(value = StringColumnChoices.class)
	String life_cycle = NO_COLUMN_SELECTION;

	@Widget(title = "Time Stamp", description = "Column to be used for the time stamp attribute in the event log. It must be a ZonedDateTime or DateTime column.")
	@Layout(Table2XLogDialogLayout.StandardOptions.class)
	@ChoicesProvider(value = TimeColumnChoices.class)
	String time_stamp = NO_COLUMN_SELECTION;

	@Widget(title = "From Table Columns to Event Log Attributes", description = "Select the columns to be used as trace attributes. The remaining columns will be used as event attributes.")
	@ChoicesProvider(value = AllColumnsProvider.class)
	@Layout(Table2XLogDialogLayout.ChooseAttributesSet.class)
	@TwinlistWidget(excludedLabel = "Event Attributes", includedLabel = "Trace Attributes")
	ColumnFilter m_columnFilterTrace = new ColumnFilter();


	@Widget(title = "XFactory", description = "XFactory implementation that is used to create the log.")
	@Layout(Table2XLogDialogLayout.ExpertChoice.class)
	@ChoicesProvider(value = XFactoryChoicesProvider.class)
	String xfactory = xFactoryVariantList.get(0);

	@Widget(title = "Error Handling", description = "The strategy for handling errors.")
	@Layout(Table2XLogDialogLayout.ExpertChoice.class)
	@ChoicesProvider(value = ErrorHandlingChoicesProvider.class)
	String error_handling = errorHandlingVariantList.get(0);

	@Widget(title = "Sparse / Dense Log", description = "This affects how empty cells in the table are handled. \r\n"
			+ "          Some plug-ins require the log to be dense, i.e., all attributes are defined for each event. \r\n"
			+ "          In other cases, it might be more efficient or even required to only add attributes to events if the attributes actually contain data.")
	@Layout(Table2XLogDialogLayout.ExpertChoice.class)
	@ChoicesProvider(value = SparseLogChoicesProvider.class)
	String sparse_log = sparseLogVariantList.get(0);
}
