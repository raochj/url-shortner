import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from "@angular/forms";
import { RouterModule, Router, ActivatedRoute } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
@Component({
  selector: "app-login",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: "./login.component.html",
  styles: [
    `
      :host {
        display: flex;
        min-height: 100vh;
        background: linear-gradient(
          135deg,
          var(--color-navy) 0%,
          #004080 50%,
          var(--color-navy-dark) 100%
        );
        align-items: center;
        justify-content: center;
        padding: 2rem 1rem;
      }
      .login-wrapper {
        width: 100%;
        max-width: 440px;
      }
      .login-brand {
        text-align: center;
        margin-bottom: 2rem;
      }
      .brand-logo-large {
        width: 64px;
        height: 64px;
        background-color: var(--color-blue);
        border-radius: 50%;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 1.75rem;
        font-weight: 800;
        color: white;
        margin-bottom: 1rem;
        box-shadow: 0 4px 16px rgba(0, 160, 223, 0.4);
      }
      .brand-title {
        font-size: 1.625rem;
        font-weight: 700;
        color: var(--color-white);
        margin-bottom: 0.25rem;
      }
      .brand-tagline {
        font-size: 0.875rem;
        color: rgba(255, 255, 255, 0.6);
      }
      .login-card {
        background: white;
        border-radius: 12px;
        box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
        overflow: hidden;
      }
      .login-card-header {
        background: var(--color-navy);
        padding: 1.5rem 2rem;
        text-align: center;
        h2 {
          color: white;
          font-size: 1.25rem;
          font-weight: 700;
          margin: 0;
        }
        p {
          color: rgba(255, 255, 255, 0.65);
          font-size: 0.875rem;
          margin-top: 0.25rem;
        }
      }
      .login-card-body {
        padding: 2rem;
      }
      .form-group {
        margin-bottom: 1.25rem;
      }
      .form-label {
        display: block;
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--color-text);
        margin-bottom: 0.375rem;
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
      .field-error {
        margin-top: 0.25rem;
        font-size: 0.8125rem;
        color: var(--color-error);
        font-weight: 500;
      }
      .btn-submit {
        width: 100%;
        padding: 0.75rem;
        background: var(--color-navy);
        color: white;
        border: none;
        border-radius: 6px;
        font-family: inherit;
        font-size: 1rem;
        font-weight: 700;
        cursor: pointer;
        transition: background 0.15s;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        margin-top: 1.5rem;
        &:hover:not(:disabled) {
          background: var(--color-navy-dark);
        }
        &:disabled {
          opacity: 0.65;
          cursor: not-allowed;
        }
      }
      .spinner-sm {
        width: 18px;
        height: 18px;
        border: 2px solid rgba(255, 255, 255, 0.35);
        border-top-color: white;
        border-radius: 50%;
        animation: spin 0.75s linear infinite;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
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
      .login-card-footer {
        border-top: 1px solid var(--color-border);
        padding: 1.25rem 2rem;
        text-align: center;
        font-size: 0.9375rem;
        color: var(--color-text-muted);
        a {
          color: var(--color-blue);
          font-weight: 600;
          text-decoration: none;
          &:hover {
            text-decoration: underline;
          }
        }
      }
      .session-expired-banner {
        background: var(--color-warning-bg);
        border: 1px solid rgba(230, 81, 0, 0.25);
        color: var(--color-warning);
        border-radius: 8px;
        padding: 0.75rem 1rem;
        margin-bottom: 1rem;
        font-size: 0.875rem;
        text-align: center;
        font-weight: 500;
      }
    `,
  ],
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  isLoading = false;
  errorMessage = "";
  sessionExpired = false;
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
  ) {
    this.loginForm = this.fb.group({
      username: ["", [Validators.required, Validators.minLength(3)]],
      password: ["", [Validators.required, Validators.minLength(6)]],
    });
  }
  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(["/dashboard"]);
      return;
    }
    this.route.queryParams.subscribe((params) => {
      this.sessionExpired = params["reason"] === "session_expired";
    });
  }
  get username() {
    return this.loginForm.get("username");
  }
  get password() {
    return this.loginForm.get("password");
  }
  submit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }
    this.isLoading = true;
    this.errorMessage = "";
    const { username, password } = this.loginForm.value;
    this.authService.login(username, password).subscribe({
      next: () => {
        this.router.navigate(["/dashboard"]);
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 401) {
          this.errorMessage = "Invalid username or password. Please try again.";
        } else if (err.status === 0) {
          this.errorMessage =
            "Cannot connect to server. Please try again later.";
        } else {
          this.errorMessage =
            err.error?.message || "Login failed. Please try again.";
        }
      },
    });
  }
}
