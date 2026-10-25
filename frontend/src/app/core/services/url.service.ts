import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import {
  ShortenUrlRequest,
  UrlResponse,
  PagedResponse,
  ApiResponse,
} from "../models/url.model";
import { environment } from "../../../environments/environment";
@Injectable({ providedIn: "root" })
export class UrlService {
  private readonly baseUrl = `${environment.apiUrl}/urls`;
  constructor(private http: HttpClient) {}
  shorten(req: ShortenUrlRequest): Observable<ApiResponse<UrlResponse>> {
    return this.http.post<ApiResponse<UrlResponse>>(this.baseUrl, req);
  }
  getMyUrls(
    page = 0,
    size = 10,
  ): Observable<ApiResponse<PagedResponse<UrlResponse>>> {
    const params = new HttpParams()
      .set("page", page.toString())
      .set("size", size.toString());
    return this.http.get<ApiResponse<PagedResponse<UrlResponse>>>(
      this.baseUrl,
      { params },
    );
  }
  getUrl(shortCode: string): Observable<ApiResponse<UrlResponse>> {
    return this.http.get<ApiResponse<UrlResponse>>(
      `${this.baseUrl}/${shortCode}`,
    );
  }
  updateUrl(
    shortCode: string,
    req: Partial<ShortenUrlRequest>,
  ): Observable<ApiResponse<UrlResponse>> {
    return this.http.put<ApiResponse<UrlResponse>>(
      `${this.baseUrl}/${shortCode}`,
      req,
    );
  }
  toggleActive(
    shortCode: string,
    isActive: boolean,
  ): Observable<ApiResponse<UrlResponse>> {
    return this.http.put<ApiResponse<UrlResponse>>(
      `${this.baseUrl}/${shortCode}`,
      { isActive },
    );
  }
  deleteUrl(shortCode: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${shortCode}`);
  }
  async copyToClipboard(text: string): Promise<void> {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      // Fallback for non-secure contexts
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
    }
  }
}
