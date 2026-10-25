import { Component } from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
  AbstractControl,
  ValidationErrors,
} from "@angular/forms";
import { RouterModule, Router } from "@angular/router";
import { UrlService } from "../../../core/services/url.service";
import { UrlResponse } from "../../../core/models/url.model";
import { HeaderComponent } from "../../../shared/components/header/header.component";
import { SidebarComponent } from "../../../shared/components/sidebar/sidebar.component";
function urlValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  try {
    const url = new URL(control.value);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return { invalidUrl: true };
    }
    return null;
  } catch {
    return { invalidUrl: true };
  }
}
function aliasValidator(control: AbstractControl): ValidationErrors | null {
  if (!control.value) return null;
  if (!/^[a-zA-Z0-9_-]+$/.test(control.value)) {
    return { invalidAlias: true };
  }
  return null;
}
@Component({
  selector: "app-url-create",
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    HeaderComponent,
    SidebarComponent,
  ],
  templateUrl: "./url-create.component.html",
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
        max-width: 780px;
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
      .card {
        background: white;
        border-radius: 10px;
        border: 1px solid var(--color-border);
        box-shadow: var(--shadow-sm);
        overflow: hidden;
        margin-bottom: 1.5rem;
      }
      .card-header {
        padding: 1.25rem 1.5rem;
        border-bottom: 1px solid var(--color-border);
        background: var(--color-navy);
        h2 {
          color: white;
          font-size: 1.0625rem;
          font-weight: 700;
          margin: 0;
        }
        p {
          color: rgba(255, 255, 255, 0.65);
          font-size: 0.875rem;
          margin-top: 0.2rem;
        }
      }
      .card-body {
        padding: 1.75rem;
      }
      .form-group {
        margin-bottom: 1.5rem;
      }
      .form-label {
        display: block;
        font-size: 0.9375rem;
        font-weight: 700;
        color: var(--color-text);
        margin-bottom: 0.375rem;
        .required {
          color: var(--color-error);
          margin-left: 0.125rem;
        }
        .optional {
          font-size: 0.8125rem;
          font-weight: 400;
          color: var(--color-text-light);
          margin-left: 0.375rem;
        }
      }
      .form-control {
        width: 100%;
        padding: 0.625rem 0.875rem;
        border: 1.5px solid var(--color-border-dark);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        color: var(--color-text);
        background: white;
        transition: all 0.15s;
        outline: none;
        box-sizing: border-box;
        &:focus {
          border-color: var(--color-blue);
          box-shadow: 0 0 0 3px rgba(0, 160, 223, 0.15);
        }
        &.is-invalid {
          border-color: var(--color-error);
        }
      }
      .form-hint {
        margin-top: 0.3125rem;
        font-size: 0.8125rem;
        color: var(--color-text-light);
        line-height: 1.5;
      }
      .field-error {
        margin-top: 0.3125rem;
        font-size: 0.8125rem;
        color: var(--color-error);
        font-weight: 500;
      }
      .form-row {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1.25rem;
        @media (max-width: 600px) {
          grid-template-columns: 1fr;
        }
      }
      .tags-input-wrap {
        position: relative;
      }
      .tags-display {
        display: flex;
        flex-wrap: wrap;
        gap: 0.375rem;
        margin-top: 0.5rem;
      }
      .tag-chip {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.1875rem 0.5rem;
        background: rgba(0, 160, 223, 0.1);
        color: var(--color-blue);
        border-radius: 100px;
        font-size: 0.8125rem;
        font-weight: 600;
        button {
          background: none;
          border: none;
          cursor: pointer;
          color: inherit;
          font-size: 0.875rem;
          line-height: 1;
          padding: 0;
          opacity: 0.7;
          &:hover {
            opacity: 1;
          }
        }
      }
      .form-actions {
        display: flex;
        align-items: center;
        gap: 1rem;
        margin-top: 0.5rem;
      }
      .btn-primary {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.6875rem 1.5rem;
        background: var(--color-navy);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        font-weight: 700;
        cursor: pointer;
        transition: background 0.15s;
        &:hover:not(:disabled) {
          background: var(--color-navy-dark);
        }
        &:disabled {
          opacity: 0.65;
          cursor: not-allowed;
        }
      }
      .btn-outline {
        display: inline-flex;
        align-items: center;
        padding: 0.6875rem 1.25rem;
        background: white;
        color: var(--color-navy);
        border: 1.5px solid var(--color-navy);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.9375rem;
        font-weight: 600;
        cursor: pointer;
        text-decoration: none;
        transition: all 0.15s;
        &:hover {
          background: var(--color-navy);
          color: white;
        }
      }
      .spinner-sm {
        width: 18px;
        height: 18px;
        border: 2px solid rgba(255, 255, 255, 0.35);
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
      /* Success Result Card */
      .success-card {
        background: white;
        border-radius: 10px;
        border: 2px solid var(--color-success);
        box-shadow: var(--shadow-md);
        overflow: hidden;
        animation: slideIn 0.3s ease;
      }
      @keyframes slideIn {
        from {
          opacity: 0;
          transform: translateY(-12px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
      .success-card-header {
        background: var(--color-success);
        padding: 1.25rem 1.5rem;
        display: flex;
        align-items: center;
        gap: 0.75rem;
        h3 {
          color: white;
          font-size: 1.0625rem;
          font-weight: 700;
          margin: 0;
        }
        p {
          color: rgba(255, 255, 255, 0.8);
          font-size: 0.875rem;
          margin: 0;
        }
      }
      .success-icon {
        width: 40px;
        height: 40px;
        background: rgba(255, 255, 255, 0.2);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      .success-card-body {
        padding: 1.5rem;
      }
      .short-url-display {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        background: var(--color-bg);
        border: 1.5px solid var(--color-border);
        border-radius: 8px;
        padding: 0.875rem 1rem;
        margin-bottom: 1.25rem;
        .short-url-text {
          flex: 1;
          font-family: var(--font-mono);
          font-size: 1rem;
          font-weight: 700;
          color: var(--color-navy);
          word-break: break-all;
        }
      }
      .btn-copy {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.5rem 1rem;
        background: var(--color-navy);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 700;
        cursor: pointer;
        white-space: nowrap;
        transition: background 0.15s;
        flex-shrink: 0;
        &:hover {
          background: var(--color-navy-dark);
        }
        &.copied {
          background: var(--color-success);
        }
      }
      .result-meta {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 0.75rem;
        margin-bottom: 1.25rem;
      }
      .meta-item {
        .meta-label {
          font-size: 0.75rem;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--color-text-light);
          margin-bottom: 0.25rem;
        }
        .meta-value {
          font-size: 0.9rem;
          font-weight: 600;
          color: var(--color-text);
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
      .result-actions {
        display: flex;
        gap: 0.75rem;
        flex-wrap: wrap;
      }
      .btn-secondary {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5625rem 1.125rem;
        background: var(--color-blue);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 700;
        cursor: pointer;
        text-decoration: none;
        transition: background 0.15s;
        &:hover {
          background: var(--color-blue-dark);
        }
      }
      .btn-ghost {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5625rem 1.125rem;
        background: transparent;
        color: var(--color-text-muted);
        border: 1.5px solid var(--color-border-dark);
        border-radius: 6px;
        font-family: inherit;
        font-size: 0.875rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s;
        &:hover {
          background: var(--color-bg);
          color: var(--color-text);
        }
      }
      .alert-error {
        background: var(--color-error-bg);
        border: 1px solid rgba(198, 40, 40, 0.25);
        color: var(--color-error);
        border-radius: 6px;
        padding: 0.75rem 1rem;
        font-size: 0.9375rem;
        margin-bottom: 1.25rem;
        display: flex;
        align-items: flex-start;
        gap: 0.5rem;
      }
    `,
  ],
})
export class UrlCreateComponent {
  form: FormGroup;
  isLoading = false;
  errorMessage = "";
  createdUrl: UrlResponse | null = null;
  copied = false;
  tagInput = "";
  currentTags: string[] = [];
  constructor(
    private fb: FormBuilder,
    private urlService: UrlService,
    private router: Router,
  ) {
    this.form = this.fb.group({
      longUrl: ["", [Validators.required, urlValidator]],
      customAlias: [
        "",
        [aliasValidator, Validators.minLength(3), Validators.maxLength(50)],
      ],
      expirationDays: [null, [Validators.min(1), Validators.max(3650)]],
      tagInput: [""],
    });
  }
  get longUrl() {
    return this.form.get("longUrl");
  }
  get customAlias() {
    return this.form.get("customAlias");
  }
  get expirationDays() {
    return this.form.get("expirationDays");
  }
  addTag(): void {
    const val = this.form.get("tagInput")?.value?.trim();
    if (
      val &&
      !this.currentTags.includes(val) &&
      this.currentTags.length < 10
    ) {
      this.currentTags = [...this.currentTags, val];
      this.form.get("tagInput")?.setValue("");
    }
  }
  removeTag(tag: string): void {
    this.currentTags = this.currentTags.filter((t) => t !== tag);
  }
  onTagKeydown(event: KeyboardEvent): void {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      this.addTag();
    }
  }
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { longUrl, customAlias, expirationDays } = this.form.value;
    const request = {
      longUrl: longUrl.trim(),
      ...(customAlias?.trim() ? { customAlias: customAlias.trim() } : {}),
      ...(expirationDays ? { expirationDays: Number(expirationDays) } : {}),
      ...(this.currentTags.length > 0 ? { tags: this.currentTags } : {}),
    };
    this.isLoading = true;
    this.errorMessage = "";
    this.urlService.shorten(request).subscribe({
      next: (resp) => {
        this.isLoading = false;
        this.createdUrl = resp.data;
        this.form.reset();
        this.currentTags = [];
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 409) {
          this.errorMessage =
            "That custom alias is already taken. Please choose another.";
        } else {
          this.errorMessage =
            err.error?.message || "Failed to shorten URL. Please try again.";
        }
      },
    });
  }
  async copyUrl(): Promise<void> {
    if (this.createdUrl) {
      await this.urlService.copyToClipboard(this.createdUrl.shortUrl);
      this.copied = true;
      setTimeout(() => {
        this.copied = false;
      }, 2500);
    }
  }
  createAnother(): void {
    this.createdUrl = null;
    this.errorMessage = "";
  }
  goToAnalytics(): void {
    if (this.createdUrl) {
      this.router.navigate(["/analytics", this.createdUrl.shortCode]);
    }
  }
}
