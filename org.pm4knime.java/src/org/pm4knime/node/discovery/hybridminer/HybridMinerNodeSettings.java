package org.pm4knime.node.discovery.hybridminer;

import org.knime.node.parameters.NodeParameters;
import org.knime.node.parameters.Widget;
import org.knime.node.parameters.widget.choices.Label;
import org.knime.node.parameters.widget.choices.ValueSwitchWidget;
import org.knime.node.parameters.widget.number.NumberInputWidget;
import org.knime.node.parameters.widget.number.NumberInputWidgetValidation.MinValidation.IsNonNegativeValidation;
import org.pm4knime.node.discovery.alpha.table.AlphaMinerTableNodeSettings.IsMaxOne;
import org.pm4knime.node.logmanipulation.filter.knimetable.FilterByLengthTableNodeSettings.IsMinOne;



public final class HybridMinerNodeSettings implements NodeParameters {

	 @Widget(title = "Threshold for Cancellation of Place Iterator", description = "Threshold for early cancellation of place iterator: after x consecutive rejected places, the place iterator is canceled.")
	 @NumberInputWidget(minValidation=IsMinOne.class)
	 int t_cancel = 1000;
	
	 
	 @Widget(title = "Fitness Threshold", description = "Fitness threshold for the place evaluation method.")
	 @NumberInputWidget(minValidation=IsNonNegativeValidation.class, maxValidation=IsMaxOne.class)
	 double t_fitness = 0.8;	 
	 
	 
	 enum FitnessType {
         @Label("local evaluation")
         LOCAL, //
         @Label("local evaluation with global fitness guarantee")
         GLOBAL;
     }
     @Widget(title = "Place evaluation method", description = "Place evaluation method.")
	 FitnessType type_fitness = FitnessType.GLOBAL;

}
