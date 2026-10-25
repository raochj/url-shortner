import { Component, Input } from "@angular/core";
import { CommonModule } from "@angular/common";
import { ReliabilityMetrics } from "../../../core/models/orchestration.model";
@Component({
  selector: "app-metrics-panel",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./metrics-panel.component.html",
  styles: [
    `
      :host {
        display: block;
      }
      .metrics-panel {
        background: white;
        border-radius: 10px;
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
      }
      .panel-header {
        padding: 1rem 1.25rem;
        border-bottom: 1px solid var(--color-border);
        background: var(--color-navy);
        h3 {
          color: white;
          font-size: 1rem;
          font-weight: 700;
          margin: 0;
        }
      }
      .panel-body {
        padding: 1.25rem;
      }
      /* Success Rate Ring */
      .rate-section {
        display: flex;
        align-items: center;
        gap: 1.5rem;
        margin-bottom: 1.25rem;
        padding-bottom: 1.25rem;
        border-bottom: 1px solid var(--color-border);
      }
      .ring-container {
        position: relative;
        flex-shrink: 0;
      }
      .ring-svg {
        transform: rotate(-90deg);
      }
      .ring-bg {
        fill: none;
        stroke: var(--color-border);
        stroke-width: 6;
      }
      .ring-fill {
        fill: none;
        stroke-width: 6;
        stroke-linecap: round;
        transition: stroke-dasharray 0.8s ease;
      }
      .ring-text {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        text-align: center;
      }
      .ring-value {
        font-size: 1.375rem;
        font-weight: 800;
        line-height: 1;
        display: block;
      }
      .ring-label {
        font-size: 0.625rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--color-text-light);
        font-weight: 600;
      }
      .rate-success {
        color: var(--color-success);
      }
      .rate-warning {
        color: var(--color-warning);
      }
      .rate-danger {
        color: var(--color-error);
      }
      .rate-info {
        flex: 1;
        h4 {
          font-size: 1rem;
          font-weight: 700;
          color: var(--color-navy);
          margin-bottom: 0.375rem;
        }
        p {
          font-size: 0.8125rem;
          color: var(--color-text-muted);
          line-height: 1.5;
        }
      }
      /* Metrics Grid */
      .metrics-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 0.875rem;
      }
      .metric-item {
        background: var(--color-bg);
        border-radius: 8px;
        padding: 0.875rem 1rem;
        .metric-label {
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--color-text-light);
          margin-bottom: 0.375rem;
        }
        .metric-value {
          font-size: 1.25rem;
          font-weight: 800;
          color: var(--color-navy);
          line-height: 1;
        }
        .metric-sub {
          font-size: 0.75rem;
          color: var(--color-text-muted);
          margin-top: 0.125rem;
        }
        &.danger .metric-value {
          color: var(--color-error);
        }
        &.success .metric-value {
          color: var(--color-success);
        }
        &.warning .metric-value {
          color: var(--color-warning);
        }
      }
      /* Progress bar for tasks */
      .task-progress {
        margin-bottom: 1rem;
      }
      .progress-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 0.5rem;
        font-size: 0.8125rem;
        span {
          font-weight: 600;
          color: var(--color-text);
        }
      }
      .progress-bar {
        height: 8px;
        background: var(--color-border);
        border-radius: 100px;
        overflow: hidden;
        display: flex;
      }
      .progress-segment {
        height: 100%;
        transition: width 0.6s ease;
        &.completed {
          background: var(--color-success);
        }
        &.failed {
          background: var(--color-error);
        }
        &.rolled-back {
          background: var(--color-purple);
        }
      }
    `,
  ],
})
export class MetricsPanelComponent {
  @Input() metrics!: ReliabilityMetrics;
  readonly RING_RADIUS = 36;
  readonly RING_CIRCUMFERENCE = 2 * Math.PI * this.RING_RADIUS;
  getSuccessRateClass(): string {
    if (!this.metrics) return "";
    if (this.metrics.successRate >= 80) return "rate-success";
    if (this.metrics.successRate >= 50) return "rate-warning";
    return "rate-danger";
  }
  getRingColor(): string {
    if (!this.metrics) return "#E2E8F0";
    if (this.metrics.successRate >= 80) return "var(--color-success)";
    if (this.metrics.successRate >= 50) return "var(--color-warning)";
    return "var(--color-error)";
  }
  getRingDashArray(): string {
    if (!this.metrics) return "0 " + this.RING_CIRCUMFERENCE;
    const filled = (this.metrics.successRate / 100) * this.RING_CIRCUMFERENCE;
    return `${filled} ${this.RING_CIRCUMFERENCE - filled}`;
  }
  formatMs(ms?: number): string {
    if (!ms && ms !== 0) return "—";
    if (ms < 1000) return `${Math.round(ms)}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  }
  getCompletedWidthPct(): number {
    if (!this.metrics || this.metrics.totalTasks === 0) return 0;
    return (this.metrics.completedTasks / this.metrics.totalTasks) * 100;
  }
  getFailedWidthPct(): number {
    if (!this.metrics || this.metrics.totalTasks === 0) return 0;
    return (this.metrics.failedTasks / this.metrics.totalTasks) * 100;
  }
  getRolledBackWidthPct(): number {
    if (!this.metrics || this.metrics.totalTasks === 0) return 0;
    return (this.metrics.rolledBackTasks / this.metrics.totalTasks) * 100;
  }
}
