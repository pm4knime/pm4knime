package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.List;

import org.knime.core.data.DataTableSpec;
import org.knime.core.expressions.ValueType;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel.InputOutputModelSubItem;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel.InputOutputModelSubItemType;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl.InputPortInfo;

/** Input/output-pane data for the KNIME Expression editor. */
@SuppressWarnings("restriction")
final class StateAwareOCELInputOutputModelUtils {

    private static final List<InputOutputModelSubItem> ROW_INFO_SUB_ITEMS = List.of(
        new InputOutputModelSubItem("ROW_NUMBER", InputOutputModelSubItemType.fromDisplayName(ValueType.INTEGER.name()),
            true, "$[ROW_NUMBER]"),
        new InputOutputModelSubItem("ROW_INDEX", InputOutputModelSubItemType.fromDisplayName(ValueType.INTEGER.name()),
            true, "$[ROW_INDEX]"),
        new InputOutputModelSubItem("ROW_ID", InputOutputModelSubItemType.fromDisplayName(ValueType.STRING.name()),
            true, "$[ROW_ID]"));

    private static final String COLUMN_ALIAS_TEMPLATE = """
            {{~#if subItems.[0].insertionText~}}
                {{ subItems.[0].insertionText }}
            {{~else~}}
                $[\" {{~{ escapeDblQuotes subItems.[0].name }~}} \"]
            {{~/if~}}
            """;

    private StateAwareOCELInputOutputModelUtils() {
        // utility class
    }

    static List<InputOutputModel> getTableInputObjects(final InputPortInfo[] inputPorts) {
        if (inputPorts == null || inputPorts.length == 0 || !(inputPorts[0].portSpec() instanceof DataTableSpec spec)) {
            return List.of(InputOutputModel.table().name("Input table")
                .subItemCodeAliasTemplate(COLUMN_ALIAS_TEMPLATE)
                .build());
        }

        return List.of(InputOutputModel.table().name("Input table")
            .subItemCodeAliasTemplate(COLUMN_ALIAS_TEMPLATE)
            .subItems(ROW_INFO_SUB_ITEMS)
            .subItems(spec,
                type -> StateAwareOCELExpressionSupport.mapDataTypeToValueType(type).baseType().name(),
                type -> StateAwareOCELExpressionSupport.mapDataTypeToValueType(type) != null)
            .build());
    }

    static InputOutputModel getEmptyFlowVariableInputs() {
        return InputOutputModel.flowVariables()
            .subItems(List.of())
            .build();
    }
}
