package org.pm4knime.node.logmanipulation.stateawareocel;

import org.eclipse.core.runtime.Platform;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest.CodeRequestBody;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest.Inputs;
import org.knime.core.webui.node.dialog.scripting.CodeGenerationRequest.Outputs;
import org.knime.core.webui.node.dialog.scripting.InputOutputModel;
import org.knime.core.webui.node.dialog.scripting.InputOutputModelNameAndTypeUtils;

/** AI assistant request builder specialized for one boolean OCEL-state expression. */
@SuppressWarnings("restriction")
final class StateAwareOCELCodeAssistant {

    private static final String EXPRESSION_CORE_PLUGIN_VERSION = getExpressionBundleVersion();

    private StateAwareOCELCodeAssistant() {
        // utility class
    }

    static CodeGenerationRequest createStateRuleCodeGenerationRequest(final String userPrompt, final String oldCode,
        final InputOutputModel[] inputModels) {
        final var inputPorts = new InputOutputModelNameAndTypeUtils.NameAndType[][]{
            InputOutputModelNameAndTypeUtils.getAllSupportedTableColumns(inputModels)};
        final var enhancedPrompt = """
                Generate a single KNIME Expression Language boolean expression for one state rule in a State-Aware OCEL Enricher node.
                The expression is evaluated row-wise on the combined OCEL event-object table and must return true or false.
                Do not return a state name, assignment, SQL, comments, prose, or markdown; return only the expression.
                User request: %s
                """.formatted(userPrompt == null ? "" : userPrompt);
        return new CodeGenerationRequest("/code_generation/knime_expression",
            new CodeRequestBody(oldCode == null ? "" : oldCode, enhancedPrompt,
                new Inputs(inputPorts, 0, InputOutputModelNameAndTypeUtils.getSupportedFlowVariables(inputModels)),
                new Outputs(0, 0, 0, false), EXPRESSION_CORE_PLUGIN_VERSION));
    }

    private static String getExpressionBundleVersion() {
        final var bundle = Platform.getBundle("org.knime.core.expressions");
        if (bundle == null) {
            return "unknown";
        }
        final var version = bundle.getVersion();
        return version.getMajor() + "." + version.getMinor() + "." + version.getMicro();
    }
}
