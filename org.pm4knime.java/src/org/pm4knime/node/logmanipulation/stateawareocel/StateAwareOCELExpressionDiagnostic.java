package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.List;

import org.knime.core.expressions.ExpressionCompileError;
import org.knime.core.expressions.ExpressionCompileException;
import org.knime.core.expressions.TextRange;

/** Diagnostic message for one state-rule expression. */
record StateAwareOCELExpressionDiagnostic(String message, String shortMessage, DiagnosticSeverity severity,
    TextRange location) {

    static final List<StateAwareOCELExpressionDiagnostic> NO_INPUT_CONNECTED_DIAGNOSTICS = List.of(withSameMessage(
        "No input table available. Connect a node first.", DiagnosticSeverity.ERROR, null));

    static final List<StateAwareOCELExpressionDiagnostic> INACTIVE_INPUT_CONNECTED_DIAGNOSTICS = List.of(withSameMessage(
        "The input connection is inactive. Connect an active table.", DiagnosticSeverity.ERROR, null));

    static StateAwareOCELExpressionDiagnostic fromError(final ExpressionCompileError error) {
        return switch (error.type()) {
            case SYNTAX -> new StateAwareOCELExpressionDiagnostic(error.message(),
                "The expression has a syntax error.", DiagnosticSeverity.ERROR, error.location());
            default -> withSameMessage(error.message(), DiagnosticSeverity.ERROR, error.location());
        };
    }

    static List<StateAwareOCELExpressionDiagnostic> fromException(final ExpressionCompileException exception) {
        return exception.getErrors().stream().map(StateAwareOCELExpressionDiagnostic::fromError).toList();
    }

    static StateAwareOCELExpressionDiagnostic withSameMessage(final String message, final DiagnosticSeverity severity,
        final TextRange location) {
        return new StateAwareOCELExpressionDiagnostic(message, message, severity, location);
    }

    enum DiagnosticSeverity {
        ERROR,
        WARNING
    }
}
