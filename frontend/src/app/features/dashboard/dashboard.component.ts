import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterModule, Router } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { UrlService } from "../../core/services/url.service";
import { UrlResponse } from "../../core/models/url.model";
import { HeaderComponent } from "../../shared/components/header/header.component";
import { SidebarComponent } from "../../shared/components/sidebar/sidebar.component";
@Component({
  selector: "app-dashboard",
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    HeaderComponent,
    SidebarComponent,
  ],
  templateUrl: "./dashboard.component.html",
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
      .stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 1.25rem;
        margin-bottom: 2rem;
      }
      .stat-card {
        background: white;
        border-radius: 10px;
        padding: 1.5rem;
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-sm);
        display: flex;
        align-items: center;
        gap: 1.25rem;
        transition: box-shadow 0.15s;
        &:hover {
          box-shadow: var(--shadow-md);
        }
      }
      .stat-icon {
        width: 52px;
        height: 52px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        &.navy {
          background: rgba(0, 48, 87, 0.1);
          color: var(--color-navy);
        }
        &.blue {
          background: rgba(0, 160, 223, 0.1);
          color: var(--color-blue);
        }
        &.success {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.warning {
          background: var(--color-warning-bg);
          color: var(--color-warning);
        }
      }
      .stat-content {
        flex: 1;
      }
      .stat-value {
        font-size: 2rem;
        font-weight: 800;
        color: var(--color-navy);
        line-height: 1;
      }
      .stat-label {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
        margin-top: 0.25rem;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        font-weight: 600;
      }
      .content-grid {
        display: grid;
        grid-template-columns: 2fr 1fr;
        gap: 1.5rem;
        @media (max-width: 900px) {
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
      }
      .card-body {
        padding: 1.5rem;
      }
      .url-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9rem;
        th {
          text-align: left;
          padding: 0.625rem 0.875rem;
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--color-text-muted);
          border-bottom: 2px solid var(--color-border);
        }
        td {
          padding: 0.75rem 0.875rem;
          border-bottom: 1px solid var(--color-border);
          vertical-align: middle;
          &:last-child {
            border-bottom: none;
          }
        }
        tr:hover td {
          background: rgba(0, 160, 223, 0.03);
        }
      }
      .short-code-link {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        font-weight: 700;
        color: var(--color-navy);
        background: rgba(0, 48, 87, 0.06);
        padding: 0.1875rem 0.5rem;
        border-radius: 4px;
        text-decoration: none;
        transition: all 0.15s;
        &:hover {
          background: var(--color-navy);
          color: white;
        }
      }
      .long-url {
        max-width: 280px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }
      .click-count {
        font-weight: 700;
        color: var(--color-navy);
      }
      .status-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.1875rem 0.5rem;
        border-radius: 100px;
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        &.active {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.inactive {
          background: #f1f5f9;
          color: #64748b;
        }
      }
      .btn-link {
        background: none;
        border: none;
        padding: 0.25rem 0.5rem;
        cursor: pointer;
        color: var(--color-blue);
        font-size: 0.8125rem;
        font-weight: 600;
        border-radius: 4px;
        transition: all 0.15s;
        &:hover {
          background: rgba(0, 160, 223, 0.1);
        }
      }
      /* Quick shorten form */
      .quick-shorten {
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .quick-shorten-input {
        display: flex;
        gap: 0.5rem;
      }
      .quick-input {
        flex: 1;
        padding: 0.5625rem 0.875rem;
        border: 1.5px solid var(--color-border-dark);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        outline: none;
        transition: all 0.15s;
        &:focus {
          border-color: var(--color-blue);
          box-shadow: 0 0 0 3px rgba(0, 160, 223, 0.12);
        }
      }
      .btn-primary-sm {
        padding: 0.5625rem 1.125rem;
        background: var(--color-navy);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        font-weight: 700;
        cursor: pointer;
        white-space: nowrap;
        transition: background 0.15s;
        display: flex;
        align-items: center;
        gap: 0.375rem;
        &:hover:not(:disabled) {
          background: var(--color-navy-dark);
        }
        &:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }
      }
      .quick-result {
        background: var(--color-success-bg);
        border: 1px solid rgba(46, 125, 50, 0.2);
        border-radius: 8px;
        padding: 1rem;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
      }
      .quick-result-url {
        font-family: var(--font-mono);
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--color-success);
        word-break: break-all;
      }
      .btn-copy-sm {
        padding: 0.375rem 0.875rem;
        background: var(--color-success);
        color: white;
        border: none;
        border-radius: 4px;
        font-family: inherit;
        font-size: 0.8125rem;
        font-weight: 600;
        cursor: pointer;
        white-space: nowrap;
        flex-shrink: 0;
        &:hover {
          background: #1b5e20;
        }
      }
      .empty-state {
        text-align: center;
        padding: 2.5rem 1rem;
        color: var(--color-text-muted);
        .empty-icon {
          font-size: 2rem;
          margin-bottom: 0.75rem;
          opacity: 0.4;
        }
        p {
          font-size: 0.9rem;
        }
      }
      .loading-state {
        text-align: center;
        padding: 2.5rem;
        color: var(--color-text-muted);
      }
      .spinner {
        width: 28px;
        height: 28px;
        border: 3px solid var(--color-border);
        border-top-color: var(--color-navy);
        border-radius: 50%;
        animation: spin 0.75s linear infinite;
        margin: 0 auto 0.75rem;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
      .view-all-link {
        font-size: 0.875rem;
        color: var(--color-blue);
        font-weight: 600;
        text-decoration: none;
        &:hover {
          text-decoration: underline;
        }
      }
      .copied-feedback {
        font-size: 0.75rem;
        color: var(--color-success);
        font-weight: 600;
      }
    `,
  ],
})
export class DashboardComponent implements OnInit {
  recentUrls: UrlResponse[] = [];
  totalUrls = 0;
  totalClicks = 0;
  activeUrls = 0;
  isLoading = true;
  quickUrl = "";
  quickShortenLoading = false;
  quickResult: UrlResponse | null = null;
  quickError = "";
  copied = false;
  constructor(
    private urlService: UrlService,
    private router: Router,
  ) {}
  ngOnInit(): void {
    this.loadData();
  }
  loadData(): void {
    this.isLoading = true;
    this.urlService.getMyUrls(0, 5).subscribe({
      next: (resp) => {
        this.isLoading = false;
        const data = resp.data;
        this.recentUrls = data.content;
        this.totalUrls = data.totalElements;
        this.totalClicks = data.content.reduce(
          (sum, u) => sum + u.clickCount,
          0,
        );
        this.activeUrls = data.content.filter((u) => u.isActive).length;
      },
      error: () => {
        this.isLoading = false;
      },
    });
  }
  quickShorten(): void {
    if (!this.quickUrl.trim()) return;
    this.quickShortenLoading = true;
    this.quickResult = null;
    this.quickError = "";
    this.urlService.shorten({ longUrl: this.quickUrl.trim() }).subscribe({
      next: (resp) => {
        this.quickShortenLoading = false;
        this.quickResult = resp.data;
        this.quickUrl = "";
        this.loadData();
      },
      error: (err) => {
        this.quickShortenLoading = false;
        this.quickError = err.error?.message || "Failed to shorten URL.";
      },
    });
  }
  async copyQuickResult(): Promise<void> {
    if (this.quickResult) {
      await this.urlService.copyToClipboard(this.quickResult.shortUrl);
      this.copied = true;
      setTimeout(() => {
        this.copied = false;
      }, 2000);
    }
  }
  goToAnalytics(shortCode: string): void {
    this.router.navigate(["/analytics", shortCode]);
  }
}
