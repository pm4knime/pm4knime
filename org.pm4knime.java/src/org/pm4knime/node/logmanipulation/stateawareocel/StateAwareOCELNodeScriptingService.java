package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.knime.core.data.DataTableSpec;
import org.knime.core.expressions.ExpressionCompileException;
import org.knime.core.expressions.Expressions;
import org.knime.core.expressions.ValueType;
import org.knime.core.node.port.inactive.InactiveBranchPortObjectSpec;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel.InputOutputModelSubItemType;
import org.knime.core.webui.node.dialog.scripting.ScriptingService;

/** Backend service for live state-expression diagnostics and AI code assistance. */
@SuppressWarnings("restriction")
final class StateAwareOCELNodeScriptingService extends ScriptingService {

    StateAwareOCELNodeScriptingService() {
        super(null, type -> false);
    }

    @Override
    public RpcService getJsonRpcService() {
        return new StateAwareOCELRpcService();
    }

    @Override
    public void onDeactivate() {
        // no resources to close
    }

    public final class StateAwareOCELRpcService extends RpcService {

        @Override
        protected CodeGenerationRequest getCodeSuggestionRequest(final String userPrompt, final String currentCode,
            final InputOutputModel[] inputModels) {
            return StateAwareOCELCodeAssistant.createStateRuleCodeGenerationRequest(userPrompt, currentCode,
                inputModels);
        }

        /** Validate every configured state expression. Expressions must compile and infer to BOOLEAN. */
        public List<StateAwareOCELExpressionDiagnosticResult> getStateRuleDiagnostics(final String[] stateNames,
            final String[] expressions) {
            final var inputSpecs = getWorkflowControl().getInputSpec();
            final var inputSpec = inputSpecs == null || inputSpecs.length == 0 ? null : inputSpecs[0];
            if (!(inputSpec instanceof DataTableSpec spec)) {
                final var diagnostic = inputSpec instanceof InactiveBranchPortObjectSpec
                    ? StateAwareOCELExpressionDiagnostic.INACTIVE_INPUT_CONNECTED_DIAGNOSTICS
                    : StateAwareOCELExpressionDiagnostic.NO_INPUT_CONNECTED_DIAGNOSTICS;
                return Collections.nCopies(expressions.length,
                    new StateAwareOCELExpressionDiagnosticResult(diagnostic,
                        InputOutputModelSubItemType.fromDisplayName("UNKNOWN")));
            }

            final List<StateAwareOCELExpressionDiagnosticResult> results = new ArrayList<>();
            for (int i = 0; i < expressions.length; i++) {
                final String stateName = stateNames == null || i >= stateNames.length ? "State " + (i + 1)
                    : stateNames[i];
                results.add(validateExpression(spec, stateName, expressions[i]));
            }
            return results;
        }

        private StateAwareOCELExpressionDiagnosticResult validateExpression(final DataTableSpec spec,
            final String stateName, final String expression) {
            final List<StateAwareOCELExpressionDiagnostic> diagnostics = new ArrayList<>();
            var returnType = InputOutputModelSubItemType.fromDisplayName("UNKNOWN");
            try {
                final var ast = Expressions.parse(expression);
                if (StateAwareOCELExpressionSupport.containsAggregation(ast)) {
                    diagnostics.add(StateAwareOCELExpressionDiagnostic.withSameMessage(
                        "State rule '%s' uses an aggregation. Aggregations are not row-wise and are not supported here; precompute aggregate values upstream and reference the resulting columns."
                            .formatted(stateName),
                        StateAwareOCELExpressionDiagnostic.DiagnosticSeverity.ERROR, Expressions.getTextLocation(ast)));
                    return new StateAwareOCELExpressionDiagnosticResult(diagnostics, returnType);
                }
                final var inferredType = Expressions.inferTypes(ast,
                    StateAwareOCELExpressionSupport.columnToTypeForTypeInference(spec),
                    StateAwareOCELExpressionSupport.flowVariableToTypeForTypeInference());
                returnType = InputOutputModelSubItemType.fromDisplayName(inferredType.baseType().name());
                if (!ValueType.BOOLEAN.equals(inferredType.baseType())) {
                    diagnostics.add(StateAwareOCELExpressionDiagnostic.withSameMessage(
                        "State rule '%s' must return a boolean value, but returns %s."
                            .formatted(stateName, inferredType.baseType().name()),
                        StateAwareOCELExpressionDiagnostic.DiagnosticSeverity.ERROR, Expressions.getTextLocation(ast)));
                }
            } catch (ExpressionCompileException ex) {
                diagnostics.addAll(StateAwareOCELExpressionDiagnostic.fromException(ex));
            }
            return new StateAwareOCELExpressionDiagnosticResult(diagnostics, returnType);
        }
    }
}
