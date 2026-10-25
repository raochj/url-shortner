import { Component, OnInit, Input } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterModule, RouterLinkActive, Router } from "@angular/router";
import { AuthService } from "../../../core/services/auth.service";
interface NavItem {
  label: string;
  route: string;
  icon: string;
  adminOnly?: boolean;
  exact?: boolean;
}
@Component({
  selector: "app-sidebar",
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: "./sidebar.component.html",
  styles: [
    `
      :host {
        display: block;
      }
      .sidebar {
        width: 240px;
        min-height: calc(100vh - 64px);
        background-color: var(--color-white);
        border-right: 1px solid var(--color-border);
        display: flex;
        flex-direction: column;
        flex-shrink: 0;
        overflow-y: auto;
      }
      .nav-section {
        padding: 1.25rem 0.75rem 0.5rem;
      }
      .nav-section-title {
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: var(--color-text-light);
        padding: 0 0.75rem;
        margin-bottom: 0.5rem;
      }
      .nav-item {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5625rem 0.75rem;
        border-radius: 6px;
        color: var(--color-text-muted);
        text-decoration: none;
        font-size: 0.9375rem;
        font-weight: 500;
        transition: all 0.15s ease;
        margin-bottom: 0.125rem;
        cursor: pointer;
        &:hover {
          background-color: rgba(0, 48, 87, 0.06);
          color: var(--color-navy);
          text-decoration: none;
        }
        &.active {
          background-color: rgba(0, 48, 87, 0.08);
          color: var(--color-navy);
          font-weight: 600;
          .nav-icon {
            color: var(--color-blue);
          }
        }
      }
      .nav-icon {
        width: 20px;
        height: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        color: var(--color-text-light);
        transition: color 0.15s ease;
      }
      .nav-item:hover .nav-icon {
        color: var(--color-blue);
      }
      .nav-badge {
        margin-left: auto;
        background-color: var(--color-blue);
        color: white;
        font-size: 0.6875rem;
        font-weight: 700;
        padding: 0.0625rem 0.4375rem;
        border-radius: 100px;
      }
      .sidebar-footer {
        margin-top: auto;
        padding: 1rem 0.75rem;
        border-top: 1px solid var(--color-border);
      }
      .version-info {
        font-size: 0.75rem;
        color: var(--color-text-light);
        padding: 0 0.75rem;
      }
      .nav-divider {
        height: 1px;
        background-color: var(--color-border);
        margin: 0.5rem 0.75rem;
      }
      @media (max-width: 768px) {
        .sidebar {
          width: 200px;
        }
      }
    `,
  ],
})
export class SidebarComponent implements OnInit {
  @Input() currentShortCode?: string;
  navItems: NavItem[] = [
    {
      label: "Dashboard",
      route: "/dashboard",
      icon: "dashboard",
      exact: true,
    },
    {
      label: "My URLs",
      route: "/urls",
      icon: "link",
    },
    {
      label: "Create Short URL",
      route: "/urls/new",
      icon: "add",
    },
    {
      label: "Orchestration",
      route: "/orchestration",
      icon: "orchestration",
      adminOnly: false,
    },
  ];
  constructor(
    public authService: AuthService,
    private router: Router,
  ) {}
  ngOnInit(): void {}
  getVisibleItems(): NavItem[] {
    return this.navItems.filter((item) => {
      if (item.adminOnly && !this.authService.isAdmin()) {
        return false;
      }
      return true;
    });
  }
  isActive(route: string): boolean {
    return this.router.url === route || this.router.url.startsWith(route + "/");
  }
}
