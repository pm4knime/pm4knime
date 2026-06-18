package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.knime.core.expressions.ExpressionConstants;
import org.knime.core.expressions.OperatorCategory;
import org.knime.core.expressions.OperatorDescription;
import org.knime.core.expressions.aggregations.BuiltInAggregations;
import org.knime.core.expressions.functions.BuiltInFunctions;
import org.knime.core.expressions.functions.ExpressionFunction;

/** Function catalog data used by the expression editor side panes. */
@SuppressWarnings("restriction")
record StateAwareOCELFunctionCatalogData(List<OperatorCategory> categories, List<OperatorDescription> functions) {

    static final StateAwareOCELFunctionCatalogData BUILT_IN_NO_AGGREGATIONS =
        new StateAwareOCELFunctionCatalogData(getBuiltInCategories(), getBuiltInOperators());

    private static List<OperatorDescription> getBuiltInOperators() {
        final var operators = new ArrayList<OperatorDescription>();
        operators.addAll(BuiltInFunctions.BUILT_IN_FUNCTIONS.stream().map(ExpressionFunction::description).toList());
        operators.addAll(Arrays.stream(ExpressionConstants.values()).map(ExpressionConstants::toOperatorDescription)
            .toList());
        return operators;
    }

    private static List<OperatorCategory> getBuiltInCategories() {
        final var categories = new ArrayList<OperatorCategory>();
        categories.add(ExpressionConstants.CONSTANTS_CATEGORY);
        categories.addAll(BuiltInFunctions.META_CATEGORY_CONTROL);
        categories.addAll(BuiltInFunctions.META_CATEGORY_MATH);
        categories.addAll(BuiltInFunctions.META_CATEGORY_STRING);
        categories.addAll(BuiltInFunctions.META_CATEGORY_TEMPORAL);
        // Keep aggregation categories out of the catalog because state rules are row-wise.
        categories.removeAll(BuiltInAggregations.BUILT_IN_CATEGORIES);
        return categories;
    }
}
