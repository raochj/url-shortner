import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterModule, ActivatedRoute } from "@angular/router";
import { AnalyticsService } from "../../../core/services/analytics.service";
import { AnalyticsSummary } from "../../../core/models/analytics.model";
import { HeaderComponent } from "../../../shared/components/header/header.component";
import { SidebarComponent } from "../../../shared/components/sidebar/sidebar.component";
export interface SvgBar {
  x: number;
  y: number;
  width: number;
  height: number;
  date: string;
  count: number;
  chartHeight: number;
}
@Component({
  selector: "app-analytics-dashboard",
  standalone: true,
  imports: [CommonModule, RouterModule, HeaderComponent, SidebarComponent],
  templateUrl: "./analytics-dashboard.component.html",
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
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: 1rem;
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
      .short-code-tag {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.375rem 0.875rem;
        background: rgba(0, 48, 87, 0.08);
        border-radius: 6px;
        font-family: var(--font-mono);
        font-size: 0.9375rem;
        font-weight: 700;
        color: var(--color-navy);
        flex-shrink: 0;
      }
      .stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 1.25rem;
        margin-bottom: 2rem;
      }
      .stat-card {
        background: white;
        border-radius: 10px;
        padding: 1.375rem 1.5rem;
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-sm);
        .stat-label {
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.06em;
          color: var(--color-text-light);
          margin-bottom: 0.5rem;
        }
        .stat-value {
          font-size: 2.25rem;
          font-weight: 800;
          color: var(--color-navy);
          line-height: 1;
        }
        .stat-sub {
          font-size: 0.8125rem;
          color: var(--color-text-muted);
          margin-top: 0.25rem;
        }
      }
      .charts-row {
        display: grid;
        grid-template-columns: 2fr 1fr;
        gap: 1.5rem;
        margin-bottom: 1.5rem;
        @media (max-width: 1024px) {
          grid-template-columns: 1fr;
        }
      }
      .breakdown-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1.5rem;
        @media (max-width: 768px) {
          grid-template-columns: 1fr;
        }
      }
      .card {
        background: white;
        border-radius: 10px;
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
      }
      .card-header {
        padding: 1.125rem 1.5rem;
        border-bottom: 1px solid var(--color-border);
        display: flex;
        align-items: center;
        justify-content: space-between;
        h3 {
          font-size: 1rem;
          font-weight: 700;
          color: var(--color-navy);
          margin: 0;
        }
        .subtitle {
          font-size: 0.8125rem;
          color: var(--color-text-muted);
        }
      }
      .card-body {
        padding: 1.5rem;
      }
      /* SVG Bar Chart */
      .chart-container {
        padding: 1rem 1.5rem 1.5rem;
        overflow-x: auto;
      }
      .chart-svg {
        display: block;
      }
      .bar-label {
        font-size: 9px;
        fill: var(--color-text-light);
      }
      .bar-value-label {
        font-size: 9px;
        fill: var(--color-navy);
        font-weight: 700;
      }
      .chart-empty {
        text-align: center;
        padding: 3rem;
        color: var(--color-text-muted);
        font-size: 0.9375rem;
      }
      /* Breakdown Table */
      .breakdown-table {
        width: 100%;
        border-collapse: collapse;
        tr:not(:last-child) td {
          border-bottom: 1px solid var(--color-border);
        }
        td {
          padding: 0.625rem 1rem;
          font-size: 0.9rem;
          vertical-align: middle;
        }
        td:last-child {
          text-align: right;
        }
      }
      .country-flag {
        margin-right: 0.375rem;
      }
      .breakdown-name {
        font-weight: 500;
        color: var(--color-text);
      }
      .breakdown-bar-wrap {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .breakdown-bar-bg {
        flex: 1;
        height: 6px;
        background: var(--color-bg);
        border-radius: 100px;
        overflow: hidden;
        min-width: 60px;
      }
      .breakdown-bar-fill {
        height: 100%;
        background: var(--color-blue);
        border-radius: 100px;
        transition: width 0.6s ease;
      }
      .breakdown-count {
        font-weight: 700;
        color: var(--color-navy);
        font-size: 0.875rem;
        min-width: 36px;
        text-align: right;
      }
      .loading-state {
        text-align: center;
        padding: 3.5rem;
        color: var(--color-text-muted);
      }
      .spinner {
        width: 32px;
        height: 32px;
        border: 3px solid var(--color-border);
        border-top-color: var(--color-navy);
        border-radius: 50%;
        animation: spin 0.75s linear infinite;
        margin: 0 auto 1rem;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
      .error-state {
        text-align: center;
        padding: 3rem;
        color: var(--color-error);
        h3 {
          margin-bottom: 0.5rem;
        }
      }
      .btn-back {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.5rem 1rem;
        background: white;
        color: var(--color-navy);
        border: 1.5px solid var(--color-navy);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 600;
        cursor: pointer;
        text-decoration: none;
        transition: all 0.15s;
        &:hover {
          background: var(--color-navy);
          color: white;
        }
      }
      .device-item {
        display: flex;
        align-items: center;
        gap: 0.875rem;
        padding: 0.75rem 0;
        &:not(:last-child) {
          border-bottom: 1px solid var(--color-border);
        }
      }
      .device-icon {
        width: 36px;
        height: 36px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(0, 48, 87, 0.06);
        color: var(--color-navy);
        flex-shrink: 0;
      }
      .device-info {
        flex: 1;
      }
      .device-name {
        font-weight: 600;
        font-size: 0.9rem;
        color: var(--color-text);
      }
      .device-pct {
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }
    `,
  ],
})
export class AnalyticsDashboardComponent implements OnInit {
  shortCode = "";
  summary: AnalyticsSummary | null = null;
  isLoading = true;
  errorMessage = "";
  readonly CHART_WIDTH = 700;
  readonly CHART_HEIGHT = 180;
  readonly BAR_WIDTH = 28;
  readonly BAR_GAP = 8;
  readonly PADDING_LEFT = 40;
  readonly PADDING_BOTTOM = 28;
  constructor(
    private analyticsService: AnalyticsService,
    private route: ActivatedRoute,
  ) {}
  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      this.shortCode = params["shortCode"];
      if (this.shortCode) {
        this.loadSummary();
      }
    });
  }
  loadSummary(): void {
    this.isLoading = true;
    this.errorMessage = "";
    this.analyticsService.getSummary(this.shortCode).subscribe({
      next: (resp) => {
        this.isLoading = false;
        this.summary = resp.data;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || "Failed to load analytics.";
      },
    });
  }
  buildSvgBars(data: Record<string, number>): SvgBar[] {
    if (!data) return [];
    const entries = Object.entries(data)
      .sort(([a], [b]) => a.localeCompare(b))
      .slice(-20); // last 20 days max
    if (entries.length === 0) return [];
    const maxCount = Math.max(...entries.map(([, v]) => v), 1);
    const usableHeight = this.CHART_HEIGHT - this.PADDING_BOTTOM - 20;
    return entries.map(([date, count], i) => {
      const barHeight = Math.max((count / maxCount) * usableHeight, 2);
      const x = this.PADDING_LEFT + i * (this.BAR_WIDTH + this.BAR_GAP);
      const y = 20 + usableHeight - barHeight;
      return {
        x,
        y,
        width: this.BAR_WIDTH,
        height: barHeight,
        date: this.formatDateLabel(date),
        count,
        chartHeight: this.CHART_HEIGHT,
      };
    });
  }
  formatDateLabel(dateStr: string): string {
    try {
      const d = new Date(dateStr);
      return `${d.getMonth() + 1}/${d.getDate()}`;
    } catch {
      return dateStr;
    }
  }
  getSvgWidth(data: Record<string, number>): number {
    const count = Math.min(Object.keys(data || {}).length, 20);
    return Math.max(
      this.PADDING_LEFT + count * (this.BAR_WIDTH + this.BAR_GAP) + 20,
      400,
    );
  }
  getTopEntries(
    data: Record<string, number>,
    limit = 8,
  ): Array<{ key: string; value: number }> {
    if (!data) return [];
    return Object.entries(data)
      .sort(([, a], [, b]) => b - a)
      .slice(0, limit)
      .map(([key, value]) => ({ key, value }));
  }
  getMaxValue(data: Record<string, number>): number {
    if (!data) return 1;
    return Math.max(...Object.values(data), 1);
  }
  getBarWidthPct(value: number, data: Record<string, number>): number {
    return Math.round((value / this.getMaxValue(data)) * 100);
  }
  get totalClicksByDateEntries(): number {
    return this.summary
      ? Object.keys(this.summary.clicksByDate || {}).length
      : 0;
  }
}
