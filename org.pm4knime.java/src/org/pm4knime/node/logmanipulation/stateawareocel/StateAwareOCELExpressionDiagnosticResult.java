package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.List;

import org.knime.core.webui.node.dialog.scripting.InputOutputModel.InputOutputModelSubItemType;

/** Diagnostics and inferred return type for one state-rule expression. */
record StateAwareOCELExpressionDiagnosticResult(List<StateAwareOCELExpressionDiagnostic> diagnostics,
    InputOutputModelSubItemType returnType) {
}
