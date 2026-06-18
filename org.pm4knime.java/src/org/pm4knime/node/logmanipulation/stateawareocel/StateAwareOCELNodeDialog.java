package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.workflow.NodeContext;
import org.knime.core.webui.data.RpcDataService;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeSettingsService;
import org.knime.core.webui.node.dialog.SettingsType;
import org.knime.core.webui.node.dialog.scripting.GenericInitialDataBuilder;
import org.knime.core.webui.node.dialog.scripting.ScriptingNodeSettingsService;
import org.knime.core.webui.node.dialog.scripting.WorkflowControl;
import org.knime.core.webui.page.Page;

/** Modern WebUI dialog for the State-Aware OCEL Enricher. */
@SuppressWarnings("restriction")
final class StateAwareOCELNodeDialog implements NodeDialog {

    @Override
    public Page getPage() {
        return StateAwareOCELPageResources.createPage("state-aware-ocel.html");
    }

    @Override
    public Set<SettingsType> getSettingsTypes() {
        return Set.of(SettingsType.MODEL);
    }

    @Override
    public NodeSettingsService getNodeSettingsService() {
        final var workflowControl = new WorkflowControl(NodeContext.getContext().getNodeContainer());
        final var initialDataBuilder = GenericInitialDataBuilder.createDefaultInitialDataBuilder(NodeContext.getContext())
            .addDataSupplier("inputObjects",
                () -> StateAwareOCELInputOutputModelUtils.getTableInputObjects(workflowControl.getInputInfo()))
            .addDataSupplier("flowVariables", StateAwareOCELInputOutputModelUtils::getEmptyFlowVariableInputs)
            .addDataSupplier("functionCatalog", () -> StateAwareOCELFunctionCatalogData.BUILT_IN_NO_AGGREGATIONS)
            .addDataSupplier("columnNamesAndTypes", () -> getColumnNamesAndTypes(workflowControl))
            .addDataSupplier("examples", StateAwareOCELNodeDialog::getExamples);
        return new ScriptingNodeSettingsService(StateAwareOCELNodeSettings::new, initialDataBuilder);
    }

    @Override
    public Optional<RpcDataService> createRpcDataService() {
        final var scriptingService = new StateAwareOCELNodeScriptingService();
        return Optional.of(RpcDataService.builder()
            .addService("ScriptingService", scriptingService.getJsonRpcService())
            .onDeactivate(scriptingService::onDeactivate)
            .build());
    }

    @Override
    public boolean canBeEnlarged() {
        return true;
    }

    private static List<Map<String, Object>> getColumnNamesAndTypes(final WorkflowControl workflowControl) {
        if (workflowControl.getInputInfo() == null || workflowControl.getInputInfo().length == 0
            || workflowControl.getInputInfo()[0] == null
            || !(workflowControl.getInputInfo()[0].portSpec() instanceof DataTableSpec spec)) {
            return List.of();
        }
        return spec.stream().map(column -> {
            final String typeName = StateAwareOCELExpressionSupport.inputOutputModelTypeName(column.getType());
            return Map.<String, Object>of("id", column.getName(), "text", column.getName(), "type",
                Map.of("id", typeName, "text", typeName));
        }).toList();
    }

    private static List<Map<String, Object>> getExamples() {
        return List.of(
            Map.of("name", "Inventory Min-Max states", "description",
                "Classify each material relation into Understock, Normal, or Overstock using dynamic stock, safety-stock, and maximum-stock columns.",
                "rules", List.of(
                    Map.of("stateName", "Understock", "expression", "$[\"object_stock\"] < $[\"object_safety_stock\"]"),
                    Map.of("stateName", "Normal", "expression",
                        "$[\"object_stock\"] >= $[\"object_safety_stock\"] and $[\"object_stock\"] <= $[\"object_max_stock\"]"),
                    Map.of("stateName", "Overstock", "expression", "$[\"object_stock\"] > $[\"object_max_stock\"]"))),
            Map.of("name", "Machine monitoring", "description",
                "Operational object states from machine-status and uptime attributes.", "rules", List.of(
                    Map.of("stateName", "Down", "expression",
                        "contains($[\"object_machine_status\"], \"Down\") or $[\"object_uptime_ratio\"] < 0.1"),
                    Map.of("stateName", "Idle", "expression", "$[\"object_machine_status\"] = \"Idle\""),
                    Map.of("stateName", "Running", "expression", "$[\"object_machine_status\"] = \"Running\""))),
            Map.of("name", "Healthcare triage", "description",
                "Patient states from risk-score and vital-status attributes.", "rules", List.of(
                    Map.of("stateName", "Critical", "expression",
                        "$[\"object_vital_status\"] = \"Critical\" or $[\"object_risk_score\"] >= 0.8"),
                    Map.of("stateName", "At Risk", "expression",
                        "$[\"object_risk_score\"] >= 0.5 and $[\"object_risk_score\"] < 0.8"),
                    Map.of("stateName", "Stable", "expression", "$[\"object_risk_score\"] < 0.5"))));
    }
}
