import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Router } from "@angular/router";
import { BehaviorSubject, map, Observable, tap } from "rxjs";
import {
  User,
  AuthResponse,
  LoginRequest,
  RegisterRequest,
} from "../models/user.model";
import { ApiResponse } from "../models/url.model";
import { environment } from "../../../environments/environment";
@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly TOKEN_KEY = "schwab_auth_token";
  private readonly USER_KEY = "schwab_user";
  private currentUserSubject = new BehaviorSubject<User | null>(
    this.loadUserFromStorage(),
  );
  public currentUser$ = this.currentUserSubject.asObservable();
  constructor(
    private http: HttpClient,
    private router: Router,
  ) {}
  private loadUserFromStorage(): User | null {
    try {
      const token = localStorage.getItem(this.TOKEN_KEY);
      const userJson = localStorage.getItem(this.USER_KEY);
      if (token && userJson) {
        const user = JSON.parse(userJson) as User;
        return user;
      }
      return null;
    } catch {
      return null;
    }
  }
  login(username: string, password: string): Observable<AuthResponse> {
    const req: LoginRequest = { username, password };
    return this.http
      .post<ApiResponse<AuthResponse>>(`${environment.apiUrl}/auth/login`, req)
      .pipe(
        map((response) => response.data),
        tap((response) => {
          localStorage.setItem(this.TOKEN_KEY, response.accessToken);
          const user: User = {
            id: 0,
            username: response.username,
            email: response.email,
            role: response.role,
          };
          localStorage.setItem(this.USER_KEY, JSON.stringify(user));
          this.currentUserSubject.next(user);
        }),
      );
  }
  register(
    username: string,
    email: string,
    password: string,
  ): Observable<AuthResponse> {
    const req: RegisterRequest = { username, email, password };
    return this.http
      .post<
        ApiResponse<AuthResponse>
      >(`${environment.apiUrl}/auth/register`, req)
      .pipe(
        map((response) => response.data),
        tap((response) => {
          localStorage.setItem(this.TOKEN_KEY, response.accessToken);
          const user: User = {
            id: 0,
            username: response.username,
            email: response.email,
            role: response.role,
          };
          localStorage.setItem(this.USER_KEY, JSON.stringify(user));
          this.currentUserSubject.next(user);
        }),
      );
  }
  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.router.navigate(["/login"]);
  }
  isLoggedIn(): boolean {
    return !!this.getToken();
  }
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }
  getCurrentUserValue(): User | null {
    return this.currentUserSubject.value;
  }
  isAdmin(): boolean {
    const user = this.getCurrentUserValue();
    return user?.role === "ADMIN" || user?.role === "ROLE_ADMIN";
  }
}
