package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.HashMap;
import java.util.Map;

import org.knime.core.expressions.Expressions;
import org.knime.core.expressions.aggregations.BuiltInAggregations;
import org.knime.core.expressions.functions.BuiltInFunctions;
import org.knime.core.node.config.ConfigRO;
import org.knime.core.node.config.ConfigWO;

/** Stores the expression-language and built-in operator versions with the node settings. */
@SuppressWarnings("restriction")
final class StateAwareOCELExpressionVersionSettings {

    private static final String CFG_KEY_LANGUAGE_VERSION = "languageVersion";
    private static final String CFG_KEY_BUILTIN_FUNCTIONS_VERSION = "builtinFunctionsVersion";
    private static final String CFG_KEY_BUILTIN_AGGREGATIONS_VERSION = "builtinAggregationsVersion";

    private int m_languageVersion = Expressions.LANGUAGE_VERSION;
    private int m_builtinFunctionsVersion = BuiltInFunctions.FUNCTIONS_VERSION;
    private int m_builtinAggregationsVersion = BuiltInAggregations.AGGREGATIONS_VERSION;

    void loadSettingsFrom(final ConfigRO settings) {
        m_languageVersion = settings.getInt(CFG_KEY_LANGUAGE_VERSION, m_languageVersion);
        m_builtinFunctionsVersion = settings.getInt(CFG_KEY_BUILTIN_FUNCTIONS_VERSION, m_builtinFunctionsVersion);
        m_builtinAggregationsVersion = settings.getInt(CFG_KEY_BUILTIN_AGGREGATIONS_VERSION,
            m_builtinAggregationsVersion);
    }

    void saveSettingsTo(final ConfigWO settings) {
        settings.addInt(CFG_KEY_LANGUAGE_VERSION, m_languageVersion);
        settings.addInt(CFG_KEY_BUILTIN_FUNCTIONS_VERSION, m_builtinFunctionsVersion);
        settings.addInt(CFG_KEY_BUILTIN_AGGREGATIONS_VERSION, m_builtinAggregationsVersion);
    }

    Map<String, Object> getVersionSettingsMap() {
        final var map = new HashMap<String, Object>();
        map.put(CFG_KEY_LANGUAGE_VERSION, m_languageVersion);
        map.put(CFG_KEY_BUILTIN_FUNCTIONS_VERSION, m_builtinFunctionsVersion);
        map.put(CFG_KEY_BUILTIN_AGGREGATIONS_VERSION, m_builtinAggregationsVersion);
        return map;
    }

    void writeMapToNodeSettings(final Map<String, Object> data) {
        m_languageVersion = intValue(data.get(CFG_KEY_LANGUAGE_VERSION), m_languageVersion);
        m_builtinFunctionsVersion = intValue(data.get(CFG_KEY_BUILTIN_FUNCTIONS_VERSION), m_builtinFunctionsVersion);
        m_builtinAggregationsVersion = intValue(data.get(CFG_KEY_BUILTIN_AGGREGATIONS_VERSION),
            m_builtinAggregationsVersion);
    }

    private static int intValue(final Object value, final int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
