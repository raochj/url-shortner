import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterModule, Router } from "@angular/router";
import { UrlService } from "../../../core/services/url.service";
import { UrlResponse } from "../../../core/models/url.model";
import { HeaderComponent } from "../../../shared/components/header/header.component";
import { SidebarComponent } from "../../../shared/components/sidebar/sidebar.component";
@Component({
  selector: "app-url-list",
  standalone: true,
  imports: [CommonModule, RouterModule, HeaderComponent, SidebarComponent],
  templateUrl: "./url-list.component.html",
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
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        margin-bottom: 1.5rem;
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
        .results-count {
          font-size: 0.875rem;
          color: var(--color-text-muted);
        }
      }
      .url-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.9rem;
        table-layout: fixed;
        thead tr {
          background: var(--color-navy);
        }
        th {
          text-align: left;
          padding: 0.875rem 1rem;
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: rgba(255, 255, 255, 0.85);
        }
        th:nth-child(1) {
          width: 130px;
        }
        th:nth-child(2) {
          width: auto;
        }
        th:nth-child(3) {
          width: 80px;
        }
        th:nth-child(4) {
          width: 90px;
        }
        th:nth-child(5) {
          width: 120px;
        }
        th:nth-child(6) {
          width: 120px;
        }
        th:nth-child(7) {
          width: 160px;
        }
        td {
          padding: 0.875rem 1rem;
          border-bottom: 1px solid var(--color-border);
          vertical-align: middle;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        tr:last-child td {
          border-bottom: none;
        }
        tbody tr:hover td {
          background: rgba(0, 160, 223, 0.03);
        }
      }
      .short-code-cell {
        display: flex;
        align-items: center;
        gap: 0.375rem;
      }
      .short-code-badge {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        font-weight: 700;
        color: var(--color-navy);
        background: rgba(0, 48, 87, 0.07);
        padding: 0.1875rem 0.5rem;
        border-radius: 4px;
        white-space: nowrap;
      }
      .long-url-cell {
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.875rem;
        color: var(--color-text-muted);
        a {
          color: inherit;
          text-decoration: none;
          &:hover {
            color: var(--color-blue);
            text-decoration: underline;
          }
        }
      }
      .click-badge {
        font-weight: 700;
        color: var(--color-navy);
        font-size: 0.9375rem;
      }
      .status-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem 0.625rem;
        border-radius: 100px;
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        white-space: nowrap;
        &.active {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.inactive {
          background: #f1f5f9;
          color: #64748b;
        }
      }
      .expiry-cell {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
      }
      .expiry-expired {
        color: var(--color-error);
        font-weight: 600;
      }
      .tags-cell {
        display: flex;
        gap: 0.25rem;
        flex-wrap: wrap;
      }
      .tag-pill {
        font-size: 0.6875rem;
        padding: 0.125rem 0.5rem;
        background: rgba(0, 160, 223, 0.1);
        color: var(--color-blue);
        border-radius: 100px;
        font-weight: 600;
        white-space: nowrap;
      }
      .actions-cell {
        display: flex;
        align-items: center;
        gap: 0.25rem;
      }
      .action-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        border: none;
        border-radius: 6px;
        cursor: pointer;
        transition: all 0.15s;
        color: var(--color-text-muted);
        background: transparent;
        &:hover {
          background: var(--color-bg);
          color: var(--color-navy);
        }
        &.danger:hover {
          background: var(--color-error-bg);
          color: var(--color-error);
        }
        &.success-btn:hover {
          background: var(--color-success-bg);
          color: var(--color-success);
        }
        &.info:hover {
          background: rgba(0, 160, 223, 0.1);
          color: var(--color-blue);
        }
      }
      .tooltip-wrap {
        position: relative;
        display: inline-flex;
      }
      .btn-primary {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5625rem 1.25rem;
        background: var(--color-navy);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        font-weight: 700;
        cursor: pointer;
        text-decoration: none;
        transition: background 0.15s;
        &:hover {
          background: var(--color-navy-dark);
        }
      }
      .pagination {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1rem 1.5rem;
        border-top: 1px solid var(--color-border);
        background: var(--color-bg);
      }
      .pagination-info {
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }
      .pagination-controls {
        display: flex;
        gap: 0.5rem;
      }
      .page-btn {
        padding: 0.4375rem 0.875rem;
        background: white;
        border: 1.5px solid var(--color-border-dark);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 600;
        cursor: pointer;
        color: var(--color-text);
        transition: all 0.15s;
        &:hover:not(:disabled) {
          background: var(--color-navy);
          color: white;
          border-color: var(--color-navy);
        }
        &:disabled {
          opacity: 0.45;
          cursor: not-allowed;
        }
      }
      .empty-state {
        text-align: center;
        padding: 3.5rem 1rem;
        color: var(--color-text-muted);
        .empty-icon {
          font-size: 3rem;
          margin-bottom: 1rem;
          opacity: 0.4;
        }
        h3 {
          color: var(--color-navy);
          margin-bottom: 0.5rem;
        }
        p {
          font-size: 0.9375rem;
          margin-bottom: 1.5rem;
        }
      }
      .loading-state {
        text-align: center;
        padding: 3rem;
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
      .copy-feedback {
        position: fixed;
        bottom: 1.5rem;
        right: 1.5rem;
        background: var(--color-success);
        color: white;
        padding: 0.625rem 1.25rem;
        border-radius: 8px;
        font-size: 0.875rem;
        font-weight: 600;
        box-shadow: var(--shadow-lg);
        animation: fadeInUp 0.2s ease;
      }
      @keyframes fadeInUp {
        from {
          opacity: 0;
          transform: translateY(8px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
    `,
  ],
})
export class UrlListComponent implements OnInit {
  urls: UrlResponse[] = [];
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 10;
  isLoading = true;
  copyFeedback = false;
  deletingCode: string | null = null;
  constructor(
    private urlService: UrlService,
    private router: Router,
  ) {}
  ngOnInit(): void {
    this.loadUrls();
  }
  loadUrls(): void {
    this.isLoading = true;
    this.urlService.getMyUrls(this.currentPage, this.pageSize).subscribe({
      next: (resp) => {
        this.isLoading = false;
        this.urls = resp.data.content;
        this.totalPages = resp.data.totalPages;
        this.totalElements = resp.data.totalElements;
      },
      error: () => {
        this.isLoading = false;
      },
    });
  }
  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadUrls();
    }
  }
  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadUrls();
    }
  }
  goToPage(page: number): void {
    this.currentPage = page;
    this.loadUrls();
  }
  async copyShortUrl(url: UrlResponse): Promise<void> {
    await this.urlService.copyToClipboard(url.shortUrl);
    this.copyFeedback = true;
    setTimeout(() => {
      this.copyFeedback = false;
    }, 2000);
  }
  toggleActive(url: UrlResponse): void {
    this.urlService.toggleActive(url.shortCode, !url.isActive).subscribe({
      next: (resp) => {
        const idx = this.urls.findIndex((u) => u.shortCode === url.shortCode);
        if (idx !== -1) {
          this.urls[idx] = resp.data;
        }
      },
    });
  }
  deleteUrl(shortCode: string): void {
    if (!confirm(`Delete short URL "${shortCode}"? This cannot be undone.`))
      return;
    this.deletingCode = shortCode;
    this.urlService.deleteUrl(shortCode).subscribe({
      next: () => {
        this.deletingCode = null;
        this.loadUrls();
      },
      error: () => {
        this.deletingCode = null;
      },
    });
  }
  goToAnalytics(shortCode: string): void {
    this.router.navigate(["/analytics", shortCode]);
  }
  isExpired(expiresAt?: string): boolean {
    if (!expiresAt) return false;
    return new Date(expiresAt) < new Date();
  }
  formatExpiry(expiresAt?: string): string {
    if (!expiresAt) return "Never";
    const d = new Date(expiresAt);
    if (this.isExpired(expiresAt)) return "Expired";
    return d.toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  }
  get pagesArray(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i);
  }
  get startItem(): number {
    return this.currentPage * this.pageSize + 1;
  }
  get endItem(): number {
    return Math.min((this.currentPage + 1) * this.pageSize, this.totalElements);
  }
}
