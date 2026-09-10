import dagre from "dagre";
import * as joint from "jointjs";
import "jointjs/dist/joint.css";
import "@fortawesome/fontawesome-free/css/all.min.css";
import graphExportCss from "./graphExportCss.txt?raw";

const normalizedGraphExportCss = graphExportCss.replace(/font-size:\s*22px;/g, "font-size: 14px;");

export type GraphNode = {
  id: string;
  type?: string;
  label?: string;
  position?: { x: number; y: number };
  width?: number;
  height?: number;
  initial?: boolean;
  final?: boolean;
  i_marking?: boolean;
  f_marking?: boolean;
};

export type GraphEdge = {
  source: string;
  target: string;
  type?: string;
  label?: string;
  frequency?: number | string;
};

export type GraphPayload = {
  nodes?: GraphNode[];
  links?: GraphEdge[];
};

type GraphDrag = {
  model: any;
  x: number;
  y: number;
};

type MarqueeSelection = {
  element: SVGRectElement;
  startX: number;
  startY: number;
};

const PADDING_INSIDE_PAPER = 10;
const MIN_ZOOM_LEVEL = 0.2;
const MAX_ZOOM_LEVEL = 3;
const MAX_AUTO_FIT_ZOOM_LEVEL = 1;
let initialGraphState: unknown;

export function renderGraphView(root: HTMLElement, graph: GraphPayload) {
  createGraphElements(root);
  return createPaper(graph.nodes ?? [], graph.links ?? []);
}

function createGraphElements(root: HTMLElement) {
  root.replaceChildren();

  const mainContainer = document.createElement("div");
  mainContainer.id = "main";
  mainContainer.style.display = "flex";
  mainContainer.style.flexDirection = "column";
  mainContainer.style.alignItems = "center";
  mainContainer.style.justifyContent = "center";
  mainContainer.style.border = "1px solid #ccc";
  mainContainer.style.width = "100%";
  mainContainer.style.height = "100%";
  mainContainer.style.background = "white";
  mainContainer.style.boxSizing = "border-box";

  const controlBar = document.createElement("div");
  controlBar.style.background = "#e0e0e0";
  controlBar.style.color = "#fff";
  controlBar.style.width = "100%";
  controlBar.style.fontFamily = "Arial, sans-serif";
  controlBar.style.boxSizing = "border-box";
  controlBar.style.minHeight = "40px";
  controlBar.style.display = "flex";
  controlBar.style.alignItems = "center";

  const graphContainer = document.createElement("div");
  graphContainer.id = "graphContainer";
  graphContainer.style.width = "100%";
  graphContainer.style.height = "calc(100% - 40px)";
  graphContainer.style.overflow = "auto";
  graphContainer.style.margin = "auto";
  graphContainer.style.boxSizing = "border-box";

  const paperDiv = document.createElement("div");
  paperDiv.id = "paper";
  paperDiv.style.width = "100%";
  paperDiv.style.height = "100%";
  paperDiv.style.margin = "auto";

  const controlsDiv = document.createElement("div");
  controlsDiv.id = "zoom-controls";
  controlsDiv.style.display = "flex";
  controlsDiv.style.alignItems = "center";
  controlsDiv.style.gap = "4px";
  controlsDiv.style.padding = "4px 6px";

  const zoomInButton = document.createElement("button");
  zoomInButton.className = "zoom-button";
  zoomInButton.id = "zoom-in";
  zoomInButton.innerHTML = `<i class="fa-solid fa-magnifying-glass-plus"></i>`;

  const zoomOutButton = document.createElement("button");
  zoomOutButton.className = "zoom-button";
  zoomOutButton.id = "zoom-out";
  zoomOutButton.innerHTML = `<i class="fa-solid fa-magnifying-glass-minus"></i>`;

  const resetButton = document.createElement("button");
  resetButton.className = "reset-button";
  resetButton.id = "reset-button";
  resetButton.innerHTML = `<i class="fa-solid fa-rotate-left"></i>`;

  const zoomToFitButton = document.createElement("button");
  zoomToFitButton.className = "zoom-button";
  zoomToFitButton.id = "zoom-to-fit";
  zoomToFitButton.innerHTML = `<i class="fa-solid fa-arrows-to-circle"></i>`;

  const downloadSvgButton = document.createElement("button");
  downloadSvgButton.className = "zoom-button";
  downloadSvgButton.id = "download-svg";
  downloadSvgButton.innerHTML = `<i class="fa-solid fa-download"></i>`;

  controlsDiv.appendChild(zoomInButton);
  controlsDiv.appendChild(zoomOutButton);
  controlsDiv.appendChild(zoomToFitButton);
  controlsDiv.appendChild(resetButton);
  controlsDiv.appendChild(downloadSvgButton);

  controlBar.appendChild(controlsDiv);
  graphContainer.appendChild(paperDiv);
  mainContainer.appendChild(controlBar);
  mainContainer.appendChild(graphContainer);
  root.appendChild(mainContainer);
}

function estimateTextWidth(text: string | undefined, fontSize: number) {
  if (typeof text === "undefined" || text === "") {
    return 0;
  }
  const averageCharWidth = fontSize * 0.7;
  return text.length * averageCharWidth;
}

function adjustPaperSize(graph: any, paper: any) {
  let maxX = 0;
  let maxY = 0;

  graph.getElements().forEach((element: any) => {
    const position = element.position();
    const size = element.size();
    maxX = Math.max(maxX, position.x + size.width);
    maxY = Math.max(maxY, position.y + size.height);
  });

  paper.setDimensions(maxX + 100, maxY + 100);
  fitPaperToContent(paper);
}

function fitPaperToContent(paper: any) {
  const graphContainer = document.getElementById("graphContainer");

  paper.fitToContent({
    useModelGeometry: true,
    padding: PADDING_INSIDE_PAPER,
    allowNewOrigin: "any",
    minWidth: graphContainer?.clientWidth ?? 0,
    minHeight: graphContainer?.clientHeight ?? 0,
  });
}

function expandPaperToContent(paper: any) {
  const graphContainer = document.getElementById("graphContainer");
  if (!graphContainer) {
    return;
  }

  const contentBounds = paper.getContentBBox({ useModelGeometry: true });
  const currentSize = paper.getComputedSize();
  const leftExpansion = Math.max(0, PADDING_INSIDE_PAPER - contentBounds.x);
  const topExpansion = Math.max(0, PADDING_INSIDE_PAPER - contentBounds.y);

  if (leftExpansion > 0 || topExpansion > 0) {
    const translation = paper.translate();
    paper.translate(
      translation.tx + leftExpansion,
      translation.ty + topExpansion,
    );
  }

  paper.setDimensions(
    Math.max(
      graphContainer.clientWidth,
      currentSize.width + leftExpansion,
      contentBounds.x + contentBounds.width + leftExpansion + PADDING_INSIDE_PAPER,
    ),
    Math.max(
      graphContainer.clientHeight,
      currentSize.height + topExpansion,
      contentBounds.y + contentBounds.height + topExpansion + PADDING_INSIDE_PAPER,
    ),
  );

  // Compensate for an expansion to the left or top so nothing jumps on screen.
  graphContainer.scrollLeft += leftExpansion;
  graphContainer.scrollTop += topExpansion;
}

function simplifyWaypoints(points: Array<{ x: number; y: number }>) {
  const simplified: Array<{ x: number; y: number }> = [];

  for (let i = 1; i < points.length - 1; i += 1) {
    const prev = points[i - 1];
    const curr = points[i];
    const next = points[i + 1];

    const det =
      prev.x * (curr.y - next.y) +
      curr.x * (next.y - prev.y) +
      next.x * (prev.y - curr.y);

    if (Math.abs(det) > 1e-10) {
      simplified.push(curr);
    }
  }

  return simplified;
}

function createPaper(nodes: GraphNode[], edges: GraphEdge[]) {
  const paperHost = document.getElementById("paper");

  if (!paperHost) {
    throw new Error("Graph paper container is missing.");
  }

  let tbFlag = 0;
  let processTreeFlag = 0;
  let zoomLevel = 1;

  const graph = new joint.dia.Graph({}, { cellNamespace: joint.shapes });
  const paper = new joint.dia.Paper({
    el: paperHost,
    width: "100%",
    height: "100%",
    defaultAnchor: { name: "perpendicular" },
    defaultConnectionPoint: { name: "boundary" },
    cellViewNamespace: joint.shapes,
    model: graph,
    overflow: true,
  });

  paper.freeze();

  const zoom = (nextZoomLevel: number) => {
    zoomLevel = Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, nextZoomLevel));
    paper.scale(zoomLevel);
    fitPaperToContent(paper);
  };

  const pn = joint.shapes.pn;
  const elements: Record<string, any> = {};
  const nodeElements: any[] = [];

  nodes.forEach((node) => {
    const nodeType = (node.type ?? "").toLowerCase();

    if (nodeType === "activity") {
      tbFlag = 1;
    }

    if (nodeType === "operator") {
      processTreeFlag = 1;
    }

    let element: any;

    if (nodeType === "place") {
      const attrs: Record<string, any> = {
        ".root": { stroke: "grey", "stroke-width": 2 },
        ".tokens > circle": { fill: "#38761d" },
      };
      let tokens = 0;

      if (node.final === true || node.f_marking === true) {
        attrs[".root"]["stroke-width"] = 4;
      }
      if (node.initial === true || node.i_marking === true) {
        tokens = 1;
      }

      node.width = 50;
      node.height = 50;

      element = new pn.Place({
        position: node.position,
        attrs,
        tokens,
        size: { width: 50, height: 50 },
      });
    } else {
      const fontSize = 14;
      const textWidth = estimateTextWidth(node.label, fontSize);
      const transitionWidth = Math.max(textWidth + 10, 20);
      node.width = transitionWidth;
      node.height = 35;

      const baseAttrs = {
        ".label": {
          text: node.label || "",
          fill: "black",
          "ref-x": 0.5,
          "ref-y": 0.5,
          "text-anchor": "middle",
          "y-alignment": "middle",
        },
        ".root": {
          fill: "#e0e0e0",
          stroke: "#999999",
          "stroke-width": 2,
        },
      };

      if (nodeType === "transition") {
        baseAttrs[".root"] = {
          fill: "#cfe2f3",
          stroke: "#3f77cf",
          "stroke-width": 2,
        };
      } else if (nodeType === "artificial start") {
        baseAttrs[".root"] = {
          fill: "#c8fcc0",
          stroke: "#167f06",
          "stroke-width": 2,
        };
      } else if (nodeType === "artificial end") {
        baseAttrs[".root"] = {
          fill: "#fcb6b6",
          stroke: "#c30909",
          "stroke-width": 2,
        };
      } else if (nodeType === "operator") {
        baseAttrs[".label"].text = operatorSymbol(node.label);
        baseAttrs[".root"] = {
          fill: "#add8e6",
          stroke: "#87ceeb",
          "stroke-width": 2,
        };
      }

      element = new pn.Transition({
        position: node.position,
        size: { width: transitionWidth, height: 50 },
        attrs: baseAttrs,
      });
    }

    const tooltip = nodeTooltip(node);
    if (tooltip) {
      element.set("tooltip", tooltip);
    }

    nodeElements.push(element);
    elements[node.id] = element;
  });

  const linkElements: any[] = [];

  edges.forEach((edge) => {
    if (!elements[edge.source] || !elements[edge.target]) {
      return;
    }

    const linkAttrs = {
      ".connection": { stroke: "grey", "stroke-width": 3 },
      ".marker-target": { fill: "grey", stroke: "grey", "stroke-width": 2 },
    };
    const labels: Array<Record<string, unknown>> = [];

    if (edge.frequency !== undefined && edge.frequency !== null) {
      labels.push({
        position: 0.5,
        attrs: {
          text: { text: edge.frequency.toString(), fill: "black", "font-size": 12 },
          rect: { fill: "white", stroke: "none" },
        },
      });
    }

    const link = new pn.Link({
      source: { id: elements[edge.source].id, selector: ".root" },
      target: { id: elements[edge.target].id, selector: ".root" },
      attrs: linkAttrs,
      labels,
    });

    const frequency = Number(edge.frequency);
    if (!Number.isNaN(frequency)) {
      if (frequency > 0) {
        link.attr(".connection", { stroke: "#000f80" });
        if (processTreeFlag === 1) {
          link.attr(".marker-target", { fill: "none", stroke: "none" });
        } else {
          link.attr(".marker-target", { fill: "#000f80", stroke: "#000f80" });
        }
        tbFlag = 1;
      } else if (frequency === 0 || frequency < -1) {
        link.attr(".connection", { stroke: "#000f80" });
        link.attr(".marker-target", { fill: "none", stroke: "none" });
        link.label(0, { attrs: { text: { text: "" } } });
        tbFlag = 1;
      } else if (frequency === -1) {
        link.attr(".connection", { stroke: "#000f80" });
        link.attr(".marker-target", { fill: "none", stroke: "none" });
        link.label(0, { attrs: { text: { text: "do" } } });
        tbFlag = 1;
      }
    }

    if (edge.type === "SureEdge" || edge.type === "HybridDirectedSureGraphEdge") {
      link.attr(".connection", { stroke: "#000f80" });
      link.attr(".marker-target", { fill: "#000f80", stroke: "#000f80" });
    } else if (edge.type === "UncertainEdge" || edge.type === "HybridDirectedUncertainGraphEdge") {
      link.attr(".connection", { stroke: "red", "stroke-dasharray": "4,2" });
      link.attr(".marker-target", { fill: "red", stroke: "red" });
    } else if (edge.type === "LongDepEdge" || edge.type === "HybridDirectedLongDepGraphEdge") {
      link.attr(".connection", { stroke: "orange" });
      link.attr(".marker-target", { fill: "orange", stroke: "orange" });
    }

    linkElements.push(link);
  });

  graph.addCells(nodeElements);
  graph.addCells(linkElements);
  applyAutoLayout();

  paper.unfreeze();
  const clearSelection = addMultiSelection(paper);
  addElementTooltips(paper, nodeElements);
  adjustPaperSize(graph, paper);
  initialGraphState = graph.toJSON();
  fitGraphToViewport();

  addZoomListeners();
  return paper;

  function addZoomListeners() {
    document.getElementById("zoom-in")?.addEventListener("click", () => {
      zoom(zoomLevel + 0.2);
    });

    document.getElementById("zoom-out")?.addEventListener("click", () => {
      zoom(zoomLevel - 0.2);
    });

    document.getElementById("zoom-to-fit")?.addEventListener("click", () => {
      fitGraphToViewport();
    });

    document.getElementById("reset-button")?.addEventListener("click", () => {
      clearSelection();
      graph.clear();
      graph.fromJSON(joint.util.cloneDeep(initialGraphState));
      addElementTooltips(paper, graph.getElements());
      fitGraphToViewport();
    });

    let wheelTimer: number | undefined;

    paper.el.addEventListener("wheel", (event: WheelEvent) => {
      event.preventDefault();
      const delta = event.deltaY;
      zoom(zoomLevel + (delta > 0 ? -0.2 : 0.2));

      if (wheelTimer) {
        window.clearTimeout(wheelTimer);
      }
      wheelTimer = window.setTimeout(() => {}, 120);
    });

    paper.on("element:pointerup link:pointerup", () => {
      expandPaperToContent(paper);
    });

    document.getElementById("download-svg")?.addEventListener("click", () => {
      const serializedSVG = createSVG(paper);
      const blob = new Blob([serializedSVG], { type: "image/svg+xml" });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "graph.svg";
      anchor.click();
      window.setTimeout(() => {
        URL.revokeObjectURL(url);
      }, 60_000);
    });

  }

  function fitGraphToViewport() {
    const graphContainer = document.getElementById("graphContainer");

    if (!graphContainer) {
      return;
    }

    const containerWidth = graphContainer.getBoundingClientRect().width - 3 * PADDING_INSIDE_PAPER;
    const containerHeight = graphContainer.getBoundingClientRect().height - 3 * PADDING_INSIDE_PAPER;
    const bbox = graph.getBBox(graph.getElements());
	const paperWidth = bbox.width;
	const paperHeight = bbox.height;

    if (paperWidth <= 0 || paperHeight <= 0) {
      zoom(1);
      return;
    }

    const scaleX = containerWidth / paperWidth;
    const scaleY = containerHeight / paperHeight;
    const nextZoomLevel = Math.min(scaleX, scaleY, MAX_AUTO_FIT_ZOOM_LEVEL);

    zoom(nextZoomLevel);
    centerGraphInViewport(graphContainer);
  }

  function centerGraphInViewport(graphContainer: HTMLElement) {
    const bbox = graph.getBBox(graph.getElements());
    const scale = paper.scale();
    const viewportWidth = graphContainer.clientWidth;
    const viewportHeight = graphContainer.clientHeight;

    paper.translate(
      (viewportWidth - bbox.width * scale.sx) / 2 - bbox.x * scale.sx,
      (viewportHeight - bbox.height * scale.sy) / 2 - bbox.y * scale.sy,
    );
  }
  
  
  
  
  const graphContainer = document.getElementById("graphContainer");

      if (!graphContainer) {
        return;
      }

      const containerWidth = graphContainer.getBoundingClientRect().width - 3 * PADDING_INSIDE_PAPER;
      const containerHeight = graphContainer.getBoundingClientRect().height - 3 * PADDING_INSIDE_PAPER;
      const paperWidth = paper.getContentBBox().width;
      const paperHeight = paper.getContentBBox().height;

      const scaleX = containerWidth / paperWidth;
      const scaleY = containerHeight / paperHeight;

      zoom(zoomLevel * Math.min(scaleX, scaleY));
  
  
  
  
  

  function applyAutoLayout() {
    const layoutGraph = new dagre.graphlib.Graph();

    if (tbFlag === 0) {
      layoutGraph.setGraph({
        rankdir: "LR",
        marginx: 20,
        marginy: 20,
      });
    } else {
      layoutGraph.setGraph({
        rankdir: "TB",
        marginx: 20,
        marginy: 20,
      });
    }

    layoutGraph.setDefaultEdgeLabel(() => ({}));

    nodes.forEach((node) => {
      layoutGraph.setNode(node.id, {
        width: node.width,
        height: node.height,
        label: node.label,
      });
    });

    edges.forEach((edge) => {
      layoutGraph.setEdge(edge.source, edge.target, {
        label: edge.label,
        width: 0,
        height: 0,
      });
    });

    if (processTreeFlag === 1) {
      dagre.layout(layoutGraph, { disableOrder: true });
    } else {
      dagre.layout(layoutGraph);
    }

    layoutGraph.nodes().forEach((nodeId: string) => {
      const layoutNode = layoutGraph.node(nodeId);
      elements[nodeId].position(
        layoutNode.x - layoutNode.width / 2,
        layoutNode.y - layoutNode.height / 2,
      );
      elements[nodeId].resize(layoutNode.width, layoutNode.height);
    });

    layoutGraph.edges().forEach((edgeRef: { v: string; w: string }) => {
      const layoutEdge = layoutGraph.edge(edgeRef);
      const sourceElement = elements[edgeRef.v];
      const targetElement = elements[edgeRef.w];

      if (!sourceElement || !targetElement) {
        return;
      }

      const links = graph
        .getConnectedLinks(sourceElement, { outbound: true })
        .filter((link: any) => link.getTargetElement() === targetElement);

      if (!links.length) {
        return;
      }

      const link = links[0];
      const waypoints = layoutEdge.points.map((point: { x: number; y: number }) => ({
        x: point.x,
        y: point.y,
      }));
      const simplifiedWaypoints = simplifyWaypoints(waypoints);
      link.vertices(simplifiedWaypoints);
    });

    zoom(1);
  }
}

function addMultiSelection(paper: any) {
  const selectionHighlighterId = "graph-selection";
  const arrowheadHighlighterId = "graph-selection-arrowhead";
  const selectedCells = new Set<any>();
  let drag: GraphDrag | null = null;
  let marquee: MarqueeSelection | null = null;

  const setSelected = (model: any, selected: boolean) => {
    if (selected) {
      selectedCells.add(model);
    } else {
      selectedCells.delete(model);
    }

    const view = paper.findViewByModel(model);
    if (!view) {
      return;
    }

    if (selected) {
      joint.highlighters.stroke.add(
        view,
        model.isLink() ? ".connection" : ".root",
        selectionHighlighterId,
        {
          padding: model.isLink() ? 0 : 6,
          attrs: {
            stroke: "#0b74de",
            "stroke-width": 2,
            "stroke-linecap": "round",
            "stroke-linejoin": "round",
          },
        },
      );

      if (model.isLink()) {
        joint.highlighters.stroke.add(
          view,
          ".marker-target",
          arrowheadHighlighterId,
          {
            padding: 2,
            attrs: {
              stroke: "#0b74de",
              "stroke-width": 2,
              "stroke-linecap": "round",
              "stroke-linejoin": "round",
            },
          },
        );
      }
    } else {
      joint.highlighters.stroke.remove(view, selectionHighlighterId);
      joint.highlighters.stroke.remove(view, arrowheadHighlighterId);
    }
  };

  const clearSelection = () => {
    selectedCells.forEach((model) => setSelected(model, false));
    selectedCells.clear();
    drag = null;
  };

  const modifierPressed = (event: MouseEvent) =>
    event.ctrlKey || event.metaKey || event.shiftKey;

  const startDrag = (view: any, event: MouseEvent, x: number, y: number) => {
    const model = view.model;

    if (modifierPressed(event)) {
      setSelected(model, !selectedCells.has(model));
    } else if (!selectedCells.has(model)) {
      clearSelection();
      setSelected(model, true);
    }

    drag = selectedCells.has(model) ? { model, x, y } : null;
  };

  const moveSelection = (view: any, _event: MouseEvent, x: number, y: number) => {
    if (!drag || view.model !== drag.model) {
      return;
    }

    const dx = x - drag.x;
    const dy = y - drag.y;
    drag.x = x;
    drag.y = y;

    if (dx === 0 && dy === 0) {
      return;
    }

    // JointJS moves the cell under the pointer. Move every other selected
    // cell by the same delta so nodes and link vertices remain a single group.
    selectedCells.forEach((model) => {
      if (model !== drag?.model) {
        model.translate(dx, dy, { ui: true });
      }
    });
  };

  paper.on("element:pointerdown link:pointerdown", startDrag);
  paper.on("element:pointermove link:pointermove", moveSelection);
  paper.on("element:pointerup link:pointerup", () => {
    drag = null;
  });

  paper.on("blank:pointerdown", (event: MouseEvent, x: number, y: number) => {
    if (event.shiftKey) {
      const element = document.createElementNS("http://www.w3.org/2000/svg", "rect");
      element.classList.add("graph-selection-marquee");
      element.setAttribute("x", String(x));
      element.setAttribute("y", String(y));
      element.setAttribute("width", "0");
      element.setAttribute("height", "0");
      paper.viewport.appendChild(element);
      marquee = { element, startX: x, startY: y };
      return;
    }

    if (!modifierPressed(event)) {
      clearSelection();
    }
  });

  paper.on("blank:pointermove", (_event: MouseEvent, x: number, y: number) => {
    if (!marquee) {
      return;
    }

    const area = rectangleBetween(marquee.startX, marquee.startY, x, y);
    marquee.element.setAttribute("x", String(area.x));
    marquee.element.setAttribute("y", String(area.y));
    marquee.element.setAttribute("width", String(area.width));
    marquee.element.setAttribute("height", String(area.height));
  });

  paper.on("blank:pointerup", (_event: MouseEvent, x: number, y: number) => {
    if (!marquee) {
      return;
    }

    const area = new joint.g.Rect(
      rectangleBetween(marquee.startX, marquee.startY, x, y),
    );
    marquee.element.remove();
    marquee = null;

    if (area.width === 0 || area.height === 0) {
      return;
    }

    paper.model.getCells().forEach((model: any) => {
      const view = paper.findViewByModel(model);
      if (!view) {
        return;
      }

      const bounds = model.isLink()
        ? view.getConnection().bbox()
        : model.getBBox({ rotate: true });
      if (area.containsRect(bounds)) {
        setSelected(model, true);
      }
    });
  });

  return clearSelection;
}

function rectangleBetween(startX: number, startY: number, endX: number, endY: number) {
  return {
    x: Math.min(startX, endX),
    y: Math.min(startY, endY),
    width: Math.abs(endX - startX),
    height: Math.abs(endY - startY),
  };
}

function addElementTooltips(paper: any, elements: any[]) {
  elements.forEach((element) => {
    const tooltip = element.get?.("tooltip");
    if (!tooltip) {
      return;
    }

    const view = paper.findViewByModel(element);
    const viewElement = view?.el as SVGElement | undefined;
    if (!viewElement) {
      return;
    }

    Array.from(viewElement.children).forEach((child) => {
      if (child.localName === "title") {
        child.remove();
      }
    });

    const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
    title.textContent = tooltip;
    viewElement.insertBefore(title, viewElement.firstChild);
  });
}

function nodeTooltip(node: GraphNode) {
  if ((node.type ?? "").toLowerCase() === "operator") {
    return operatorTooltip(node.label);
  }
  return "";
}

function operatorTooltip(label: string | undefined) {
  if (label === "xlp") {
    return "Loop operator";
  }
  if (label === "xor") {
    return "Exclusive choice operator";
  }
  if (label === "and") {
    return "Parallel operator";
  }
  if (label === "seq") {
    return "Sequence operator";
  }
  return "";
}

function operatorSymbol(label: string | undefined) {
  if (label === "xlp") {
    return "\u2b6f";
  }
  if (label === "xor") {
    return "\u2716";
  }
  if (label === "and") {
    return "\u271A";
  }
  if (label === "seq") {
    return "\u279c";
  }
  return label || "";
}

export function createSVG(paper: any) {
  const svgElement = paper.svg.cloneNode(true) as SVGElement;
  svgElement.setAttribute("xmlns", "http://www.w3.org/2000/svg");

  const bbox = paper.getContentBBox();
  const padding = 30;
  const widthWithPadding = bbox.width + 2 * padding;
  const heightWithPadding = bbox.height + 2 * padding;

  svgElement.setAttribute("width", String(widthWithPadding));
  svgElement.setAttribute("height", String(heightWithPadding));
  svgElement.setAttribute(
    "viewBox",
    `${bbox.x - padding} ${bbox.y - padding} ${widthWithPadding} ${heightWithPadding}`,
  );

  const cssStyle = document.createElement("style");
  cssStyle.setAttribute("type", "text/css");
  cssStyle.textContent = normalizedGraphExportCss;
  svgElement.prepend(cssStyle);

  return new XMLSerializer().serializeToString(svgElement);
}
