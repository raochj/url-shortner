import { Injectable } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { AnalyticsSummary } from "../models/analytics.model";
import { ApiResponse } from "../models/url.model";
import { environment } from "../../../environments/environment";
@Injectable({ providedIn: "root" })
export class AnalyticsService {
  private readonly baseUrl = `${environment.apiUrl}/analytics`;
  constructor(private http: HttpClient) {}
  getSummary(shortCode: string): Observable<ApiResponse<AnalyticsSummary>> {
    return this.http.get<ApiResponse<AnalyticsSummary>>(
      `${this.baseUrl}/${shortCode}/summary`,
    );
  }
  getTimeSeries(
    shortCode: string,
    from: string,
    to: string,
  ): Observable<ApiResponse<Record<string, number>>> {
    const params = new HttpParams().set("from", from).set("to", to);
    return this.http.get<ApiResponse<Record<string, number>>>(
      `${this.baseUrl}/${shortCode}/timeseries`,
      { params },
    );
  }
  getClickEvents(
    shortCode: string,
    page = 0,
    size = 20,
  ): Observable<ApiResponse<unknown>> {
    const params = new HttpParams()
      .set("page", page.toString())
      .set("size", size.toString());
    return this.http.get<ApiResponse<unknown>>(
      `${this.baseUrl}/${shortCode}/clicks`,
      { params },
    );
  }
}
