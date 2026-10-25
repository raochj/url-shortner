import { Component, OnInit, OnDestroy } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterModule } from "@angular/router";
import { Subscription } from "rxjs";
import {
  ScenarioInfo,
  OrchestrationRun,
  ScenarioType,
  TaskNode,
  TaskStatus,
} from "../../../core/models/orchestration.model";
import { OrchestrationService } from "../../../core/services/orchestration.service";
import { AuthService } from "../../../core/services/auth.service";
import { HeaderComponent } from "../../../shared/components/header/header.component";
import { SidebarComponent } from "../../../shared/components/sidebar/sidebar.component";
import { DagViewerComponent } from "../dag-viewer/dag-viewer.component";
import { MetricsPanelComponent } from "../metrics-panel/metrics-panel.component";
@Component({
  selector: "app-orchestration-panel",
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    HeaderComponent,
    SidebarComponent,
    DagViewerComponent,
    MetricsPanelComponent,
  ],
  templateUrl: "./orchestration-panel.component.html",
  styles: [
    `
      :host {
        display: block;
      }
      .page-layout {
        display: flex;
        flex-direction: column;
        min-height: 100vh;
      }
      .body-layout {
        display: flex;
        flex: 1;
      }
      .page-body {
        flex: 1;
        padding: 2rem;
        min-width: 0;
        overflow: hidden;
      }
      .page-header {
        margin-bottom: 1.75rem;
        h1 {
          font-size: 1.75rem;
          color: var(--color-navy);
          font-weight: 700;
        }
        p {
          margin-top: 0.25rem;
          color: var(--color-text-muted);
          font-size: 0.9375rem;
        }
      }
      /* Scenario Selector */
      .scenario-section {
        margin-bottom: 2rem;
      }
      .section-title {
        font-size: 0.8125rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.07em;
        color: var(--color-text-light);
        margin-bottom: 1rem;
      }
      .scenario-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
        gap: 1.25rem;
        margin-bottom: 1.5rem;
      }
      .scenario-card {
        background: white;
        border: 2px solid var(--color-border);
        border-radius: 12px;
        padding: 1.5rem;
        cursor: pointer;
        transition: all 0.2s ease;
        position: relative;
        overflow: hidden;
        &::before {
          content: "";
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 4px;
          background: var(--color-border);
          transition: background 0.2s;
        }
        &:hover {
          border-color: var(--color-blue);
          box-shadow: var(--shadow-md);
          transform: translateY(-2px);
          &::before {
            background: var(--color-blue);
          }
        }
        &.selected {
          border-color: var(--color-navy);
          box-shadow:
            0 0 0 3px rgba(0, 48, 87, 0.12),
            var(--shadow-sm);
          &::before {
            background: var(--color-navy);
          }
        }
      }
      .scenario-icon {
        width: 48px;
        height: 48px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 1rem;
        font-size: 1.5rem;
        &.greenfield {
          background: rgba(46, 125, 50, 0.1);
        }
        &.brownfield {
          background: rgba(230, 81, 0, 0.1);
        }
        &.ambiguous {
          background: rgba(0, 48, 87, 0.08);
        }
      }
      .scenario-title {
        font-size: 1.125rem;
        font-weight: 700;
        color: var(--color-navy);
        margin-bottom: 0.375rem;
      }
      .scenario-desc {
        font-size: 0.875rem;
        color: var(--color-text-muted);
        line-height: 1.6;
        margin-bottom: 1rem;
      }
      .scenario-task-count {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.8125rem;
        font-weight: 600;
        color: var(--color-text-muted);
        margin-bottom: 0.875rem;
      }
      .scenario-features {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }
      .feature-item {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        .feature-dot {
          width: 6px;
          height: 6px;
          border-radius: 50%;
          flex-shrink: 0;
          &.greenfield {
            background: var(--color-success);
          }
          &.brownfield {
            background: var(--color-warning);
          }
          &.ambiguous {
            background: var(--color-navy);
          }
        }
      }
      .scenario-check {
        position: absolute;
        top: 1rem;
        right: 1rem;
        width: 22px;
        height: 22px;
        background: var(--color-navy);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      /* Ambiguous goal input */
      .goal-input-section {
        margin-bottom: 1.5rem;
        animation: fadeIn 0.2s ease;
      }
      @keyframes fadeIn {
        from {
          opacity: 0;
          transform: translateY(-6px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
      .goal-textarea {
        width: 100%;
        padding: 0.875rem 1rem;
        border: 1.5px solid var(--color-border-dark);
        border-radius: 8px;
        font-family: inherit;
        font-size: 0.9375rem;
        color: var(--color-text);
        resize: none;
        outline: none;
        transition: all 0.15s;
        line-height: 1.6;
        box-sizing: border-box;
        &:focus {
          border-color: var(--color-blue);
          box-shadow: 0 0 0 3px rgba(0, 160, 223, 0.12);
        }
      }
      /* Start Run Button */
      .run-controls {
        display: flex;
        align-items: center;
        gap: 1rem;
        flex-wrap: wrap;
      }
      .btn-start-run {
        display: inline-flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.75rem 2rem;
        background: var(--color-navy);
        color: white;
        border: none;
        border-radius: 8px;
        font-family: inherit;
        font-size: 1rem;
        font-weight: 700;
        cursor: pointer;
        transition: all 0.15s;
        box-shadow: var(--shadow-sm);
        &:hover:not(:disabled) {
          background: var(--color-navy-dark);
          box-shadow: var(--shadow-md);
          transform: translateY(-1px);
        }
        &:disabled {
          opacity: 0.55;
          cursor: not-allowed;
          transform: none;
        }
      }
      .run-hint {
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }
      .spinner-sm {
        width: 18px;
        height: 18px;
        border: 2px solid rgba(255, 255, 255, 0.4);
        border-top-color: white;
        border-radius: 50%;
        animation: spin 0.75s linear infinite;
        display: inline-block;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
      /* Active Run Section */
      .run-section {
        margin-top: 2rem;
        animation: slideUp 0.3s ease;
      }
      @keyframes slideUp {
        from {
          opacity: 0;
          transform: translateY(12px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
      .run-status-bar {
        background: white;
        border: 1px solid var(--color-border);
        border-radius: 10px;
        padding: 1rem 1.5rem;
        margin-bottom: 1.5rem;
        display: flex;
        align-items: center;
        gap: 1.5rem;
        flex-wrap: wrap;
        box-shadow: var(--shadow-sm);
      }
      .run-id-display {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        background: var(--color-bg);
        padding: 0.25rem 0.625rem;
        border-radius: 4px;
      }
      .run-status-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.3125rem 0.875rem;
        border-radius: 100px;
        font-size: 0.8125rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        &.RUNNING,
        &.IN_PROGRESS {
          background: var(--color-info-bg);
          color: var(--color-info);
        }
        &.COMPLETED {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.FAILED {
          background: var(--color-error-bg);
          color: var(--color-error);
        }
        &.PENDING {
          background: #f1f5f9;
          color: #64748b;
        }
        &.AWAITING_APPROVAL {
          background: var(--color-warning-bg);
          color: var(--color-warning);
        }
        &.PAUSED {
          background: var(--color-warning-bg);
          color: var(--color-warning);
        }
      }
      .run-timing {
        font-size: 0.875rem;
        color: var(--color-text-muted);
        margin-left: auto;
      }
      .run-grid {
        display: grid;
        grid-template-columns: 1fr 320px;
        gap: 1.5rem;
        margin-bottom: 1.5rem;
        @media (max-width: 1200px) {
          grid-template-columns: 1fr;
        }
      }
      /* Approval Queue */
      .approval-section {
        background: var(--color-warning-bg);
        border: 2px solid var(--color-warning);
        border-radius: 10px;
        padding: 1.25rem 1.5rem;
        margin-bottom: 1.5rem;
        animation: pulse-border 2s ease-in-out infinite;
      }
      @keyframes pulse-border {
        0%,
        100% {
          border-color: var(--color-warning);
        }
        50% {
          border-color: rgba(230, 81, 0, 0.4);
        }
      }
      .approval-header {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 1.25rem;
        h3 {
          font-size: 1rem;
          font-weight: 700;
          color: var(--color-warning);
          margin: 0;
        }
      }
      .approval-icon {
        width: 32px;
        height: 32px;
        background: var(--color-warning);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        font-weight: 800;
        font-size: 1rem;
        flex-shrink: 0;
      }
      .approval-cards {
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
      }
      .approval-task-card {
        background: white;
        border-radius: 8px;
        border: 1.5px solid rgba(230, 81, 0, 0.3);
        padding: 1rem 1.125rem;
      }
      .approval-task-name {
        font-size: 0.9375rem;
        font-weight: 700;
        color: var(--color-text);
        margin-bottom: 0.25rem;
      }
      .approval-task-prompt {
        font-size: 0.875rem;
        color: var(--color-text-muted);
        line-height: 1.5;
        margin-bottom: 1rem;
      }
      .approval-btns {
        display: flex;
        gap: 0.625rem;
      }
      .btn-approve {
        flex: 1;
        padding: 0.5rem;
        background: var(--color-success);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 700;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        transition: background 0.15s;
        &:hover {
          background: #1b5e20;
        }
      }
      .btn-reject {
        flex: 1;
        padding: 0.5rem;
        background: var(--color-error);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 700;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        transition: background 0.15s;
        &:hover {
          background: #a31e1e;
        }
      }
      /* Task Log Table */
      .task-log-section {
        margin-top: 1.5rem;
      }
      .log-card {
        background: white;
        border-radius: 10px;
        border: 1px solid var(--color-border);
        overflow: hidden;
        box-shadow: var(--shadow-sm);
      }
      .log-card-header {
        padding: 1rem 1.5rem;
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
      .log-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
        thead tr {
          background: rgba(0, 48, 87, 0.05);
        }
        th {
          text-align: left;
          padding: 0.625rem 1rem;
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--color-text-muted);
          border-bottom: 1px solid var(--color-border);
        }
        td {
          padding: 0.75rem 1rem;
          border-bottom: 1px solid var(--color-border);
          vertical-align: middle;
          &:last-child {
            border-bottom: none;
          }
        }
        tr:hover td {
          background: rgba(0, 160, 223, 0.03);
        }
        tr:last-child td {
          border-bottom: none;
        }
      }
      .task-id-mono {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        background: rgba(0, 48, 87, 0.06);
        padding: 0.125rem 0.5rem;
        border-radius: 4px;
        color: var(--color-navy);
      }
      .log-status {
        display: inline-flex;
        align-items: center;
        padding: 0.1875rem 0.5625rem;
        border-radius: 100px;
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        &.COMPLETED {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.FAILED {
          background: var(--color-error-bg);
          color: var(--color-error);
        }
        &.IN_PROGRESS {
          background: var(--color-info-bg);
          color: var(--color-info);
        }
        &.AWAITING_APPROVAL {
          background: var(--color-warning-bg);
          color: var(--color-warning);
        }
        &.PENDING {
          background: #f1f5f9;
          color: #64748b;
        }
        &.ROLLED_BACK {
          background: var(--color-purple-bg);
          color: var(--color-purple);
        }
        &.SKIPPED {
          background: #f8fafc;
          color: #94a3b8;
        }
        &.APPROVED {
          background: #e3f2fd;
          color: #1565c0;
        }
      }
      .latency-text {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        font-family: var(--font-mono);
      }
      .error-preview {
        font-size: 0.8125rem;
        color: var(--color-error);
        max-width: 200px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .error-message-display {
        background: var(--color-error-bg);
        border: 1px solid rgba(198, 40, 40, 0.2);
        border-radius: 6px;
        padding: 0.875rem 1rem;
        color: var(--color-error);
        font-size: 0.9375rem;
        margin-top: 1rem;
      }
      .polling-indicator {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.8125rem;
        color: var(--color-blue);
        font-weight: 500;
      }
      .polling-dot {
        width: 8px;
        height: 8px;
        background: var(--color-blue);
        border-radius: 50%;
        animation: pulse-dot 1s infinite;
      }
      @keyframes pulse-dot {
        0%,
        100% {
          opacity: 1;
          transform: scale(1);
        }
        50% {
          opacity: 0.5;
          transform: scale(0.8);
        }
      }
    `,
  ],
})
export class OrchestrationPanelComponent implements OnInit, OnDestroy {
  scenarios: ScenarioInfo[] = [];
  activeRunId: string | null = null;
  activeRun: OrchestrationRun | null = null;
  isLoading = false;
  isStarting = false;
  selectedScenario: ScenarioType | null = null;
  goalText = "";
  errorMessage = "";
  pollingSubscription: Subscription | null = null;
  isPolling = false;
  // Fallback scenarios when API unavailable
  readonly defaultScenarios: ScenarioInfo[] = [
    {
      type: "GREENFIELD",
      title: "Greenfield Development",
      description:
        "Build a new feature from scratch with full pipeline: requirements, design, implementation, testing, docs, and release.",
      taskCount: 12,
      features: [
        "Requirements gathering & analysis",
        "System design & architecture",
        "Full implementation pipeline",
        "Automated testing & QA",
        "Documentation generation",
        "Staged release workflow",
      ],
    },
    {
      type: "BROWNFIELD",
      title: "Brownfield Enhancement",
      description:
        "Enhance an existing system while preserving backward compatibility with dependency analysis and rollback support.",
      taskCount: 9,
      features: [
        "Codebase analysis & impact assessment",
        "Backward compatibility validation",
        "Incremental implementation",
        "Regression testing suite",
        "Rollback capability at each stage",
        "Change documentation",
      ],
    },
    {
      type: "AMBIGUOUS",
      title: "Ambiguous Scenario",
      description:
        "Describe what you want to achieve and the AI orchestrator will classify the work and build the optimal pipeline.",
      taskCount: 0,
      features: [
        "Natural language goal specification",
        "AI-powered scenario classification",
        "Dynamic pipeline construction",
        "Human-in-the-loop approval gates",
        "Adaptive retry & recovery",
        "Reliability metrics tracking",
      ],
    },
  ];
  constructor(
    private orchestrationService: OrchestrationService,
    public authService: AuthService,
  ) {}
  ngOnInit(): void {
    this.loadScenarios();
  }
  loadScenarios(): void {
    this.orchestrationService.getScenarios().subscribe({
      next: (scenarios) => {
        this.scenarios =
          scenarios && scenarios.length > 0 ? scenarios : this.defaultScenarios;
      },
      error: () => {
        this.scenarios = this.defaultScenarios;
      },
    });
  }
  get displayScenarios(): ScenarioInfo[] {
    return this.scenarios.length > 0 ? this.scenarios : this.defaultScenarios;
  }
  selectScenario(type: ScenarioType): void {
    this.selectedScenario = type;
    this.errorMessage = "";
  }
  startRun(): void {
    if (!this.selectedScenario) return;
    if (this.selectedScenario === "AMBIGUOUS" && !this.goalText.trim()) return;
    this.isStarting = true;
    this.errorMessage = "";
    const parameters: Record<string, unknown> = {};
    if (this.selectedScenario === "AMBIGUOUS") {
      parameters["goal"] = this.goalText.trim();
    }
    this.orchestrationService
      .startRun(this.selectedScenario, parameters)
      .subscribe({
        next: (resp) => {
          this.isStarting = false;
          this.activeRunId = resp.runId;
          this.startPolling(resp.runId);
        },
        error: (err) => {
          this.isStarting = false;
          this.errorMessage =
            err.error?.message || "Failed to start orchestration run.";
        },
      });
  }
  startPolling(runId: string): void {
    this.isPolling = true;
    this.stopPolling();
    this.pollingSubscription = this.orchestrationService
      .pollRunState(runId)
      .subscribe({
        next: (run) => {
          this.activeRun = run;
          // Stop polling if terminal state
          if (["COMPLETED", "FAILED", "ROLLED_BACK"].includes(run.status)) {
            this.stopPolling();
          }
        },
        error: () => {
          this.isPolling = false;
        },
      });
  }
  stopPolling(): void {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }
    this.isPolling = false;
  }
  onApprove(taskId: string): void {
    if (!this.activeRunId) return;
    this.orchestrationService
      .submitApproval(this.activeRunId, taskId, true, "Approved via UI")
      .subscribe({
        next: () => {
          // State update comes from polling
        },
      });
  }
  onReject(taskId: string): void {
    if (!this.activeRunId) return;
    this.orchestrationService
      .submitApproval(this.activeRunId, taskId, false, "Rejected via UI")
      .subscribe({
        next: () => {
          // State update comes from polling
        },
      });
  }
  getAwaitingApprovalTasks(): TaskNode[] {
    return (
      this.activeRun?.tasks.filter((t) => t.status === "AWAITING_APPROVAL") ??
      []
    );
  }
  getTasksByStatus(status: TaskStatus): TaskNode[] {
    return this.activeRun?.tasks.filter((t) => t.status === status) ?? [];
  }
  isAdmin(): boolean {
    return this.authService.isAdmin();
  }
  formatDate(dateStr?: string): string {
    if (!dateStr) return "—";
    return new Date(dateStr).toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  }
  formatLatency(ms?: number): string {
    if (!ms && ms !== 0) return "—";
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  }
  getScenarioIcon(type: ScenarioType): string {
    const icons: Record<ScenarioType, string> = {
      GREENFIELD: "🌱",
      BROWNFIELD: "🏗️",
      AMBIGUOUS: "🔮",
    };
    return icons[type] || "⚙️";
  }
  get allTasks(): TaskNode[] {
    return this.activeRun?.tasks ?? [];
  }
  get completedAndFailedTasks(): TaskNode[] {
    return this.allTasks.filter((t) =>
      ["COMPLETED", "FAILED", "ROLLED_BACK", "SKIPPED"].includes(t.status),
    );
  }
  canStartRun(): boolean {
    if (!this.selectedScenario) return false;
    if (this.selectedScenario === "AMBIGUOUS" && !this.goalText.trim())
      return false;
    return !this.isStarting;
  }
  resetRun(): void {
    this.stopPolling();
    this.activeRunId = null;
    this.activeRun = null;
    this.errorMessage = "";
  }
  ngOnDestroy(): void {
    this.stopPolling();
  }
}
