package org.pm4knime.portobject.factories;

import java.util.List;
import java.util.Optional;

import org.knime.core.webui.data.InitialDataService;
import org.knime.core.webui.data.RpcDataService;
import org.knime.core.webui.node.port.PortView;
import org.knime.core.webui.node.port.PortViewFactory;
import org.knime.core.webui.node.port.PortViewManager;
import org.knime.core.webui.page.Page;
import org.pm4knime.node.visualizations.common.BundlePageResources;
import org.pm4knime.portobject.RepResultPortObjectTable;

/** Registers the modern port view for table-based alignment replay results. */
@SuppressWarnings("restriction")
public final class AlignmentPortViewFactories {

    private static final String ALIGNMENT_PAGE = "src/views/alignment/index.html";
    private static final String VIEW_NAME = "Alignment View";

    private static final PortViewFactory<RepResultPortObjectTable> PORT_VIEW_FACTORY =
        AlignmentPortViewFactories::createPortView;

    private AlignmentPortViewFactories() {
    }

    public static void register() {
        PortViewManager.registerPortViews(
            RepResultPortObjectTable.class,
            List.of(new PortViewManager.PortViewDescriptor(VIEW_NAME, PORT_VIEW_FACTORY)),
            List.of(0),
            List.of(0)
        );
    }

    private static PortView createPortView(final RepResultPortObjectTable portObject) {
        return new PortView() {
            @Override
            public Page getPage() {
                return BundlePageResources.createPage(ALIGNMENT_PAGE);
            }

            @Override
            public Optional<InitialDataService<Object>> createInitialDataService() {
                return Optional.of(InitialDataService.builder(
                    () -> (Object)AlignmentViewData.create(portObject)
                ).build());
            }

            @Override
            public Optional<RpcDataService> createRpcDataService() {
                return Optional.empty();
            }
        };
    }
}
