package org.pm4knime.node.logmanipulation.stateawareocel;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.webui.node.dialog.NodeDialog;
import org.knime.core.webui.node.dialog.NodeDialogFactory;
import org.knime.core.webui.node.dialog.NodeDialogManager;

/** Factory for the State-Aware OCEL Enricher node. */
@SuppressWarnings("restriction")
public final class StateAwareOCELNodeFactory extends NodeFactory<StateAwareOCELNodeModel>
    implements NodeDialogFactory {

    @Override
    public StateAwareOCELNodeModel createNodeModel() {
        return new StateAwareOCELNodeModel();
    }

    @Override
    public int getNrNodeViews() {
        return 0;
    }

    @Override
    public NodeView<StateAwareOCELNodeModel> createNodeView(final int viewIndex,
        final StateAwareOCELNodeModel nodeModel) {
        return null;
    }

    @Override
    public boolean hasDialog() {
        return true;
    }

    @Override
    public NodeDialogPane createNodeDialogPane() {
        return NodeDialogManager.createLegacyFlowVariableNodeDialog(createNodeDialog());
    }

    @Override
    public NodeDialog createNodeDialog() {
        return new StateAwareOCELNodeDialog();
    }
}
