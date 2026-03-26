import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  StatusResponse,
  RiskResponse,
  HealthResponse,
  AnomalyStatus,
  CircuitBreakerStatus,
  TokenStatus,
  ActionResponse,
  Position,
  TradeRecord,
  OrdersResponse,
} from '../models/api.models';

/**
 * Central API service for all AutoTrader backend calls.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  // ── Read endpoints ────────────────────────────────────────────
  getStatus(): Observable<StatusResponse> {
    return this.http.get<StatusResponse>(`${this.base}/status`);
  }

  getRisk(): Observable<RiskResponse> {
    return this.http.get<RiskResponse>(`${this.base}/risk`);
  }

  getHealth(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>(`${this.base}/health`);
  }

  getAnomalyStatus(): Observable<AnomalyStatus> {
    return this.http.get<AnomalyStatus>(`${this.base}/anomaly/status`);
  }

  getCircuitBreakerStatus(): Observable<CircuitBreakerStatus> {
    return this.http.get<CircuitBreakerStatus>(`${this.base}/circuit-breaker/status`);
  }

  getTokenStatus(): Observable<TokenStatus> {
    return this.http.get<TokenStatus>(`${this.base}/token/status`);
  }

  getPositions(): Observable<Position[]> {
    return this.http.get<Position[]>(`${this.base}/positions`);
  }

  getTrades(): Observable<TradeRecord[]> {
    return this.http.get<TradeRecord[]>(`${this.base}/trades`);
  }

  getOrders(): Observable<OrdersResponse> {
    return this.http.get<OrdersResponse>(`${this.base}/orders`);
  }

  // ── Action endpoints ──────────────────────────────────────────
  activateKillSwitch(reason: string = 'Manual via UI'): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/kill`, null, {
      params: { reason },
    });
  }

  emergencyFlatten(reason: string = 'Manual emergency flatten via UI'): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/emergency-flatten`, null, {
      params: { reason },
    });
  }

  acknowledgeAnomaly(): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/anomaly/acknowledge`, null);
  }

  resetDay(): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/reset`, null);
  }

  resetCircuitBreaker(): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/circuit-breaker/reset`, null);
  }

  refreshToken(): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/token/refresh`, null);
  }

  exitPosition(correlationId: string): Observable<ActionResponse> {
    return this.http.post<ActionResponse>(`${this.base}/exit/${correlationId}`, null);
  }
}
