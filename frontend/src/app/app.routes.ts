import { Routes } from "@angular/router";
import { authGuard } from "./core/guards/auth.guard";
export const routes: Routes = [
  {
    path: "",
    redirectTo: "/dashboard",
    pathMatch: "full",
  },
  {
    path: "login",
    loadComponent: () =>
      import("./features/auth/login/login.component").then(
        (m) => m.LoginComponent,
      ),
  },
  {
    path: "register",
    loadComponent: () =>
      import("./features/auth/register/register.component").then(
        (m) => m.RegisterComponent,
      ),
  },
  {
    path: "dashboard",
    canActivate: [authGuard],
    loadComponent: () =>
      import("./features/dashboard/dashboard.component").then(
        (m) => m.DashboardComponent,
      ),
  },
  {
    path: "urls",
    canActivate: [authGuard],
    loadComponent: () =>
      import("./features/urls/url-list/url-list.component").then(
        (m) => m.UrlListComponent,
      ),
  },
  {
    path: "urls/new",
    canActivate: [authGuard],
    loadComponent: () =>
      import("./features/urls/url-create/url-create.component").then(
        (m) => m.UrlCreateComponent,
      ),
  },
  {
    path: "analytics/:shortCode",
    canActivate: [authGuard],
    loadComponent: () =>
      import(
        "./features/analytics/analytics-dashboard/analytics-dashboard.component"
      ).then((m) => m.AnalyticsDashboardComponent),
  },
  {
    path: "orchestration",
    canActivate: [authGuard],
    loadComponent: () =>
      import(
        "./features/orchestration/orchestration-panel/orchestration-panel.component"
      ).then((m) => m.OrchestrationPanelComponent),
  },
  {
    path: "**",
    redirectTo: "/dashboard",
  },
];
