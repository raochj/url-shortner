import { Component, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { Router, RouterModule } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
import { User } from "../../../core/models/user.model";
@Component({
  selector: "app-header",
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: "./header.component.html",
  styles: [
    `
      :host {
        display: block;
      }
      .header {
        background-color: var(--color-navy);
        color: var(--color-white);
        height: 64px;
        display: flex;
        align-items: center;
        padding: 0 1.5rem;
        position: sticky;
        top: 0;
        z-index: 100;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
      }
      .header-brand {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        text-decoration: none;
        flex-shrink: 0;
      }
      .brand-logo {
        width: 38px;
        height: 38px;
        background-color: var(--color-blue);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 1.125rem;
        font-weight: 800;
        color: white;
        letter-spacing: -0.5px;
        flex-shrink: 0;
      }
      .brand-text {
        display: flex;
        flex-direction: column;
        line-height: 1.2;
      }
      .brand-name {
        font-size: 1.0625rem;
        font-weight: 700;
        color: var(--color-white);
        letter-spacing: 0.01em;
      }
      .brand-subtitle {
        font-size: 0.6875rem;
        color: rgba(255, 255, 255, 0.65);
        letter-spacing: 0.05em;
        text-transform: uppercase;
      }
      .header-spacer {
        flex: 1;
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 1rem;
      }
      .user-info {
        display: flex;
        align-items: center;
        gap: 0.625rem;
      }
      .user-avatar {
        width: 34px;
        height: 34px;
        background-color: rgba(255, 255, 255, 0.15);
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.875rem;
        font-weight: 700;
        color: white;
        flex-shrink: 0;
      }
      .user-details {
        display: flex;
        flex-direction: column;
        line-height: 1.2;
      }
      .user-name {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--color-white);
      }
      .user-role {
        display: inline-flex;
        align-items: center;
        padding: 0.0625rem 0.5rem;
        border-radius: 100px;
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        margin-top: 1px;
        width: fit-content;
      }
      .role-admin {
        background-color: var(--color-blue);
        color: white;
      }
      .role-user {
        background-color: rgba(255, 255, 255, 0.2);
        color: rgba(255, 255, 255, 0.85);
      }
      .btn-logout {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.4375rem 1rem;
        background-color: transparent;
        border: 1.5px solid rgba(255, 255, 255, 0.35);
        border-radius: 4px;
        color: rgba(255, 255, 255, 0.85);
        font-size: 0.875rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        font-family: inherit;
        letter-spacing: 0.01em;
        &:hover {
          background-color: rgba(255, 255, 255, 0.12);
          border-color: rgba(255, 255, 255, 0.6);
          color: white;
        }
      }
      .header-divider {
        width: 1px;
        height: 28px;
        background-color: rgba(255, 255, 255, 0.2);
      }
      @media (max-width: 600px) {
        .brand-subtitle,
        .user-details {
          display: none;
        }
      }
    `,
  ],
})
export class HeaderComponent implements OnInit {
  currentUser: User | null = null;
  constructor(
    public authService: AuthService,
    private router: Router,
  ) {}
  ngOnInit(): void {
    this.authService.currentUser$.subscribe((user) => {
      this.currentUser = user;
    });
  }
  getInitials(username: string): string {
    return username ? username.charAt(0).toUpperCase() : "?";
  }
  logout(): void {
    this.authService.logout();
  }
}
