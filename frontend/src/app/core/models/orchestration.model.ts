export type TaskStatus =
  | "PENDING"
  | "IN_PROGRESS"
  | "AWAITING_APPROVAL"
  | "APPROVED"
  | "COMPLETED"
  | "FAILED"
  | "ROLLED_BACK"
  | "SKIPPED";
export type TaskStage =
  | "REQUIREMENTS"
  | "DESIGN"
  | "IMPLEMENTATION"
  | "TESTING"
  | "DOCUMENTATION"
  | "RELEASE";
export type ScenarioType = "GREENFIELD" | "BROWNFIELD" | "AMBIGUOUS";
export interface TaskNode {
  taskId: string;
  name: string;
  description: string;
  stage: TaskStage;
  status: TaskStatus;
  attemptNumber: number;
  latencyMs?: number;
  dependencies: string[];
  errorDetail?: string;
  requiresApproval: boolean;
  approvalPrompt?: string;
}
export interface ReliabilityMetrics {
  runId: string;
  totalTasks: number;
  completedTasks: number;
  failedTasks: number;
  rolledBackTasks: number;
  retryCount: number;
  successRate: number;
  rollbackRate: number;
  totalElapsedMs: number;
  meanTimeToRecoverMs: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  approvalsPending: number;
  approvalsGranted: number;
}
export interface OrchestrationRun {
  runId: string;
  scenarioType: ScenarioType;
  status: string;
  tasks: TaskNode[];
  parameters: Record<string, unknown>;
  startedAt?: string;
  completedAt?: string;
  metrics?: ReliabilityMetrics;
}
export interface DagVisualization {
  nodes: DagNode[];
  edges: DagEdge[];
}
export interface DagNode {
  id: string;
  name: string;
  stage: TaskStage;
  status: TaskStatus;
  x: number;
  y: number;
  requiresApproval: boolean;
}
export interface DagEdge {
  source: string;
  target: string;
}
export interface ScenarioInfo {
  type: ScenarioType;
  title: string;
  description: string;
  taskCount: number;
  features: string[];
}
export interface ApprovalRequest {
  runId: string;
  taskId: string;
  approved: boolean;
  comment: string;
}
export interface StartRunRequest {
  scenarioType: ScenarioType;
  parameters: Record<string, unknown>;
}
export interface StartRunResponse {
  runId: string;
}
