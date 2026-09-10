import { JsonDataService } from "@knime/ui-extension-service";
import "./style.css";

type Move = { label: string; type: string };
type RepresentedCase = { index: number; name: string };
type Alignment = {
  index: number;
  reliable: boolean;
  cases: RepresentedCase[];
  caseCount: number;
  moves: Move[];
  moveCounts: Record<string, number>;
  metrics: Record<string, unknown>;
};
type AlignmentData = {
  alignments: Alignment[];
  alignmentCount: number;
  caseCount: number;
  reliableCaseCount: number;
  metrics: Record<string, unknown>;
};

const MOVE_META: Record<string, { label: string; className: string }> = {
  LMGOOD: { label: "Synchronous", className: "sync" },
  L: { label: "Log move", className: "log" },
  MREAL: { label: "Model move", className: "model" },
  MINVI: { label: "Invisible model move", className: "invisible" },
  LMNOGOOD: { label: "Violation", className: "violation" },
  LMREPLACED: { label: "Replaced", className: "replaced" },
  LMSWAPPED: { label: "Swapped", className: "swapped" },
};

const PAGE_SIZE = 50;
let data: AlignmentData;
let visibleCount = PAGE_SIZE;
let query = "";
let reliability = "all";
let sort = "cases-desc";
const enabledMoveTypes = new Set(Object.keys(MOVE_META));

const app = document.getElementById("app")!;

function escapeHtml(value: unknown): string {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function formatMetric(value: unknown): string {
  if (typeof value === "number") {
    return Number.isInteger(value) ? String(value) : value.toLocaleString(undefined, { maximumFractionDigits: 3 });
  }
  return String(value ?? "-");
}

function filteredAlignments(): Alignment[] {
  const normalized = query.trim().toLocaleLowerCase();
  const result = data.alignments.filter((alignment) => {
    if (reliability === "reliable" && !alignment.reliable) return false;
    if (reliability === "unreliable" && alignment.reliable) return false;
    if (normalized && !alignment.cases.some((item) => item.name.toLocaleLowerCase().includes(normalized))) return false;
    return alignment.moves.some((move) => enabledMoveTypes.has(move.type));
  });

  return result.sort((a, b) => {
    if (sort === "cases-asc") return a.caseCount - b.caseCount;
    if (sort === "moves-desc") return b.moves.length - a.moves.length;
    if (sort === "moves-asc") return a.moves.length - b.moves.length;
    return b.caseCount - a.caseCount;
  });
}

function renderShell(): void {
  const reliablePct = data.caseCount ? Math.round((data.reliableCaseCount / data.caseCount) * 100) : 0;
  app.innerHTML = `
    <header class="topbar">
      <div><h1>Alignment Viewer</h1><p>Log-model replay result</p></div>
      <div class="summary" aria-label="Alignment summary">
        <div><strong>${data.alignmentCount.toLocaleString()}</strong><span>Alignments</span></div>
        <div><strong>${data.caseCount.toLocaleString()}</strong><span>Cases</span></div>
        <div><strong>${reliablePct}%</strong><span>Reliable cases</span></div>
      </div>
    </header>
    <section class="controls">
      <label class="search"><span>Search cases</span><input id="search" type="search" placeholder="Case name" /></label>
      <label><span>Reliability</span><select id="reliability"><option value="all">All results</option><option value="reliable">Reliable only</option><option value="unreliable">Unreliable only</option></select></label>
      <label><span>Order</span><select id="sort"><option value="cases-desc">Most cases</option><option value="cases-asc">Fewest cases</option><option value="moves-desc">Longest alignment</option><option value="moves-asc">Shortest alignment</option></select></label>
    </section>
    <section id="legend" class="legend" aria-label="Move type filters"></section>
    <details class="metrics"><summary>Replay statistics</summary><div id="metrics-grid"></div></details>
    <main><div id="result-status" class="result-status"></div><div id="alignments" class="alignment-list"></div><button id="load-more" class="load-more" type="button">Load more</button></main>
  `;

  renderLegend();
  renderMetrics();
  document.getElementById("search")!.addEventListener("input", (event) => {
    query = (event.target as HTMLInputElement).value;
    resetAndRender();
  });
  document.getElementById("reliability")!.addEventListener("change", (event) => {
    reliability = (event.target as HTMLSelectElement).value;
    resetAndRender();
  });
  document.getElementById("sort")!.addEventListener("change", (event) => {
    sort = (event.target as HTMLSelectElement).value;
    resetAndRender();
  });
  document.getElementById("load-more")!.addEventListener("click", () => {
    visibleCount += PAGE_SIZE;
    renderAlignments();
  });
  renderAlignments();
}

function renderLegend(): void {
  const presentTypes = new Set(data.alignments.flatMap((alignment) => alignment.moves.map((move) => move.type)));
  const legend = document.getElementById("legend")!;
  legend.innerHTML = Object.entries(MOVE_META)
    .filter(([type]) => presentTypes.has(type))
    .map(([type, meta]) => `<label class="legend-item"><input type="checkbox" data-type="${type}" checked><i class="${meta.className}"></i><span>${meta.label}</span></label>`)
    .join("");
  legend.querySelectorAll<HTMLInputElement>("input").forEach((input) => input.addEventListener("change", () => {
    if (input.checked) enabledMoveTypes.add(input.dataset.type!);
    else enabledMoveTypes.delete(input.dataset.type!);
    resetAndRender();
  }));
}

function renderMetrics(): void {
  const metrics = Object.entries(data.metrics);
  document.getElementById("metrics-grid")!.innerHTML = metrics.length
    ? metrics.map(([key, value]) => `<div><span>${escapeHtml(key)}</span><strong>${escapeHtml(formatMetric(value))}</strong></div>`).join("")
    : `<p>No aggregate statistics available.</p>`;
}

function resetAndRender(): void {
  visibleCount = PAGE_SIZE;
  renderAlignments();
}

function renderAlignments(): void {
  const filtered = filteredAlignments();
  const shown = filtered.slice(0, visibleCount);
  document.getElementById("result-status")!.textContent = `Showing ${shown.length.toLocaleString()} of ${filtered.length.toLocaleString()} alignments`;
  document.getElementById("alignments")!.innerHTML = shown.length
    ? shown.map(renderAlignment).join("")
    : `<div class="empty">No alignments match the current filters.</div>`;
  const loadMore = document.getElementById("load-more") as HTMLButtonElement;
  loadMore.hidden = shown.length >= filtered.length;
}

function renderAlignment(alignment: Alignment, position: number): string {
  const caseNames = alignment.cases.map((item) => item.name);
  const casePreview = caseNames.slice(0, 3).map(escapeHtml).join(", ");
  const remaining = Math.max(0, caseNames.length - 3);
  const metricEntries = Object.entries(alignment.metrics).slice(0, 4);
  const moves = alignment.moves.map((move) => {
    const meta = MOVE_META[move.type] ?? { label: move.type, className: "unknown" };
    const isVisible = enabledMoveTypes.has(move.type);
    return `<div class="move ${meta.className}${isVisible ? "" : " muted"}" title="${escapeHtml(meta.label)}: ${escapeHtml(move.label)}"><span>${escapeHtml(move.label)}</span><small>${escapeHtml(meta.label)}</small></div>`;
  }).join("");

  return `<article class="alignment-card">
    <div class="alignment-head">
      <div class="rank"><strong>#${position + 1}</strong><span>Alignment</span></div>
      <div class="case-info"><strong>${alignment.caseCount.toLocaleString()} ${alignment.caseCount === 1 ? "case" : "cases"}</strong><span title="${escapeHtml(caseNames.join(", "))}">${casePreview}${remaining ? ` +${remaining}` : ""}</span></div>
      <span class="reliability ${alignment.reliable ? "is-reliable" : "is-unreliable"}">${alignment.reliable ? "Reliable" : "Unreliable"}</span>
      <div class="card-metrics">${metricEntries.map(([key, value]) => `<span title="${escapeHtml(key)}"><b>${escapeHtml(key)}</b> ${escapeHtml(formatMetric(value))}</span>`).join("")}</div>
    </div>
    <div class="moves" tabindex="0" aria-label="Alignment move sequence">${moves}</div>
  </article>`;
}

async function execute(): Promise<void> {
  try {
    const service = await JsonDataService.getInstance();
    const initial = await service.initialData() as { alignment?: AlignmentData } | null;
    if (!initial?.alignment) throw new Error("No alignment data received");
    data = initial.alignment;
    renderShell();
  } catch (error) {
    app.innerHTML = `<div class="error"><strong>Alignment view could not be loaded.</strong><span>${escapeHtml(error)}</span></div>`;
  }
}

execute();
