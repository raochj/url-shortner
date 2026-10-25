import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable, interval } from "rxjs";
import { switchMap, distinctUntilChanged, map } from "rxjs/operators";
import {
  ScenarioInfo,
  OrchestrationRun,
  DagVisualization,
  ReliabilityMetrics,
  StartRunResponse,
  ScenarioType,
} from "../models/orchestration.model";
import { environment } from "../../../environments/environment";
import { ApiResponse } from "../models/url.model";
@Injectable({ providedIn: "root" })
export class OrchestrationService {
  private readonly baseUrl = `${environment.apiUrl}/orchestration`;
  constructor(private http: HttpClient) {}
  getScenarios(): Observable<ScenarioInfo[]> {
    return this.http
      .get<ApiResponse<ScenarioInfo[]>>(`${this.baseUrl}/scenarios`)
      .pipe(map((response) => response.data));
  }
  startRun(
    scenarioType: ScenarioType,
    parameters: Record<string, unknown>,
  ): Observable<StartRunResponse> {
    return this.http
      .post<ApiResponse<StartRunResponse>>(`${this.baseUrl}/runs`, {
        scenarioType,
        parameters,
      })
      .pipe(map((response) => response.data));
  }
  getRunState(runId: string): Observable<OrchestrationRun> {
    return this.http
      .get<ApiResponse<OrchestrationRun>>(`${this.baseUrl}/runs/${runId}`)
      .pipe(map((response) => response.data));
  }
  getDagVisualization(runId: string): Observable<DagVisualization> {
    return this.http
      .get<ApiResponse<DagVisualization>>(`${this.baseUrl}/runs/${runId}/dag`)
      .pipe(map((response) => response.data));
  }
  getMetrics(runId: string): Observable<ReliabilityMetrics> {
    return this.http
      .get<
        ApiResponse<ReliabilityMetrics>
      >(`${this.baseUrl}/runs/${runId}/metrics`)
      .pipe(map((response) => response.data));
  }
  getTaskLogs(runId: string): Observable<unknown[]> {
    return this.http
      .get<ApiResponse<unknown[]>>(`${this.baseUrl}/runs/${runId}/logs`)
      .pipe(map((response) => response.data));
  }
  submitApproval(
    runId: string,
    taskId: string,
    approved: boolean,
    comment: string,
  ): Observable<unknown> {
    return this.http.post<ApiResponse<void>>(
      `${this.baseUrl}/runs/${runId}/approve/${taskId}`,
      {
        approved,
        comment,
      },
    );
  }
  injectTask(runId: string, task: Partial<unknown>): Observable<unknown> {
    return this.http.post<unknown>(
      `${this.baseUrl}/runs/${runId}/inject`,
      task,
    );
  }
  pollRunState(runId: string): Observable<OrchestrationRun> {
    return interval(2000).pipe(
      switchMap(() => this.getRunState(runId)),
      distinctUntilChanged(
        (prev, curr) =>
          prev.status === curr.status &&
          JSON.stringify(prev.tasks.map((t) => t.status)) ===
            JSON.stringify(curr.tasks.map((t) => t.status)),
      ),
    );
  }
  listRuns(page = 0, size = 10): Observable<unknown> {
    return this.http
      .get<
        ApiResponse<unknown>
      >(`${this.baseUrl}/runs?page=${page}&size=${size}`)
      .pipe(map((response) => response.data));
  }
}
