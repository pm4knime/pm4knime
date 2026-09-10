package org.pm4knime.node.visualizations.jsgraphviz;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.SwingConstants;

import org.knime.base.data.xml.SvgCell;
import org.knime.base.data.xml.SvgImageContent;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.image.ImagePortObject;
import org.knime.core.node.port.image.ImagePortObjectSpec;
import org.knime.node.DefaultModel;
import org.pm4knime.node.visualizations.common.JsonViewData;
import org.pm4knime.portobject.AbstractJSONPortObject;

import org.jgraph.event.GraphModelEvent;
import org.jgraph.event.GraphModelListener;
import org.jgraph.graph.CellView;
import org.jgraph.graph.CellViewFactory;
import org.jgraph.graph.ConnectionSet;
import org.jgraph.graph.DefaultCellViewFactory;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.DefaultGraphCell;
import org.jgraph.graph.DefaultGraphModel;
import org.jgraph.graph.EdgeView;
import org.jgraph.graph.GraphConstants;
import org.jgraph.graph.GraphLayoutCache;
import org.jgraph.graph.GraphModel;
import org.jgraph.graph.VertexView;

import com.jgraph.layout.JGraphFacade;
import com.jgraph.layout.JGraphModelFacade;
import com.jgraph.layout.hierarchical.JGraphHierarchicalLayout;

public final class GraphVisualizerModel {

    private static final double PLACE_SIZE = 50.0;
    private static final double NODE_HEIGHT = 35.0;
    private static final double NODE_MIN_WIDTH = 20.0;
    private static final double LABEL_FONT_SIZE = 14.0;
    private static final double EDGE_LABEL_FONT_SIZE = 12.0;
    private static final double SVG_PADDING = 30.0;

    private GraphVisualizerModel() {
    }

    static void configure(final DefaultModel.ConfigureInput i, final DefaultModel.ConfigureOutput o)
        throws InvalidSettingsException {
        if (!hasConfiguredOutput(o)) {
            return;
        }

        o.setOutSpec(0, new ImagePortObjectSpec(SvgCell.TYPE));
    }

    static void execute(final DefaultModel.ExecuteInput i, final DefaultModel.ExecuteOutput o) {
        try {
            final var portObject = (AbstractJSONPortObject)i.getInPortObject(0);
            if (hasConfiguredOutput(o)) {
                final var imagePort = render(portObject, i.getExecutionContext());
                o.setOutData(0, imagePort);
            }
            o.setInternalData(portObject);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }       
    }

    private static boolean hasConfiguredOutput(final Object output) {
        return configuredOutputCount(output) > 0;
    }

    private static int configuredOutputCount(final Object output) {
        for (final Field field : output.getClass().getDeclaredFields()) {
            if (!field.getType().isArray()) {
                continue;
            }
            final Class<?> componentType = field.getType().getComponentType();
            if (!componentType.getName().startsWith("org.knime.core.node.port.PortObject")) {
                continue;
            }
            field.setAccessible(true);
            try {
                final Object value = field.get(output);
                if (value != null) {
                    return Array.getLength(value);
                }
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Unable to inspect configured output ports.", ex);
            }
        }
        return 0;
    }
    
    static ImagePortObject render(final AbstractJSONPortObject portObject, final ExecutionContext exec)
        throws Exception {

        exec.checkCanceled();

        final var graph = NormalizedGraph.from(portObject);
        final var svg = buildSvg(graph, exec);
        final var content =
            new SvgImageContent(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));

        return new ImagePortObject(content, new ImagePortObjectSpec(SvgCell.TYPE));
    }

    private static String buildSvg(final NormalizedGraph graph, final ExecutionContext exec) throws Exception {
        if (graph.nodes().isEmpty()) {
            return """
                <svg xmlns="http://www.w3.org/2000/svg" width="120" height="60" viewBox="0 0 120 60">
                  <rect width="120" height="60" fill="white"/>
                </svg>
                """;
        }

        final var layout = layoutGraph(graph, exec);
        exec.checkCanceled();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (final var node : layout.nodes().values()) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + node.width());
            maxY = Math.max(maxY, node.y() + node.height());
        }

        for (final var edge : layout.edges()) {
            for (final var point : edge.points()) {
                minX = Math.min(minX, point.getX());
                minY = Math.min(minY, point.getY());
                maxX = Math.max(maxX, point.getX());
                maxY = Math.max(maxY, point.getY());
            }
        }

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) {
            minX = 0;
            minY = 0;
            maxX = 100;
            maxY = 100;
        }

        final var width = Math.max(1.0, (maxX - minX) + 2 * SVG_PADDING);
        final var height = Math.max(1.0, (maxY - minY) + 2 * SVG_PADDING);
        final var offsetX = minX - SVG_PADDING;
        final var offsetY = minY - SVG_PADDING;

        final var svg = new StringBuilder(16_384);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
            .append(fmt(width))
            .append("\" height=\"")
            .append(fmt(height))
            .append("\" viewBox=\"")
            .append(fmt(offsetX))
            .append(' ')
            .append(fmt(offsetY))
            .append(' ')
            .append(fmt(width))
            .append(' ')
            .append(fmt(height))
            .append("\">");
        svg.append("<defs>");
        appendArrowMarker(svg, "arrow-grey", "#808080");
        appendArrowMarker(svg, "arrow-blue", "#000f80");
        appendArrowMarker(svg, "arrow-red", "red");
        appendArrowMarker(svg, "arrow-orange", "orange");
        svg.append("</defs>");
        svg.append("<rect x=\"")
            .append(fmt(offsetX))
            .append("\" y=\"")
            .append(fmt(offsetY))
            .append("\" width=\"")
            .append(fmt(width))
            .append("\" height=\"")
            .append(fmt(height))
            .append("\" fill=\"white\"/>");

        for (final var edge : layout.edges()) {
            appendEdge(svg, edge, graph.processTree());
        }

        for (final var node : layout.nodes().values()) {
            appendNode(svg, node);
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static LayoutResult layoutGraph(final NormalizedGraph graph, final ExecutionContext exec) throws Exception {
        final var model = new DefaultGraphModel();
        final GraphLayoutCache cache = new HeadlessGraphLayoutCache(model, new DefaultCellViewFactory());
        final var connectionSet = new ConnectionSet();

        final var vertexCells = new HashMap<String, DefaultGraphCell>();
        final var edgeCellMap = new HashMap<String, DefaultEdge>();
        final var allCells = new ArrayList<Object>();

        for (final var node : graph.nodes()) {
            exec.checkCanceled();
            final var cell = new DefaultGraphCell(node.id());
            GraphConstants.setBounds(cell.getAttributes(),
                new Rectangle2D.Double(20.0, 20.0, node.width(), node.height()));
            cell.addPort();
            vertexCells.put(node.id(), cell);
            allCells.add(cell);
        }

        final var routedEdges = new ArrayList<LaidOutEdge>();

        for (final var edge : graph.edges()) {
            exec.checkCanceled();
            final var sourceCell = vertexCells.get(edge.source());
            final var targetCell = vertexCells.get(edge.target());
            if (sourceCell == null || targetCell == null) {
                continue;
            }

            final var edgeCell = new DefaultEdge(edge.id());
            connectionSet.connect(edgeCell, sourceCell.getChildAt(0), targetCell.getChildAt(0));
            edgeCellMap.put(edge.id(), edgeCell);
            allCells.add(edgeCell);
            routedEdges.add(new LaidOutEdge(edge.id(), edge, List.of(), ""));
        }

        model.insert(allCells.toArray(), null, connectionSet, null, null);

        final JGraphFacade facade = new JGraphFacade(cache);
        facade.setIgnoresHiddenCells(true);
        facade.setIgnoresUnconnectedCells(true);
        facade.setDirected(true);
        final var hierarchicalLayout = new JGraphHierarchicalLayout(true);
        hierarchicalLayout.setOrientation(graph.topToBottom() ? SwingConstants.NORTH : SwingConstants.WEST);
        hierarchicalLayout.setDeterministic(true);
        hierarchicalLayout.run(facade);
        cache.edit(facade.createNestedMap(true, true));

        final var laidOutNodes = new HashMap<String, LaidOutNode>();
        for (final var node : graph.nodes()) {
            exec.checkCanceled();
            final var cell = vertexCells.get(node.id());
            final var view = (VertexView)cache.getMapping(cell, false);
            final var bounds = view == null ? null : view.getBounds();

            final double x = bounds == null ? 20.0 : bounds.getX();
            final double y = bounds == null ? 20.0 : bounds.getY();
            final double width = bounds == null ? node.width() : bounds.getWidth();
            final double height = bounds == null ? node.height() : bounds.getHeight();

            laidOutNodes.put(node.id(), new LaidOutNode(node, x, y, width, height));
        }

        final var laidOutEdges = new ArrayList<LaidOutEdge>();
        for (final var laidOutEdge : routedEdges) {
            exec.checkCanceled();
            final var edgeCell = edgeCellMap.get(laidOutEdge.id());
            if (edgeCell == null) {
                continue;
            }

            final var edgeView = (EdgeView)cache.getMapping(edgeCell, false);
            if (edgeView == null || edgeView.getPointCount() < 2) {
                continue;
            }

            final var points = new ArrayList<Point2D>(edgeView.getPointCount());
            for (int i = 0; i < edgeView.getPointCount(); i++) {
                final var point = edgeView.getPoint(i);
                if (point != null) {
                    points.add(new Point2D.Double(point.getX(), point.getY()));
                }
            }
            if (points.size() < 2) {
                continue;
            }

            laidOutEdges.add(new LaidOutEdge(laidOutEdge.id(), laidOutEdge.edge(), points,
                determineDisplayedLabel(laidOutEdge.edge())));
        }

        return new LayoutResult(laidOutNodes, laidOutEdges);
    }

    private static void appendNode(final StringBuilder svg, final LaidOutNode node) {
        final var style = NodeStyle.from(node.node());

        svg.append("<g>");
        if (!style.tooltip().isBlank()) {
            svg.append("<title>")
                .append(escapeXml(style.tooltip()))
                .append("</title>");
        }

        if (style.isPlace()) {
            final var cx = node.x() + node.width() / 2.0;
            final var cy = node.y() + node.height() / 2.0;
            final var radius = Math.min(node.width(), node.height()) / 2.0 - 2.0;

            svg.append("<circle cx=\"")
                .append(fmt(cx))
                .append("\" cy=\"")
                .append(fmt(cy))
                .append("\" r=\"")
                .append(fmt(radius))
                .append("\" fill=\"white\" stroke=\"")
                .append(style.stroke())
                .append("\" stroke-width=\"")
                .append(fmt(style.strokeWidth()))
                .append("\"/>");

            if (node.node().initial()) {
                svg.append("<circle cx=\"")
                    .append(fmt(cx))
                    .append("\" cy=\"")
                    .append(fmt(cy))
                    .append("\" r=\"7\" fill=\"#38761d\"/>");
            }
            svg.append("</g>");
            return;
        }

        svg.append("<rect x=\"")
            .append(fmt(node.x()))
            .append("\" y=\"")
            .append(fmt(node.y()))
            .append("\" width=\"")
            .append(fmt(node.width()))
            .append("\" height=\"")
            .append(fmt(node.height()))
            .append("\" rx=\"4\" ry=\"4\" fill=\"")
            .append(style.fill())
            .append("\" stroke=\"")
            .append(style.stroke())
            .append("\" stroke-width=\"")
            .append(fmt(style.strokeWidth()))
            .append("\"/>");

        final var label = style.label();
        if (!label.isBlank()) {
            svg.append("<text x=\"")
                .append(fmt(node.x() + node.width() / 2.0))
                .append("\" y=\"")
                .append(fmt(node.y() + node.height() / 2.0))
                .append("\" font-family=\"Arial, sans-serif\" font-size=\"")
                .append(fmt(LABEL_FONT_SIZE))
                .append("\" fill=\"black\" text-anchor=\"middle\" dominant-baseline=\"middle\">")
                .append(escapeXml(label))
                .append("</text>");
        }
        svg.append("</g>");
    }

    private static void appendEdge(final StringBuilder svg, final LaidOutEdge edge, final boolean processTree) {
        final var style = EdgeStyle.from(edge.edge(), processTree);
        final var path = buildPath(edge.points());

        svg.append("<path d=\"")
            .append(path)
            .append("\" fill=\"none\" stroke=\"")
            .append(style.stroke())
            .append("\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\"");

        if (!style.dashArray().isEmpty()) {
            svg.append(" stroke-dasharray=\"").append(style.dashArray()).append('"');
        }
        if (!style.markerId().isEmpty()) {
            svg.append(" marker-end=\"url(#").append(style.markerId()).append(")\"");
        }
        svg.append("/>");

        final var label = edge.label();
        if (!label.isBlank()) {
            final var labelPoint = midpoint(edge.points());
            final var labelWidth = Math.max(18.0, estimateTextWidth(label, EDGE_LABEL_FONT_SIZE) + 8.0);
            final var labelHeight = 18.0;

            svg.append("<rect x=\"")
                .append(fmt(labelPoint.getX() - labelWidth / 2.0))
                .append("\" y=\"")
                .append(fmt(labelPoint.getY() - labelHeight / 2.0))
                .append("\" width=\"")
                .append(fmt(labelWidth))
                .append("\" height=\"")
                .append(fmt(labelHeight))
                .append("\" fill=\"white\" stroke=\"none\" rx=\"3\" ry=\"3\"/>");
            svg.append("<text x=\"")
                .append(fmt(labelPoint.getX()))
                .append("\" y=\"")
                .append(fmt(labelPoint.getY()))
                .append("\" font-family=\"Arial, sans-serif\" font-size=\"")
                .append(fmt(EDGE_LABEL_FONT_SIZE))
                .append("\" fill=\"black\" text-anchor=\"middle\" dominant-baseline=\"middle\">")
                .append(escapeXml(label))
                .append("</text>");
        }
    }

    private static void appendArrowMarker(final StringBuilder svg, final String id, final String color) {
        svg.append("<marker id=\"")
            .append(id)
            .append("\" markerWidth=\"10\" markerHeight=\"7\" refX=\"9\" refY=\"3.5\" orient=\"auto\" markerUnits=\"strokeWidth\">")
            .append("<path d=\"M 0 0 L 10 3.5 L 0 7 z\" fill=\"")
            .append(color)
            .append("\" stroke=\"")
            .append(color)
            .append("\"/>")
            .append("</marker>");
    }

    private static String buildPath(final List<Point2D> points) {
        final var path = new StringBuilder(256);
        for (int i = 0; i < points.size(); i++) {
            final var point = points.get(i);
            path.append(i == 0 ? "M " : " L ")
                .append(fmt(point.getX()))
                .append(' ')
                .append(fmt(point.getY()));
        }
        return path.toString();
    }

    private static Point2D midpoint(final List<Point2D> points) {
        if (points.size() == 2) {
            final var start = points.get(0);
            final var end = points.get(1);
            return new Point2D.Double((start.getX() + end.getX()) / 2.0, (start.getY() + end.getY()) / 2.0);
        }

        double totalLength = 0.0;
        for (int i = 1; i < points.size(); i++) {
            totalLength += points.get(i - 1).distance(points.get(i));
        }

        double traversed = 0.0;
        final var half = totalLength / 2.0;
        for (int i = 1; i < points.size(); i++) {
            final var start = points.get(i - 1);
            final var end = points.get(i);
            final var segmentLength = start.distance(end);
            if (traversed + segmentLength >= half && segmentLength > 0.0) {
                final var ratio = (half - traversed) / segmentLength;
                return new Point2D.Double(
                    start.getX() + ratio * (end.getX() - start.getX()),
                    start.getY() + ratio * (end.getY() - start.getY()));
            }
            traversed += segmentLength;
        }
        return points.get(points.size() / 2);
    }

    private static String determineDisplayedLabel(final EdgeData edge) {
        if (edge.frequency() == null) {
            return "";
        }

        final int frequency = edge.frequency().intValue();
        if (frequency > 0) {
            return Integer.toString(frequency);
        }
        if (frequency == -1) {
            return "do";
        }
        return "";
    }

    private static double estimateTextWidth(final String text, final double fontSize) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }
        return text.length() * fontSize * 0.7;
    }

    private static String operatorSymbol(final String label) {
        return switch (label) {
            case "xlp" -> "\u2B6F";
            case "xor" -> "\u2716";
            case "and" -> "\u271A";
            case "seq" -> "\u279C";
            default -> label == null ? "" : label;
        };
    }

    private static String operatorTooltip(final String label) {
        return switch (label) {
            case "xlp" -> "Loop operator";
            case "xor" -> "Exclusive choice operator";
            case "and" -> "Parallel operator";
            case "seq" -> "Sequence operator";
            default -> "";
        };
    }

    private static String escapeXml(final String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String fmt(final double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record LayoutResult(Map<String, LaidOutNode> nodes, List<LaidOutEdge> edges) {
    }

    private record LaidOutNode(NodeData node, double x, double y, double width, double height) {
    }

    private record LaidOutEdge(String id, EdgeData edge, List<Point2D> points, String label) {
    }

    private record NodeData(String id, String type, String label, boolean initial, boolean fin, double width,
        double height) {
    }

    private record EdgeData(String id, String source, String target, String type, Integer frequency) {
    }

    private record NormalizedGraph(List<NodeData> nodes, List<EdgeData> edges, boolean topToBottom,
        boolean processTree) {

        static NormalizedGraph from(final AbstractJSONPortObject portObject) {
            final var normalized = JsonViewData.normalize(portObject);
            final var nodeItems = asListOfMaps(normalized.get("nodes"));
            final var edgeItems = asListOfMaps(normalized.get("links"));

            final var nodes = new ArrayList<NodeData>(nodeItems.size());
            final var edges = new ArrayList<EdgeData>(edgeItems.size());

            boolean hasActivityNode = false;
            boolean hasOperatorNode = false;
            boolean hasFrequency = false;

            for (final var node : nodeItems) {
                final var type = stringValue(node.get("type"));
                final var label = stringValue(node.get("label"));
                final boolean initial = booleanValue(node.get("initial")) || booleanValue(node.get("i_marking"));
                final boolean fin = booleanValue(node.get("final")) || booleanValue(node.get("f_marking"));

                if ("activity".equalsIgnoreCase(type)) {
                    hasActivityNode = true;
                }
                if ("operator".equalsIgnoreCase(type)) {
                    hasOperatorNode = true;
                }

                final boolean isPlace = "place".equalsIgnoreCase(type);
                final double width = isPlace ? PLACE_SIZE : Math.max(NODE_MIN_WIDTH, estimateTextWidth(label,
                    LABEL_FONT_SIZE) + 10.0);
                final double height = isPlace ? PLACE_SIZE : NODE_HEIGHT;

                nodes.add(new NodeData(stringValue(node.get("id")), type, label, initial, fin, width, height));
            }

            int edgeIndex = 0;
            for (final var edge : edgeItems) {
                final Integer frequency = integerValue(edge.get("frequency"));
                if (frequency != null) {
                    hasFrequency = true;
                }
                edges.add(new EdgeData(
                    "edge-" + edgeIndex++,
                    stringValue(edge.get("source")),
                    stringValue(edge.get("target")),
                    stringValue(edge.get("type")),
                    frequency));
            }

            return new NormalizedGraph(nodes, edges, hasActivityNode || hasFrequency, hasOperatorNode);
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> asListOfMaps(final Object value) {
            if (value instanceof List<?> list) {
                final var result = new ArrayList<Map<String, Object>>(list.size());
                for (final var item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add((Map<String, Object>)map);
                    }
                }
                return result;
            }
            return List.of();
        }

        private static String stringValue(final Object value) {
            return value == null ? "" : String.valueOf(value);
        }

        private static boolean booleanValue(final Object value) {
            if (value instanceof Boolean bool) {
                return bool.booleanValue();
            }
            return value != null && Boolean.parseBoolean(String.valueOf(value));
        }

        private static Integer integerValue(final Object value) {
            if (value instanceof Number number) {
                return Integer.valueOf(number.intValue());
            }
            if (value == null) {
                return null;
            }
            final var text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private record NodeStyle(boolean isPlace, String fill, String stroke, double strokeWidth, String label,
        String tooltip) {

        static NodeStyle from(final NodeData node) {
            final var type = node.type().toLowerCase(Locale.ROOT);
            if ("place".equals(type)) {
                return new NodeStyle(true, "white", "grey", node.fin() ? 4.0 : 2.0, "", "");
            }
            if ("transition".equals(type)) {
                return new NodeStyle(false, "#cfe2f3", "#3f77cf", 2.0, node.label(), "");
            }
            if ("artificial start".equals(type)) {
                return new NodeStyle(false, "#c8fcc0", "#167f06", 2.0, node.label(), "");
            }
            if ("artificial end".equals(type)) {
                return new NodeStyle(false, "#fcb6b6", "#c30909", 2.0, node.label(), "");
            }
            if ("operator".equals(type)) {
                return new NodeStyle(false, "#add8e6", "#87ceeb", 2.0, operatorSymbol(node.label()),
                    operatorTooltip(node.label()));
            }
            return new NodeStyle(false, "#e0e0e0", "#999999", 2.0, node.label(), "");
        }
    }

    private record EdgeStyle(String stroke, String dashArray, String markerId) {

        static EdgeStyle from(final EdgeData edge, final boolean processTree) {
            if ("SureEdge".equals(edge.type()) || "HybridDirectedSureGraphEdge".equals(edge.type())) {
                return new EdgeStyle("#000f80", "", "arrow-blue");
            }
            if ("UncertainEdge".equals(edge.type()) || "HybridDirectedUncertainGraphEdge".equals(edge.type())) {
                return new EdgeStyle("red", "4,2", "arrow-red");
            }
            if ("LongDepEdge".equals(edge.type()) || "HybridDirectedLongDepGraphEdge".equals(edge.type())) {
                return new EdgeStyle("orange", "", "arrow-orange");
            }
            if (edge.frequency() != null) {
                final int frequency = edge.frequency().intValue();
                if (frequency > 0) {
                    return new EdgeStyle("#000f80", "", processTree ? "" : "arrow-blue");
                }
                if (frequency == 0 || frequency < -1 || frequency == -1) {
                    return new EdgeStyle("#000f80", "", "");
                }
            }
            return new EdgeStyle("grey", "", "arrow-grey");
        }
    }

    private static final class HeadlessGraphLayoutCache extends GraphLayoutCache implements GraphModelListener {

        HeadlessGraphLayoutCache(final GraphModel model, final CellViewFactory factory) {
            this(model, factory, null, null, false);
        }

        HeadlessGraphLayoutCache(final GraphModel model, final CellViewFactory factory, final CellView[] cellViews,
            final CellView[] hiddenCellViews, final boolean partial) {
            super(model, factory, cellViews, hiddenCellViews, partial);
            if (graphModel != null) {
                graphModel.addGraphModelListener(this);
            }
        }

        @Override
        public void graphChanged(final GraphModelEvent event) {
            graphChanged(event.getChange());
        }
    }
}
