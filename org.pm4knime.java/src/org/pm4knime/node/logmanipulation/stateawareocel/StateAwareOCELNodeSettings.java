package org.pm4knime.node.logmanipulation.stateawareocel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.StringValue;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

/**
 * Persistent settings for the State-Aware OCEL Enricher node.
 *
 * The state rules are ordered. During execution, every rule expression is evaluated as a boolean KNIME Expression
 * Language expression and the first matching rule determines the row's object state.
 */
final class StateAwareOCELNodeSettings {

    static final String DEFAULT_EVENT_ID_COLUMN = "event_id";

    static final String DEFAULT_OBJECT_ID_COLUMN = "object_id";

    static final String DEFAULT_TIMESTAMP_COLUMN = "timestamp";

    static final String DEFAULT_ACTIVITY_COLUMN = "activity";

    static final String DEFAULT_STATE_COLUMN = "ocel_state";

    static final String DEFAULT_STATE_AWARE_ACTIVITY_COLUMN = "state_aware_activity";

    static final String DEFAULT_STATE = "Undefined";

    static final String DEFAULT_TRANSITION_PREFIX = "ST CHANGE ";

    static final String DEFAULT_TRANSITION_EVENT_ID_PREFIX = "STC";

    static final double DEFAULT_EPSILON_SECONDS = 0.001d;

    static final String COL_IS_STATE_CHANGE_EVENT = "is_state_change_event";

    static final String COL_TRANSITION_FROM = "state_transition_from";

    static final String COL_TRANSITION_TO = "state_transition_to";

    static final String COL_TRANSITION_SOURCE_EVENT = "state_change_source_event_id";

    static final String COL_TRANSITION_AFFECTED_OBJECTS = "state_change_affected_objects";

    private static final String CFG_EVENT_ID_COLUMN = "eventIdColumn";

    private static final String CFG_OBJECT_ID_COLUMN = "objectIdColumn";

    private static final String CFG_TIMESTAMP_COLUMN = "timestampColumn";

    private static final String CFG_ACTIVITY_COLUMN = "activityColumn";

    private static final String CFG_STATE_COLUMN = "stateColumn";

    private static final String CFG_STATE_AWARE_ACTIVITY_COLUMN = "stateAwareActivityColumn";

    private static final String CFG_DEFAULT_STATE = "defaultState";

    private static final String CFG_ADD_TRANSITION_EVENTS = "addTransitionEvents";

    private static final String CFG_COALESCE_TRANSITION_EVENTS = "coalesceTransitionEvents";

    private static final String CFG_TRANSITION_PREFIX = "transitionPrefix";

    private static final String CFG_TRANSITION_EVENT_ID_PREFIX = "transitionEventIdPrefix";

    private static final String CFG_TRANSITION_EPSILON_SECONDS = "transitionEpsilonSeconds";

    private static final String CFG_RULES = "stateRules";

    private static final String CFG_RULE_STATE_NAME = "stateName";

    private static final String CFG_RULE_EXPRESSION = "expression";

    String m_eventIdColumn = DEFAULT_EVENT_ID_COLUMN;

    String m_objectIdColumn = DEFAULT_OBJECT_ID_COLUMN;

    String m_timestampColumn = DEFAULT_TIMESTAMP_COLUMN;

    String m_activityColumn = DEFAULT_ACTIVITY_COLUMN;

    String m_stateColumn = DEFAULT_STATE_COLUMN;

    String m_stateAwareActivityColumn = DEFAULT_STATE_AWARE_ACTIVITY_COLUMN;

    String m_defaultState = DEFAULT_STATE;

    boolean m_addTransitionEvents = false;

    boolean m_coalesceTransitionEvents = false;

    String m_transitionPrefix = DEFAULT_TRANSITION_PREFIX;

    String m_transitionEventIdPrefix = DEFAULT_TRANSITION_EVENT_ID_PREFIX;

    double m_transitionEpsilonSeconds = DEFAULT_EPSILON_SECONDS;

    final List<StateRule> m_rules = new ArrayList<>();

    StateAwareOCELNodeSettings() {
        m_rules.add(new StateRule("Normal", "true"));
    }

    void saveSettingsTo(final NodeSettingsWO settings) {
        settings.addString(CFG_EVENT_ID_COLUMN, nullToEmpty(m_eventIdColumn));
        settings.addString(CFG_OBJECT_ID_COLUMN, nullToEmpty(m_objectIdColumn));
        settings.addString(CFG_TIMESTAMP_COLUMN, nullToEmpty(m_timestampColumn));
        settings.addString(CFG_ACTIVITY_COLUMN, nullToEmpty(m_activityColumn));
        settings.addString(CFG_STATE_COLUMN, nullToEmpty(m_stateColumn));
        settings.addString(CFG_STATE_AWARE_ACTIVITY_COLUMN, nullToEmpty(m_stateAwareActivityColumn));
        settings.addString(CFG_DEFAULT_STATE, nullToEmpty(m_defaultState));
        settings.addBoolean(CFG_ADD_TRANSITION_EVENTS, m_addTransitionEvents);
        settings.addBoolean(CFG_COALESCE_TRANSITION_EVENTS, m_coalesceTransitionEvents);
        settings.addString(CFG_TRANSITION_PREFIX, nullToEmpty(m_transitionPrefix));
        settings.addString(CFG_TRANSITION_EVENT_ID_PREFIX, nullToEmpty(m_transitionEventIdPrefix));
        settings.addDouble(CFG_TRANSITION_EPSILON_SECONDS, m_transitionEpsilonSeconds);

        final var rulesConfig = settings.addConfig(CFG_RULES);
        rulesConfig.addInt("count", m_rules.size());
        for (int i = 0; i < m_rules.size(); i++) {
            final var ruleConfig = rulesConfig.addConfig(Integer.toString(i));
            ruleConfig.addString(CFG_RULE_STATE_NAME, nullToEmpty(m_rules.get(i).stateName()));
            ruleConfig.addString(CFG_RULE_EXPRESSION, nullToEmpty(m_rules.get(i).expression()));
        }
    }

    void loadSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_eventIdColumn = settings.getString(CFG_EVENT_ID_COLUMN);
        m_objectIdColumn = settings.getString(CFG_OBJECT_ID_COLUMN);
        m_timestampColumn = settings.getString(CFG_TIMESTAMP_COLUMN);
        m_activityColumn = settings.getString(CFG_ACTIVITY_COLUMN);
        m_stateColumn = settings.getString(CFG_STATE_COLUMN);
        m_stateAwareActivityColumn = settings.getString(CFG_STATE_AWARE_ACTIVITY_COLUMN);
        m_defaultState = settings.getString(CFG_DEFAULT_STATE);
        m_addTransitionEvents = settings.getBoolean(CFG_ADD_TRANSITION_EVENTS);
        m_coalesceTransitionEvents = settings.getBoolean(CFG_COALESCE_TRANSITION_EVENTS);
        m_transitionPrefix = settings.getString(CFG_TRANSITION_PREFIX);
        m_transitionEventIdPrefix = settings.getString(CFG_TRANSITION_EVENT_ID_PREFIX);
        m_transitionEpsilonSeconds = settings.getDouble(CFG_TRANSITION_EPSILON_SECONDS);
        loadRules(settings);
    }

    void loadSettingsForDialog(final NodeSettingsRO settings, final DataTableSpec inputSpec) {
        try {
            loadSettingsFrom(settings);
        } catch (InvalidSettingsException ex) { // NOSONAR: default settings are intended for first-time configuration
            resetToDefaults();
        }
        autoSelectColumns(inputSpec);
    }

    void validateBasic() throws InvalidSettingsException {
        ensureNotEmpty(m_eventIdColumn, "Event ID column");
        ensureNotEmpty(m_objectIdColumn, "Object ID column");
        ensureNotEmpty(m_timestampColumn, "Timestamp column");
        ensureNotEmpty(m_activityColumn, "Activity column");
        ensureNotEmpty(m_stateColumn, "State column name");
        ensureNotEmpty(m_stateAwareActivityColumn, "State-aware activity column name");
        ensureNotEmpty(m_defaultState, "Default state");
        ensureNotEmpty(m_transitionPrefix, "Transition activity prefix");
        ensureNotEmpty(m_transitionEventIdPrefix, "Generated transition event ID prefix");
        if (m_transitionEpsilonSeconds < 0.0d || Double.isNaN(m_transitionEpsilonSeconds)
            || Double.isInfinite(m_transitionEpsilonSeconds)) {
            throw new InvalidSettingsException("Transition epsilon must be a finite value greater than or equal to 0.");
        }
        validateRules();
        final var appended = getAppendedColumnNames();
        if (new HashSet<>(appended).size() != appended.size()) {
            throw new InvalidSettingsException("Output column names must be unique. Check the configured state and "
                + "state-aware activity column names and the reserved transition metadata names.");
        }
    }

    void validateAgainstSpec(final DataTableSpec spec) throws InvalidSettingsException {
        validateBasic();
        ensureColumnExists(spec, m_eventIdColumn, "Event ID");
        ensureColumnExists(spec, m_objectIdColumn, "Object ID");
        ensureColumnExists(spec, m_timestampColumn, "Timestamp");
        ensureColumnExists(spec, m_activityColumn, "Activity");

        ensureStringCompatible(spec, m_eventIdColumn, "Event ID");
        ensureStringCompatible(spec, m_objectIdColumn, "Object ID");
        ensureStringCompatible(spec, m_activityColumn, "Activity");

        for (final var columnName : getAppendedColumnNames()) {
            if (spec.containsName(columnName)) {
                throw new InvalidSettingsException("The output column '" + columnName + "' already exists in the "
                    + "input table. Choose a different configured output column name or remove the existing column.");
            }
        }
    }

    List<String> getAppendedColumnNames() {
        return List.of(m_stateColumn, m_stateAwareActivityColumn, COL_IS_STATE_CHANGE_EVENT, COL_TRANSITION_FROM,
            COL_TRANSITION_TO, COL_TRANSITION_SOURCE_EVENT, COL_TRANSITION_AFFECTED_OBJECTS);
    }

    private void validateRules() throws InvalidSettingsException {
        if (m_rules.isEmpty()) {
            throw new InvalidSettingsException("Configure at least one state rule.");
        }
        final Set<String> stateNames = new HashSet<>();
        for (int i = 0; i < m_rules.size(); i++) {
            final var rule = m_rules.get(i);
            final String prefix = "State rule " + (i + 1) + ": ";
            if (isBlank(rule.stateName())) {
                throw new InvalidSettingsException(prefix + "state name must not be empty.");
            }
            if (isBlank(rule.expression())) {
                throw new InvalidSettingsException(prefix + "expression must not be empty.");
            }
            if (!stateNames.add(rule.stateName().trim())) {
                throw new InvalidSettingsException("State name '" + rule.stateName() + "' is used more than once.");
            }
        }
    }

    private static void ensureColumnExists(final DataTableSpec spec, final String columnName, final String label)
        throws InvalidSettingsException {
        if (!spec.containsName(columnName)) {
            throw new InvalidSettingsException(label + " column '" + columnName + "' is not available in the input "
                + "table.");
        }
    }

    private static void ensureStringCompatible(final DataTableSpec spec, final String columnName, final String label)
        throws InvalidSettingsException {
        final var type = spec.getColumnSpec(columnName).getType();
        if (!type.isCompatible(StringValue.class)) {
            throw new InvalidSettingsException(label + " column '" + columnName + "' must be string-compatible. "
                + "OCEL identifiers and activities are expected to be strings.");
        }
    }

    private static void ensureNotEmpty(final String value, final String label) throws InvalidSettingsException {
        if (isBlank(value)) {
            throw new InvalidSettingsException(label + " must not be empty.");
        }
    }

    private void loadRules(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_rules.clear();
        final var rulesConfig = settings.getConfig(CFG_RULES);
        final int count = rulesConfig.getInt("count");
        for (int i = 0; i < count; i++) {
            final var ruleConfig = rulesConfig.getConfig(Integer.toString(i));
            m_rules.add(new StateRule(ruleConfig.getString(CFG_RULE_STATE_NAME).trim(),
                ruleConfig.getString(CFG_RULE_EXPRESSION).trim()));
        }
        validateRules();
    }

    private void resetToDefaults() {
        m_eventIdColumn = DEFAULT_EVENT_ID_COLUMN;
        m_objectIdColumn = DEFAULT_OBJECT_ID_COLUMN;
        m_timestampColumn = DEFAULT_TIMESTAMP_COLUMN;
        m_activityColumn = DEFAULT_ACTIVITY_COLUMN;
        m_stateColumn = DEFAULT_STATE_COLUMN;
        m_stateAwareActivityColumn = DEFAULT_STATE_AWARE_ACTIVITY_COLUMN;
        m_defaultState = DEFAULT_STATE;
        m_addTransitionEvents = false;
        m_coalesceTransitionEvents = false;
        m_transitionPrefix = DEFAULT_TRANSITION_PREFIX;
        m_transitionEventIdPrefix = DEFAULT_TRANSITION_EVENT_ID_PREFIX;
        m_transitionEpsilonSeconds = DEFAULT_EPSILON_SECONDS;
        m_rules.clear();
        m_rules.add(new StateRule("Normal", "true"));
    }

    private void autoSelectColumns(final DataTableSpec inputSpec) {
        if (inputSpec == null) {
            return;
        }
        m_eventIdColumn = autoSelect(inputSpec, m_eventIdColumn, DEFAULT_EVENT_ID_COLUMN, "ocel:eid", "ocel_eid",
            "event", "event id");
        m_objectIdColumn = autoSelect(inputSpec, m_objectIdColumn, DEFAULT_OBJECT_ID_COLUMN, "ocel:oid", "ocel_oid",
            "object", "object id");
        m_timestampColumn = autoSelect(inputSpec, m_timestampColumn, DEFAULT_TIMESTAMP_COLUMN, "time:timestamp",
            "ocel:timestamp", "ocel_timestamp", "time", "event_time");
        m_activityColumn = autoSelect(inputSpec, m_activityColumn, DEFAULT_ACTIVITY_COLUMN, "concept:name",
            "ocel:activity", "ocel_activity", "event_type");
    }

    private static String autoSelect(final DataTableSpec spec, final String current, final String... candidates) {
        if (!isBlank(current) && spec.containsName(current)) {
            return current;
        }
        for (final String candidate : candidates) {
            if (spec.containsName(candidate)) {
                return candidate;
            }
        }
        return current;
    }

    private static boolean isBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    record StateRule(String stateName, String expression) {
    }
}
