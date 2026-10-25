import { Component, Input, Output, EventEmitter } from "@angular/core";
import { CommonModule } from "@angular/common";
import { TaskNode } from "../../../core/models/orchestration.model";
@Component({
  selector: "app-task-node-card",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./task-node-card.component.html",
  styles: [
    `
      :host {
        display: block;
      }
      .task-card {
        background: white;
        border: 1.5px solid var(--color-border);
        border-radius: 8px;
        overflow: hidden;
        transition: box-shadow 0.15s;
        &:hover {
          box-shadow: var(--shadow-sm);
        }
      }
      .task-card-header {
        display: flex;
        align-items: flex-start;
        gap: 0.875rem;
        padding: 0.875rem 1rem;
        cursor: pointer;
        background: white;
        &:hover {
          background: rgba(0, 48, 87, 0.02);
        }
      }
      .task-status-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        flex-shrink: 0;
        margin-top: 5px;
        &.pending {
          background: #94a3b8;
        }
        &.in-progress {
          background: var(--color-blue);
          animation: pulse 1.5s infinite;
        }
        &.awaiting-approval {
          background: var(--color-warning);
        }
        &.approved {
          background: #1565c0;
        }
        &.completed {
          background: var(--color-success);
        }
        &.failed {
          background: var(--color-error);
        }
        &.rolled-back {
          background: var(--color-purple);
        }
        &.skipped {
          background: #cbd5e0;
        }
      }
      @keyframes pulse {
        0%,
        100% {
          opacity: 1;
        }
        50% {
          opacity: 0.5;
        }
      }
      .task-info {
        flex: 1;
        min-width: 0;
      }
      .task-name {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--color-text);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .task-meta {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-top: 0.25rem;
        flex-wrap: wrap;
      }
      .stage-badge {
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        padding: 0.125rem 0.5rem;
        border-radius: 100px;
        background: rgba(0, 48, 87, 0.08);
        color: var(--color-navy);
      }
      .status-badge {
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        padding: 0.125rem 0.5rem;
        border-radius: 100px;
        &.PENDING {
          background: #f1f5f9;
          color: #64748b;
        }
        &.IN_PROGRESS {
          background: var(--color-info-bg);
          color: var(--color-info);
        }
        &.AWAITING_APPROVAL {
          background: var(--color-warning-bg);
          color: var(--color-warning);
        }
        &.APPROVED {
          background: #e3f2fd;
          color: #1565c0;
        }
        &.COMPLETED {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.FAILED {
          background: var(--color-error-bg);
          color: var(--color-error);
        }
        &.ROLLED_BACK {
          background: var(--color-purple-bg);
          color: var(--color-purple);
        }
        &.SKIPPED {
          background: #f8fafc;
          color: #94a3b8;
        }
      }
      .latency-badge {
        font-size: 0.6875rem;
        color: var(--color-text-light);
        font-weight: 500;
      }
      .attempt-badge {
        font-size: 0.6875rem;
        color: var(--color-text-light);
        font-weight: 500;
      }
      .expand-icon {
        color: var(--color-text-light);
        transition: transform 0.2s;
        flex-shrink: 0;
        &.expanded {
          transform: rotate(180deg);
        }
      }
      .task-detail {
        border-top: 1px solid var(--color-border);
        padding: 0.875rem 1rem;
        background: var(--color-bg);
      }
      .detail-field {
        margin-bottom: 0.625rem;
        &:last-child {
          margin-bottom: 0;
        }
      }
      .detail-label {
        font-size: 0.75rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--color-text-light);
        margin-bottom: 0.25rem;
      }
      .detail-value {
        font-size: 0.875rem;
        color: var(--color-text);
        line-height: 1.5;
      }
      .error-detail {
        background: var(--color-error-bg);
        border: 1px solid rgba(198, 40, 40, 0.2);
        border-radius: 6px;
        padding: 0.625rem 0.75rem;
        font-size: 0.8125rem;
        color: var(--color-error);
        font-family: var(--font-mono);
        word-break: break-word;
      }
      .approval-prompt {
        background: var(--color-warning-bg);
        border: 1px solid rgba(230, 81, 0, 0.2);
        border-radius: 6px;
        padding: 0.625rem 0.75rem;
        font-size: 0.875rem;
        color: var(--color-warning);
        line-height: 1.5;
      }
      .deps-list {
        display: flex;
        flex-wrap: wrap;
        gap: 0.375rem;
      }
      .dep-chip {
        font-size: 0.6875rem;
        padding: 0.125rem 0.5rem;
        background: rgba(0, 48, 87, 0.07);
        color: var(--color-navy);
        border-radius: 4px;
        font-family: var(--font-mono);
        font-weight: 600;
      }
      .approval-actions {
        display: flex;
        gap: 0.625rem;
        margin-top: 0.75rem;
        padding-top: 0.75rem;
        border-top: 1px solid rgba(230, 81, 0, 0.2);
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
        transition: background 0.15s;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
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
        transition: background 0.15s;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        &:hover {
          background: #a31e1e;
        }
      }
    `,
  ],
})
export class TaskNodeCardComponent {
  @Input() task!: TaskNode;
  @Input() showApprovalButtons = false;
  @Output() approve = new EventEmitter<string>();
  @Output() reject = new EventEmitter<string>();
  expanded = false;
  toggleExpanded(): void {
    this.expanded = !this.expanded;
  }
  onApprove(): void {
    this.approve.emit(this.task.taskId);
  }
  onReject(): void {
    this.reject.emit(this.task.taskId);
  }
  getStatusDotClass(status: string): string {
    const map: Record<string, string> = {
      PENDING: "pending",
      IN_PROGRESS: "in-progress",
      AWAITING_APPROVAL: "awaiting-approval",
      APPROVED: "approved",
      COMPLETED: "completed",
      FAILED: "failed",
      ROLLED_BACK: "rolled-back",
      SKIPPED: "skipped",
    };
    return map[status] || "pending";
  }
  formatLatency(ms?: number): string {
    if (!ms) return "";
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(1)}s`;
  }
}
