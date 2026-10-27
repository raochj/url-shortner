import { Component, Input, OnChanges, SimpleChanges } from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  DagVisualization,
  OrchestrationRun,
  TaskStatus,
  TaskStage,
  DagNode,
} from "../../../core/models/orchestration.model";
interface LayoutNode {
  id: string;
  name: string;
  stage: TaskStage;
  status: TaskStatus;
  x: number;
  y: number;
  requiresApproval: boolean;
}
interface LayoutEdge {
  source: string;
  target: string;
  path: string;
  sourceX: number;
  sourceY: number;
  targetX: number;
  targetY: number;
}
interface StageColumn {
  name: string;
  x: number;
}
const STAGE_ORDER: TaskStage[] = [
  "REQUIREMENTS",
  "DESIGN",
  "IMPLEMENTATION",
  "TESTING",
  "DOCUMENTATION",
  "RELEASE",
];
const NODE_WIDTH = 140;
const NODE_HEIGHT = 50;
const STAGE_COL_WIDTH = 180;
const NODE_VERTICAL_GAP = 72;
const PADDING_TOP = 50;
const PADDING_LEFT = 20;
@Component({
  selector: "app-dag-viewer",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./dag-viewer.component.html",
  styles: [
    `
      :host {
        display: block;
      }
      .dag-container {
        background: white;
        border-radius: 10px;
        border: 1px solid var(--color-border);
        overflow: hidden;
      }
      .dag-header {
        padding: 1rem 1.25rem;
        border-bottom: 1px solid var(--color-border);
        background: var(--color-navy);
        display: flex;
        align-items: center;
        justify-content: space-between;
        h3 {
          color: white;
          font-size: 1rem;
          font-weight: 700;
          margin: 0;
        }
      }
      .dag-scroll {
        overflow-x: auto;
        overflow-y: auto;
        max-height: 480px;
        background: #f8fafc;
      }
      .dag-svg {
        display: block;
      }
      .stage-label-text {
        font-size: 11px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        fill: var(--color-text-light);
      }
      .node-id-text {
        font-size: 10px;
        font-weight: 700;
        font-family: "SFMono-Regular", Consolas, monospace;
      }
      .node-name-text {
        font-size: 10px;
        font-weight: 500;
      }
      .approval-badge-text {
        font-size: 10px;
        font-weight: 800;
        fill: white;
      }
      .clickable {
        cursor: pointer;
        &:hover rect {
          filter: brightness(0.95);
        }
      }
      /* Stage progress bar */
      .stage-progress {
        display: flex;
        gap: 0.5rem;
        padding: 0.75rem 1.25rem;
        border-top: 1px solid var(--color-border);
        background: white;
        flex-wrap: wrap;
      }
      .stage-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.25rem 0.625rem;
        background: rgba(0, 48, 87, 0.06);
        border-radius: 100px;
        font-size: 0.75rem;
        .stage-name {
          font-weight: 700;
          color: var(--color-navy);
          text-transform: uppercase;
          letter-spacing: 0.04em;
        }
        .stage-count {
          color: var(--color-text-muted);
          font-weight: 600;
        }
        &.all-done {
          background: rgba(46, 125, 50, 0.1);
          .stage-name {
            color: var(--color-success);
          }
        }
      }
      .empty-dag {
        text-align: center;
        padding: 3rem;
        color: var(--color-text-muted);
        font-size: 0.9375rem;
      }
      .dag-legend {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        padding: 0.75rem 1.25rem;
        border-top: 1px solid var(--color-border);
        background: white;
      }
      .legend-item {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.75rem;
        color: var(--color-text-muted);
        font-weight: 500;
      }
      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 3px;
      }
    `,
  ],
})
export class DagViewerComponent implements OnChanges {
  @Input() dagData: DagVisualization | null = null;
  @Input() runState: OrchestrationRun | null = null;
  layoutNodes: LayoutNode[] = [];
  layoutEdges: LayoutEdge[] = [];
  stageColumns: StageColumn[] = [];
  svgWidth = 600;
  svgHeight = 300;
  tasksByStage: Record<string, LayoutNode[]> = {};
  ngOnChanges(changes: SimpleChanges): void {
    if (changes["dagData"] || changes["runState"]) {
      this.computeLayout();
    }
  }
  computeLayout(): void {
    if (!this.dagData && !this.runState) return;
    const nodes: DagNode[] =
      this.dagData?.nodes ?? this.buildNodesFromRunState();
    const edges = this.dagData?.edges ?? this.buildEdgesFromRunState();
    if (nodes.length === 0) return;
    // Group by stage
    const stageMap = new Map<string, DagNode[]>();
    for (const node of nodes) {
      if (!stageMap.has(node.stage)) stageMap.set(node.stage, []);
      stageMap.get(node.stage)!.push(node);
    }
    // Sort stages by STAGE_ORDER
    const presentStages = STAGE_ORDER.filter((s) => stageMap.has(s));
    // Also include any stages not in STAGE_ORDER
    const extraStages = Array.from(stageMap.keys()).filter(
      (s) => !STAGE_ORDER.includes(s as TaskStage),
    );
    const orderedStages = [...presentStages, ...extraStages];
    // Compute column x positions
    this.stageColumns = orderedStages.map((stage, i) => ({
      name: stage,
      x: PADDING_LEFT + i * STAGE_COL_WIDTH + NODE_WIDTH / 2,
    }));
    // Assign x,y to nodes
    this.layoutNodes = [];
    this.tasksByStage = {};
    for (const stage of orderedStages) {
      const stageNodes = stageMap.get(stage) ?? [];
      const stageIdx = orderedStages.indexOf(stage);
      const centerX =
        PADDING_LEFT + stageIdx * STAGE_COL_WIDTH + NODE_WIDTH / 2;
      const layoutForStage: LayoutNode[] = stageNodes.map((node, i) => ({
        id: node.id,
        name: node.name,
        stage: node.stage,
        status: node.status,
        requiresApproval: node.requiresApproval,
        x: centerX,
        y: PADDING_TOP + i * NODE_VERTICAL_GAP + NODE_HEIGHT / 2,
      }));
      this.layoutNodes.push(...layoutForStage);
      this.tasksByStage[stage] = layoutForStage;
    }
    // Compute SVG dimensions
    const maxY =
      this.layoutNodes.length > 0
        ? Math.max(...this.layoutNodes.map((n) => n.y)) + NODE_HEIGHT / 2 + 20
        : 200;
    this.svgWidth = PADDING_LEFT + orderedStages.length * STAGE_COL_WIDTH + 20;
    this.svgHeight = Math.max(maxY, 200);
    // Build layout edges
    const nodeMap = new Map(this.layoutNodes.map((n) => [n.id, n]));
    this.layoutEdges = (edges ?? [])
      .map((edge) => {
        const src = nodeMap.get(edge.source);
        const tgt = nodeMap.get(edge.target);
        if (!src || !tgt) return null;
        const x1 = src.x + NODE_WIDTH / 2;
        const y1 = src.y;
        const x2 = tgt.x - NODE_WIDTH / 2;
        const y2 = tgt.y;
        return {
          source: edge.source,
          target: edge.target,
          path: this.getCubicBezierPath(x1, y1, x2, y2),
          sourceX: x1,
          sourceY: y1,
          targetX: x2,
          targetY: y2,
        };
      })
      .filter((e): e is LayoutEdge => e !== null);
  }
  private buildNodesFromRunState(): DagNode[] {
    if (!this.runState?.tasks) return [];
    return this.runState.tasks.map((task) => ({
      id: task.taskId,
      name: task.name,
      stage: task.stage,
      status: task.status,
      x: 0,
      y: 0,
      requiresApproval: task.requiresApproval,
    }));
  }
  private buildEdgesFromRunState(): Array<{ source: string; target: string }> {
    if (!this.runState?.tasks) return [];
    const edges: Array<{ source: string; target: string }> = [];
    for (const task of this.runState.tasks) {
      for (const dep of task.dependencies ?? []) {
        edges.push({ source: dep, target: task.taskId });
      }
    }
    return edges;
  }
  getCubicBezierPath(x1: number, y1: number, x2: number, y2: number): string {
    const midX = (x1 + x2) / 2;
    return `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`;
  }
  getNodeColor(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      FALLBACK_COMPLETED: "#2E7D32",
      STALE: "#FFF8E1",
      COMPLETED: "#003057",
      IN_PROGRESS: "#E1F5FE",
      AWAITING_APPROVAL: "#FFF3E0",
      APPROVED: "#E3F2FD",
      FAILED: "#FFEBEE",
      PENDING: "#F5F5F5",
      ROLLED_BACK: "#EDE7F6",
      SKIPPED: "#FAFAFA",
    };
    return map[status] ?? "#F5F5F5";
  }
  getNodeBorderColor(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      FALLBACK_COMPLETED: "#1B5E20",
      STALE: "#F9A825",
      COMPLETED: "#001e3c",
      IN_PROGRESS: "#0277BD",
      AWAITING_APPROVAL: "#E65100",
      APPROVED: "#1565C0",
      FAILED: "#C62828",
      PENDING: "#CBD5E0",
      ROLLED_BACK: "#6A1B9A",
      SKIPPED: "#E2E8F0",
    };
    return map[status] ?? "#CBD5E0";
  }
  getNodeTextColor(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      FALLBACK_COMPLETED: "#FFFFFF",
      STALE: "#8D6E00",
      COMPLETED: "#FFFFFF",
      IN_PROGRESS: "#01579B",
      AWAITING_APPROVAL: "#E65100",
      APPROVED: "#1565C0",
      FAILED: "#C62828",
      PENDING: "#718096",
      ROLLED_BACK: "#6A1B9A",
      SKIPPED: "#9E9E9E",
    };
    return map[status] ?? "#4A5568";
  }
  getStatusIcon(status: TaskStatus): string {
    const map: Record<TaskStatus, string> = {
      FALLBACK_COMPLETED: "F",
      STALE: "!",
      COMPLETED: "✓",
      IN_PROGRESS: "⟳",
      AWAITING_APPROVAL: "⏸",
      APPROVED: "→",
      FAILED: "✗",
      PENDING: "○",
      ROLLED_BACK: "↩",
      SKIPPED: "⤳",
    };
    return map[status] ?? "○";
  }
  getCompletedCountForStage(stage: string): number {
    const nodes = this.tasksByStage[stage] ?? [];
    return nodes.filter((n) => n.status === "COMPLETED").length;
  }
  isStageAllDone(stage: string): boolean {
    const nodes = this.tasksByStage[stage] ?? [];
    return nodes.length > 0 && nodes.every((n) => n.status === "COMPLETED");
  }
  get stageProgressKeys(): string[] {
    return Object.keys(this.tasksByStage);
  }
  readonly legendItems = [
    { color: "#003057", label: "Completed" },
    { color: "#B3E5FC", label: "In Progress" },
    { color: "#FFE0B2", label: "Awaiting Approval" },
    { color: "#FFEBEE", label: "Failed" },
    { color: "#F5F5F5", label: "Pending" },
    { color: "#EDE7F6", label: "Rolled Back" },
  ];
}
