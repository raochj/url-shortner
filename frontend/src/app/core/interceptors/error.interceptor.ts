import { HttpInterceptorFn, HttpErrorResponse } from "@angular/common/http";
import { inject } from "@angular/core";
import { Router } from "@angular/router";
import { throwError } from "rxjs";
import { catchError } from "rxjs/operators";
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Unauthorized — clear session and redirect to login
        localStorage.removeItem("schwab_auth_token");
        localStorage.removeItem("schwab_user");
        router.navigate(["/login"], {
          queryParams: { reason: "session_expired" },
        });
      } else if (error.status === 403) {
        console.error("Access forbidden:", req.url, error.message);
      } else if (error.status >= 500) {
        console.error("Server error:", error.status, error.message);
      } else if (error.status === 0) {
        console.error("Network error — server may be unreachable");
      }
      return throwError(() => error);
    }),
  );
};
