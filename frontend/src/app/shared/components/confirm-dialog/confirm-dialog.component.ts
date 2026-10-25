import { Component, Input, Output, EventEmitter } from "@angular/core";
import { CommonModule } from "@angular/common";
@Component({
  selector: "app-confirm-dialog",
  standalone: true,
  imports: [CommonModule],
  template: `
    <div ngIf="visible" class="overlay" (click)="onCancel()">
      <div class="dialog" (click)="$event.stopPropagation()">
        <div class="dialog-header">
          <h3>{{ title }}</h3>
        </div>
        <div class="dialog-body">
          <p>{{ message }}</p>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" (click)="onCancel()">
            {{ cancelLabel }}
          </button>
          <button class="btn-confirm" (click)="onConfirm()">
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .overlay {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(0, 0, 0, 0.45);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        backdrop-filter: blur(2px);
      }
      .dialog {
        background: white;
        border-radius: 12px;
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
        width: 100%;
        max-width: 420px;
        margin: 1rem;
        overflow: hidden;
        animation: dialogIn 0.2s ease;
      }
      @keyframes dialogIn {
        from {
          opacity: 0;
          transform: scale(0.95) translateY(-8px);
        }
        to {
          opacity: 1;
          transform: scale(1) translateY(0);
        }
      }
      .dialog-header {
        padding: 1.25rem 1.5rem;
        border-bottom: 1px solid var(--color-border);
        background: var(--color-navy);
        h3 {
          color: white;
          font-size: 1rem;
          font-weight: 700;
          margin: 0;
        }
      }
      .dialog-body {
        padding: 1.5rem;
        p {
          color: var(--color-text-muted);
          font-size: 0.9375rem;
          line-height: 1.6;
        }
      }
      .dialog-footer {
        padding: 1rem 1.5rem;
        border-top: 1px solid var(--color-border);
        display: flex;
        justify-content: flex-end;
        gap: 0.75rem;
      }
      .btn-cancel {
        padding: 0.5rem 1.25rem;
        background: white;
        color: var(--color-text-muted);
        border: 1.5px solid var(--color-border-dark);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s;
        &:hover {
          background: var(--color-bg);
        }
      }
      .btn-confirm {
        padding: 0.5rem 1.25rem;
        background: var(--color-error);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        font-weight: 700;
        cursor: pointer;
        transition: background 0.15s;
        &:hover {
          background: #a31e1e;
        }
      }
    `,
  ],
})
export class ConfirmDialogComponent {
  @Input() visible = false;
  @Input() title = "Confirm Action";
  @Input() message = "Are you sure you want to proceed?";
  @Input() confirmLabel = "Confirm";
  @Input() cancelLabel = "Cancel";
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();
  onConfirm(): void {
    this.confirmed.emit();
  }
  onCancel(): void {
    this.cancelled.emit();
  }
}
