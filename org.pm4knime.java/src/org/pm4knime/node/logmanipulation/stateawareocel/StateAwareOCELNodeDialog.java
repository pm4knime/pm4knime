package org.pm4knime.node.logmanipulation.stateawareocel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

import org.knime.core.data.DataTableSpec;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObjectSpec;
import org.pm4knime.node.logmanipulation.stateawareocel.StateAwareOCELNodeSettings.StateRule;

/**
 * Swing dialog for the State-Aware OCEL Enricher. It intentionally mirrors the KNIME Expression node idea: a compact
 * configuration area and an expression editor that can be enlarged, but here users maintain one boolean expression per
 * state instead of one expression per output column.
 */
final class StateAwareOCELNodeDialog extends NodeDialogPane {

    private final StateAwareOCELNodeSettings m_settings = new StateAwareOCELNodeSettings();

    private final RulesTableModel m_rulesTableModel = new RulesTableModel();

    private final JTable m_rulesTable = new JTable(m_rulesTableModel);

    private final JTextArea m_expressionEditor = new JTextArea(12, 70);

    private final JList<String> m_columnsList = new JList<>();

    private final JComboBox<String> m_eventIdColumn = new JComboBox<>();

    private final JComboBox<String> m_objectIdColumn = new JComboBox<>();

    private final JComboBox<String> m_timestampColumn = new JComboBox<>();

    private final JComboBox<String> m_activityColumn = new JComboBox<>();

    private final JTextField m_stateColumn = new JTextField(StateAwareOCELNodeSettings.DEFAULT_STATE_COLUMN, 24);

    private final JTextField m_stateAwareActivityColumn =
        new JTextField(StateAwareOCELNodeSettings.DEFAULT_STATE_AWARE_ACTIVITY_COLUMN, 24);

    private final JTextField m_defaultState = new JTextField(StateAwareOCELNodeSettings.DEFAULT_STATE, 24);

    private final JCheckBox m_addTransitionEvents = new JCheckBox("Insert object state transition events");

    private final JCheckBox m_coalesceTransitionEvents =
        new JCheckBox("Coalesce simultaneous equal transition events");

    private final JTextField m_transitionPrefix = new JTextField(StateAwareOCELNodeSettings.DEFAULT_TRANSITION_PREFIX, 24);

    private final JTextField m_transitionEventIdPrefix =
        new JTextField(StateAwareOCELNodeSettings.DEFAULT_TRANSITION_EVENT_ID_PREFIX, 24);

    private final JSpinner m_epsilonSeconds = new JSpinner(new SpinnerNumberModel(
        StateAwareOCELNodeSettings.DEFAULT_EPSILON_SECONDS, 0.0d, 3600.0d, 0.001d));

    private boolean m_updatingEditor;

    StateAwareOCELNodeDialog() {
        m_expressionEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        m_expressionEditor.setLineWrap(false);
        m_rulesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_rulesTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        m_rulesTable.getColumnModel().getColumn(1).setPreferredWidth(650);
        m_rulesTable.getSelectionModel().addListSelectionListener(e -> showSelectedRuleInEditor());
        m_expressionEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent e) {
                updateSelectedRuleExpression();
            }

            @Override
            public void removeUpdate(final DocumentEvent e) {
                updateSelectedRuleExpression();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                updateSelectedRuleExpression();
            }
        });
        m_columnsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() == 2) {
                    insertColumnReference(m_columnsList.getSelectedValue());
                }
            }
        });

        addTab("State rules", createMainPanel());
        addTab("Output and transitions", createOutputPanel());
    }

    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings, final PortObjectSpec[] specs)
        throws NotConfigurableException {
        final PortObjectSpec firstSpec = specs == null || specs.length == 0 ? null : specs[0];
        if (!(firstSpec instanceof DataTableSpec inputSpec)) {
            throw new NotConfigurableException("Connect an event-object table before configuring this node.");
        }
        m_settings.loadSettingsForDialog(settings, inputSpec);
        updateColumnModels(inputSpec);
        loadSettingsIntoControls();
        if (m_rulesTableModel.getRowCount() > 0) {
            m_rulesTable.setRowSelectionInterval(0, 0);
        }
    }

    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) throws InvalidSettingsException {
        if (m_rulesTable.isEditing()) {
            m_rulesTable.getCellEditor().stopCellEditing();
        }
        updateSettingsFromControls();
        m_settings.validateBasic();
        m_settings.saveSettingsTo(settings);
    }

    private JPanel createMainPanel() {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createRulesPanel(), createEditorPanel());
        split.setResizeWeight(0.42d);
        root.add(split, BorderLayout.CENTER);
        root.add(createSyntaxHelpPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createRulesPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Ordered state rules"));
        panel.add(new JScrollPane(m_rulesTable), BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JButton add = new JButton("Add state");
        final JButton remove = new JButton("Remove");
        final JButton up = new JButton("Move up");
        final JButton down = new JButton("Move down");
        add.addActionListener(e -> addRule());
        remove.addActionListener(e -> removeSelectedRule());
        up.addActionListener(e -> moveSelectedRule(-1));
        down.addActionListener(e -> moveSelectedRule(1));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(up);
        buttons.add(down);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createEditorPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Expression for selected state"));
        panel.add(new JScrollPane(m_expressionEditor), BorderLayout.CENTER);

        final JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("Input columns"));
        right.add(new JScrollPane(m_columnsList), BorderLayout.CENTER);
        final JLabel columnsHint = new JLabel("Double-click a column to insert $[\"column\"].");
        right.add(columnsHint, BorderLayout.SOUTH);
        panel.add(right, BorderLayout.EAST);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JButton enlarge = new JButton("Open enlarged editor...");
        final JButton insertExample = new JButton("Insert inventory example");
        enlarge.addActionListener(e -> openEnlargedEditor());
        insertExample.addActionListener(e -> insertExample());
        buttons.add(enlarge);
        buttons.add(insertExample);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createSyntaxHelpPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Expression syntax quick guide"));
        final JTextArea help = new JTextArea("""
            Rules are evaluated top-down; the first expression returning TRUE determines the state. Expressions use the KNIME Expression Language.
            Access columns with $[\"column name\"] or, when the column name has no spaces/special characters, $column_name.
            Examples: $[\"object_stock\"] < $[\"object_safety_stock\"]  |  contains($[\"object_status\"], \"Down\")  |  $[ROW_INDEX] = 0
            Each expression must return BOOLEAN. Missing results are treated as FALSE for state assignment.
            """);
        help.setEditable(false);
        help.setOpaque(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        panel.add(help, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createOutputPanel() {
        final JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(createColumnMappingPanel());
        root.add(Box.createVerticalStrut(8));
        root.add(createOutputColumnsPanel());
        root.add(Box.createVerticalStrut(8));
        root.add(createTransitionPanel());
        return root;
    }

    private JPanel createColumnMappingPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("OCEL table columns"));
        int row = 0;
        addLabelAndComponent(panel, row++, "Event ID column", m_eventIdColumn,
            "Identifier of the event in the combined event-object table.");
        addLabelAndComponent(panel, row++, "Object ID column", m_objectIdColumn,
            "Identifier of the related object. Transitions are detected independently per object.");
        addLabelAndComponent(panel, row++, "Timestamp column", m_timestampColumn,
            "Event timestamp used to order events and timestamp generated transition events.");
        addLabelAndComponent(panel, row++, "Activity column", m_activityColumn,
            "Original activity column. This column is not overwritten.");
        return panel;
    }

    private JPanel createOutputColumnsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Appended output columns"));
        int row = 0;
        addLabelAndComponent(panel, row++, "State column", m_stateColumn,
            "New string column containing the selected state for each event-object relation row.");
        addLabelAndComponent(panel, row++, "State-aware activity column", m_stateAwareActivityColumn,
            "New string column with activity labels such as Goods Receipt (Understock).");
        addLabelAndComponent(panel, row++, "Default state", m_defaultState,
            "State used when none of the configured expressions returns TRUE.");
        final JTextArea metadata = new JTextArea("""
            The node also appends transition metadata columns: is_state_change_event, state_transition_from,
            state_transition_to, state_change_source_event_id, and state_change_affected_objects.
            """);
        metadata.setEditable(false);
        metadata.setOpaque(false);
        metadata.setLineWrap(true);
        metadata.setWrapStyleWord(true);
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 4, 4, 4);
        panel.add(metadata, gbc);
        return panel;
    }

    private JPanel createTransitionPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Optional state transition events"));
        int row = 0;
        final GridBagConstraints checkGbc = new GridBagConstraints();
        checkGbc.gridx = 0;
        checkGbc.gridy = row++;
        checkGbc.gridwidth = 3;
        checkGbc.anchor = GridBagConstraints.WEST;
        checkGbc.insets = new Insets(4, 4, 4, 4);
        panel.add(m_addTransitionEvents, checkGbc);

        final GridBagConstraints coalesceGbc = new GridBagConstraints();
        coalesceGbc.gridx = 0;
        coalesceGbc.gridy = row++;
        coalesceGbc.gridwidth = 3;
        coalesceGbc.anchor = GridBagConstraints.WEST;
        coalesceGbc.insets = new Insets(4, 4, 4, 4);
        panel.add(m_coalesceTransitionEvents, coalesceGbc);

        addLabelAndComponent(panel, row++, "Transition activity prefix", m_transitionPrefix,
            "Prefix used to build labels such as ST CHANGE Normal to Understock.");
        addLabelAndComponent(panel, row++, "Generated event ID prefix", m_transitionEventIdPrefix,
            "Prefix for generated transition event identifiers.");
        addLabelAndComponent(panel, row++, "Timestamp epsilon, seconds", m_epsilonSeconds,
            "Amount subtracted from the source event timestamp so the transition is ordered just before that event.");
        m_addTransitionEvents.addActionListener(e -> updateTransitionControlState());
        updateTransitionControlState();
        return panel;
    }

    private static void addLabelAndComponent(final JPanel panel, final int row, final String labelText,
        final JComponent component, final String tooltip) {
        final JLabel label = new JLabel(labelText);
        label.setToolTipText(tooltip);
        label.setLabelFor(component);

        component.setToolTipText(tooltip);
        final int height = component.getPreferredSize().height;
        component.setPreferredSize(new Dimension(360, height));
        component.setMinimumSize(new Dimension(240, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));

        final GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(4, 4, 4, 8);
        panel.add(label, labelGbc);

        final GridBagConstraints componentGbc = new GridBagConstraints();
        componentGbc.gridx = 1;
        componentGbc.gridy = row;
        componentGbc.weightx = 1.0;
        componentGbc.fill = GridBagConstraints.HORIZONTAL;
        componentGbc.insets = new Insets(4, 4, 4, 4);
        panel.add(component, componentGbc);

        final JLabel description = new JLabel(tooltip);
        final GridBagConstraints descriptionGbc = new GridBagConstraints();
        descriptionGbc.gridx = 2;
        descriptionGbc.gridy = row;
        descriptionGbc.weightx = 0.5;
        descriptionGbc.fill = GridBagConstraints.HORIZONTAL;
        descriptionGbc.insets = new Insets(4, 8, 4, 4);
        panel.add(description, descriptionGbc);
    }

    private void updateColumnModels(final DataTableSpec inputSpec) {
        final String[] columns = inputSpec.getColumnNames();
        m_columnsList.setListData(columns);
        updateComboModel(m_eventIdColumn, columns, m_settings.m_eventIdColumn);
        updateComboModel(m_objectIdColumn, columns, m_settings.m_objectIdColumn);
        updateComboModel(m_timestampColumn, columns, m_settings.m_timestampColumn);
        updateComboModel(m_activityColumn, columns, m_settings.m_activityColumn);
    }

    private static void updateComboModel(final JComboBox<String> combo, final String[] values, final String selected) {
        combo.removeAllItems();
        for (final String value : values) {
            combo.addItem(value);
        }
        if (selected != null && Arrays.asList(values).contains(selected)) {
            combo.setSelectedItem(selected);
        } else if (values.length > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private void loadSettingsIntoControls() {
        m_rulesTableModel.setRules(m_settings.m_rules);
        m_eventIdColumn.setSelectedItem(m_settings.m_eventIdColumn);
        m_objectIdColumn.setSelectedItem(m_settings.m_objectIdColumn);
        m_timestampColumn.setSelectedItem(m_settings.m_timestampColumn);
        m_activityColumn.setSelectedItem(m_settings.m_activityColumn);
        m_stateColumn.setText(m_settings.m_stateColumn);
        m_stateAwareActivityColumn.setText(m_settings.m_stateAwareActivityColumn);
        m_defaultState.setText(m_settings.m_defaultState);
        m_addTransitionEvents.setSelected(m_settings.m_addTransitionEvents);
        m_coalesceTransitionEvents.setSelected(m_settings.m_coalesceTransitionEvents);
        m_transitionPrefix.setText(m_settings.m_transitionPrefix);
        m_transitionEventIdPrefix.setText(m_settings.m_transitionEventIdPrefix);
        m_epsilonSeconds.setValue(m_settings.m_transitionEpsilonSeconds);
        updateTransitionControlState();
    }

    private void updateSettingsFromControls() {
        m_settings.m_eventIdColumn = selectedString(m_eventIdColumn);
        m_settings.m_objectIdColumn = selectedString(m_objectIdColumn);
        m_settings.m_timestampColumn = selectedString(m_timestampColumn);
        m_settings.m_activityColumn = selectedString(m_activityColumn);
        m_settings.m_stateColumn = m_stateColumn.getText().trim();
        m_settings.m_stateAwareActivityColumn = m_stateAwareActivityColumn.getText().trim();
        m_settings.m_defaultState = m_defaultState.getText().trim();
        m_settings.m_addTransitionEvents = m_addTransitionEvents.isSelected();
        m_settings.m_coalesceTransitionEvents = m_coalesceTransitionEvents.isSelected();
        m_settings.m_transitionPrefix = m_transitionPrefix.getText();
        m_settings.m_transitionEventIdPrefix = m_transitionEventIdPrefix.getText().trim();
        m_settings.m_transitionEpsilonSeconds = ((Number)m_epsilonSeconds.getValue()).doubleValue();
        m_settings.m_rules.clear();
        m_settings.m_rules.addAll(m_rulesTableModel.rules());
    }

    private static String selectedString(final JComboBox<String> combo) {
        final Object selected = combo.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    private void showSelectedRuleInEditor() {
        if (m_rulesTable.getSelectedRow() < 0) {
            m_expressionEditor.setText("");
            return;
        }
        final int modelRow = m_rulesTable.convertRowIndexToModel(m_rulesTable.getSelectedRow());
        m_updatingEditor = true;
        try {
            m_expressionEditor.setText(m_rulesTableModel.getRule(modelRow).expression());
            m_expressionEditor.setCaretPosition(0);
        } finally {
            m_updatingEditor = false;
        }
    }

    private void updateSelectedRuleExpression() {
        if (m_updatingEditor || m_rulesTable.getSelectedRow() < 0) {
            return;
        }
        final int modelRow = m_rulesTable.convertRowIndexToModel(m_rulesTable.getSelectedRow());
        m_rulesTableModel.setExpression(modelRow, m_expressionEditor.getText());
    }

    private void addRule() {
        final int index = m_rulesTableModel.addRule(new StateRule("New State", "false"));
        m_rulesTable.setRowSelectionInterval(index, index);
    }

    private void removeSelectedRule() {
        final int selected = m_rulesTable.getSelectedRow();
        if (selected < 0) {
            return;
        }
        final int modelRow = m_rulesTable.convertRowIndexToModel(selected);
        m_rulesTableModel.removeRule(modelRow);
        if (m_rulesTableModel.getRowCount() > 0) {
            final int newRow = Math.min(modelRow, m_rulesTableModel.getRowCount() - 1);
            m_rulesTable.setRowSelectionInterval(newRow, newRow);
        }
    }

    private void moveSelectedRule(final int delta) {
        final int selected = m_rulesTable.getSelectedRow();
        if (selected < 0) {
            return;
        }
        final int modelRow = m_rulesTable.convertRowIndexToModel(selected);
        final int newRow = m_rulesTableModel.moveRule(modelRow, delta);
        m_rulesTable.setRowSelectionInterval(newRow, newRow);
    }

    private void insertColumnReference(final String columnName) {
        if (columnName == null) {
            return;
        }
        m_expressionEditor.replaceSelection("$[\"" + columnName.replace("\\", "\\\\").replace("\"", "\\\"")
            + "\"]");
    }

    private void insertExample() {
        m_expressionEditor.setText("$[\"object_stock\"] < $[\"object_safety_stock\"]");
    }

    private void openEnlargedEditor() {
        final Window owner = SwingUtilities.getWindowAncestor(m_expressionEditor);
        final JDialog dialog = new JDialog(owner, "State expression editor", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        final JTextArea largeEditor = new JTextArea(m_expressionEditor.getText(), 30, 110);
        largeEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        largeEditor.setLineWrap(false);
        dialog.add(new JScrollPane(largeEditor), BorderLayout.CENTER);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        final JButton ok = new JButton("Apply");
        final JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            m_expressionEditor.setText(largeEditor.getText());
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        buttons.add(ok);
        buttons.add(cancel);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setResizable(true);
        dialog.setSize(1000, 720);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void updateTransitionControlState() {
        final boolean enabled = m_addTransitionEvents.isSelected();
        m_coalesceTransitionEvents.setEnabled(enabled);
        m_transitionPrefix.setEnabled(enabled);
        m_transitionEventIdPrefix.setEnabled(enabled);
        m_epsilonSeconds.setEnabled(enabled);
    }

    private static final class RulesTableModel extends AbstractTableModel {
        private final List<StateRule> m_rules = new ArrayList<>();

        @Override
        public int getRowCount() {
            return m_rules.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(final int column) {
            return column == 0 ? "State name" : "Boolean expression";
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final StateRule rule = m_rules.get(rowIndex);
            return columnIndex == 0 ? rule.stateName() : rule.expression();
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
            final StateRule old = m_rules.get(rowIndex);
            if (columnIndex == 0) {
                m_rules.set(rowIndex, new StateRule(value == null ? "" : value.toString(), old.expression()));
            } else {
                m_rules.set(rowIndex, new StateRule(old.stateName(), value == null ? "" : value.toString()));
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        void setRules(final List<StateRule> rules) {
            m_rules.clear();
            m_rules.addAll(rules);
            fireTableDataChanged();
        }

        List<StateRule> rules() {
            return new ArrayList<>(m_rules);
        }

        StateRule getRule(final int row) {
            return m_rules.get(row);
        }

        void setExpression(final int row, final String expression) {
            final StateRule old = m_rules.get(row);
            m_rules.set(row, new StateRule(old.stateName(), expression));
            fireTableCellUpdated(row, 1);
        }

        int addRule(final StateRule rule) {
            m_rules.add(rule);
            final int index = m_rules.size() - 1;
            fireTableRowsInserted(index, index);
            return index;
        }

        void removeRule(final int row) {
            if (row < 0 || row >= m_rules.size()) {
                return;
            }
            m_rules.remove(row);
            fireTableRowsDeleted(row, row);
        }

        int moveRule(final int row, final int delta) {
            final int newRow = Math.max(0, Math.min(m_rules.size() - 1, row + delta));
            if (newRow == row) {
                return row;
            }
            final StateRule rule = m_rules.remove(row);
            m_rules.add(newRow, rule);
            fireTableDataChanged();
            return newRow;
        }
    }
}
