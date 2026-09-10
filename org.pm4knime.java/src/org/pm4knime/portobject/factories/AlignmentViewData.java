package org.pm4knime.portobject.factories;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pm4knime.portobject.RepResultPortObjectTable;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;

/** Converts ProM replay result objects to the JSON contract consumed by the alignment view. */
public final class AlignmentViewData {

    private AlignmentViewData() {
    }

    public static Map<String, Object> create(final RepResultPortObjectTable portObject) {
        final var replayResult = portObject.getRepResult();
        final List<Map<String, Object>> alignments = new ArrayList<>();
        int representedCases = 0;
        int reliableCases = 0;

        if (replayResult != null) {
            int alignmentIndex = 0;
            for (SyncReplayResult result : replayResult) {
                final List<Map<String, Object>> moves = createMoves(result);
                final List<Map<String, Object>> cases = new ArrayList<>();
                for (int traceIndex : result.getTraceIndex()) {
                    final Map<String, Object> representedCase = new LinkedHashMap<>();
                    representedCase.put("index", traceIndex);
                    representedCase.put("name", getTraceName(portObject, traceIndex));
                    cases.add(representedCase);
                }

                final Map<String, Object> alignment = new LinkedHashMap<>();
                alignment.put("index", alignmentIndex++);
                alignment.put("reliable", result.isReliable());
                alignment.put("cases", cases);
                alignment.put("caseCount", cases.size());
                alignment.put("moves", moves);
                alignment.put("moveCounts", countMoves(result.getStepTypes()));
                alignment.put("metrics", jsonSafeMap(result.getInfo()));
                alignments.add(alignment);

                representedCases += cases.size();
                if (result.isReliable()) {
                    reliableCases += cases.size();
                }
            }
        }

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("alignments", alignments);
        data.put("alignmentCount", alignments.size());
        data.put("caseCount", representedCases);
        data.put("reliableCaseCount", reliableCases);
        data.put("metrics", replayResult == null ? Map.of() : jsonSafeMap(replayResult.getInfo()));

        return Map.of("alignment", data);
    }

    private static List<Map<String, Object>> createMoves(final SyncReplayResult result) {
        final List<Object> nodes = result.getNodeInstance();
        final List<StepTypes> types = result.getStepTypes();
        final int count = Math.min(nodes.size(), types.size());
        final List<Map<String, Object>> moves = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            final Map<String, Object> move = new LinkedHashMap<>();
            move.put("label", nodeLabel(nodes.get(i)));
            move.put("type", types.get(i).name());
            moves.add(move);
        }
        return moves;
    }

    private static Map<String, Integer> countMoves(final List<StepTypes> types) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (StepTypes type : types) {
            counts.merge(type.name(), 1, Integer::sum);
        }
        return counts;
    }

    private static String nodeLabel(final Object node) {
        if (node instanceof Transition transition) {
            return transition.getLabel();
        }
        return String.valueOf(node);
    }

    private static String getTraceName(final RepResultPortObjectTable portObject, final int traceIndex) {
        try {
            final String name = portObject.getLog().getTraceName(traceIndex);
            return name == null || name.isBlank() ? "Case " + traceIndex : name;
        } catch (RuntimeException ex) {
            return "Case " + traceIndex;
        }
    }

    private static Map<String, Object> jsonSafeMap(final Map<?, ?> source) {
        final Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(String.valueOf(key), jsonSafeValue(value)));
        return result;
    }

    private static Object jsonSafeValue(final Object value) {
        if (value == null || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof Number number) {
            final double doubleValue = number.doubleValue();
            return Double.isFinite(doubleValue) ? number : String.valueOf(number);
        }
        return String.valueOf(value);
    }
}
