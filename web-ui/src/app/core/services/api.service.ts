import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConnectionService } from './connection.service';
import { MockApiService } from './mock-api.service';
import {
  StatusResponse, RiskResponse, HealthResponse, AnomalyStatus,
  CircuitBreakerStatus, TokenStatus, ActionResponse, Position,
  TradeRecord, OrdersResponse,
  StrategyVersionInfo, StrategyConfig, ValidationResult,
  RecommendationSignal, SizingRequest, SizingResponse,
  BacktestJobRequest, BacktestReport
} from '../models/api.models';

/**
 * Central API service for all AutoTrader backend calls.
 * Dynamically routes to real HTTP or Mock service based on ConnectionService state.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private connection = inject(ConnectionService);
  private mock = inject(MockApiService);

  private get base() {
    return this.connection.backendUrl();
  }

  private dispatch<T>(mockFn: () => Observable<T>, realFn: () => Observable<T>): Observable<T> {
    return this.connection.isMock() ? mockFn() : realFn();
  }

  // ── Phase-II Endpoints ────────────────────────────────────────

  getSignals(symbol?: string, limit: number = 100): Observable<RecommendationSignal[]> {
    return this.dispatch(
      () => this.mock.getSignals(symbol, limit),
      () => this.http.get<RecommendationSignal[]>(`${this.base}/signals`, { params: { symbol: symbol || '', limit } })
    );
  }

  calculateSizing(request: SizingRequest): Observable<SizingResponse> {
    return this.dispatch(
      () => this.mock.calculateSizing(request),
      () => this.http.post<SizingResponse>(`${this.base}/risk/calculate-sizing`, request)
    );
  }

  // ── Read endpoints ────────────────────────────────────────────
  getStatus(): Observable<StatusResponse> {
    return this.dispatch(
      () => this.mock.getStatus(),
      () => this.http.get<StatusResponse>(`${this.base}/status`)
    );
  }

  getRisk(): Observable<RiskResponse> {
    return this.dispatch(
      () => this.mock.getRisk(),
      () => this.http.get<RiskResponse>(`${this.base}/risk`)
    );
  }

  getHealth(): Observable<HealthResponse> {
    return this.dispatch(
      () => this.mock.getHealth(),
      () => this.http.get<HealthResponse>(`${this.base}/health`)
    );
  }

  getAnomalyStatus(): Observable<AnomalyStatus> {
    return this.dispatch(
      () => this.mock.getAnomalyStatus(),
      () => this.http.get<AnomalyStatus>(`${this.base}/anomaly/status`)
    );
  }

  getCircuitBreakerStatus(): Observable<CircuitBreakerStatus> {
    return this.dispatch(
      () => this.mock.getCircuitBreakerStatus(),
      () => this.http.get<CircuitBreakerStatus>(`${this.base}/circuit-breaker/status`)
    );
  }

  getTokenStatus(): Observable<TokenStatus> {
    return this.dispatch(
      () => this.mock.getTokenStatus(),
      () => this.http.get<TokenStatus>(`${this.base}/token/status`)
    );
  }

  getPositions(): Observable<Position[]> {
    return this.dispatch(
      () => this.mock.getPositions(),
      () => this.http.get<Position[]>(`${this.base}/positions`)
    );
  }

  getTrades(): Observable<TradeRecord[]> {
    return this.dispatch(
      () => this.mock.getTrades(),
      () => this.http.get<TradeRecord[]>(`${this.base}/trades`)
    );
  }

  getOrders(): Observable<OrdersResponse> {
    return this.dispatch(
      () => this.mock.getOrders(),
      () => this.http.get<OrdersResponse>(`${this.base}/orders`)
    );
  }

  // ── Action endpoints ──────────────────────────────────────────
  activateKillSwitch(reason: string = 'Manual via UI'): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.activateKillSwitch(reason),
      () => this.http.post<ActionResponse>(`${this.base}/kill`, null, { params: { reason } })
    );
  }

  emergencyFlatten(reason: string = 'Manual emergency flatten via UI'): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.emergencyFlatten(reason),
      () => this.http.post<ActionResponse>(`${this.base}/emergency-flatten`, null, { params: { reason } })
    );
  }

  acknowledgeAnomaly(): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.acknowledgeAnomaly(),
      () => this.http.post<ActionResponse>(`${this.base}/anomaly/acknowledge`, null)
    );
  }

  resetDay(): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.resetDay(),
      () => this.http.post<ActionResponse>(`${this.base}/reset`, null)
    );
  }

  resetCircuitBreaker(): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.resetCircuitBreaker(),
      () => this.http.post<ActionResponse>(`${this.base}/circuit-breaker/reset`, null)
    );
  }

  refreshToken(): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.refreshToken(),
      () => this.http.post<ActionResponse>(`${this.base}/token/refresh`, null)
    );
  }

  exitPosition(correlationId: string): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.exitPosition(correlationId),
      () => this.http.post<ActionResponse>(`${this.base}/exit/${correlationId}`, null)
    );
  }

  // ── Data endpoints ──────────────────────────────────────────
  getMetrics(): Observable<any> {
    return this.dispatch(
      () => this.mock.getMetrics(),
      () => this.http.get<any>(`${this.base}/metrics`)
    );
  }

  getDimensions(): Observable<any> {
    return this.dispatch(
      () => this.mock.getDimensions(),
      () => this.http.get<any>(`${this.base}/dimensions`)
    );
  }

  getDimensionTable(table: string): Observable<any[]> {
    return this.dispatch(
      () => this.mock.getDimensionTable(table),
      () => this.http.get<any[]>(`${this.base}/dimensions/${table}`)
    );
  }

  searchSymbolMaster(params: Record<string, string>): Observable<any[]> {
    return this.dispatch(
      () => this.mock.searchSymbolMaster(params),
      () => this.http.get<any[]>(`${this.base}/symbol-master`, { params })
    );
  }

  parseSymbol(symbol: string): Observable<any> {
    return this.dispatch(
      () => this.mock.parseSymbol(symbol),
      () => this.http.get<any>(`${this.base}/symbol/parse`, { params: { s: symbol } })
    );
  }

  getSymbolProfile(symbol: string): Observable<any> {
    return this.dispatch(
      () => this.mock.getSymbolProfile(symbol),
      () => this.http.get<any>(`${this.base}/profile/${symbol}`)
    );
  }

  // ── Strategy version endpoints ──────────────────────────────
  getStrategies(): Observable<StrategyVersionInfo[]> {
    return this.dispatch(
      () => this.mock.getStrategies(),
      () => this.http.get<StrategyVersionInfo[]>(`${this.base}/strategies`)
    );
  }

  getStrategy(id: string): Observable<StrategyVersionInfo> {
    return this.dispatch(
      () => this.mock.getStrategy(id),
      () => this.http.get<StrategyVersionInfo>(`${this.base}/strategies/${id}`)
    );
  }

  createDraft(id: string): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.createDraft(id),
      () => this.http.post<ActionResponse>(`${this.base}/strategies/${id}/draft`, null)
    );
  }

  updateDraft(id: string, config: StrategyConfig): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.updateDraft(id, config),
      () => this.http.put<ActionResponse>(`${this.base}/strategies/${id}/draft`, config)
    );
  }

  promoteDraft(id: string): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.promoteDraft(id),
      () => this.http.post<ActionResponse>(`${this.base}/strategies/${id}/promote`, null)
    );
  }

  validateStrategy(config: StrategyConfig): Observable<ValidationResult> {
    return this.dispatch(
      () => this.mock.validateStrategy(config),
      () => this.http.post<ValidationResult>(`${this.base}/strategies/validate`, config)
    );
  }

  toggleStrategy(id: string): Observable<ActionResponse> {
    return this.dispatch(
      () => this.mock.toggleStrategy(id),
      () => this.http.put<ActionResponse>(`${this.base}/strategies/${id}/toggle`, null)
    );
  }

  getStrategyDefaults(): Observable<StrategyConfig> {
    return this.dispatch(
      () => this.mock.getStrategyDefaults(),
      () => this.http.get<StrategyConfig>(`${this.base}/strategies/defaults`)
    );
  }

  // ── Candle Database endpoints ───────────────────────────────
  getCandleDbSymbols(): Observable<string[]> {
    return this.dispatch(
      () => this.mock.getCandleDbSymbols(),
      () => this.http.get<string[]>(`${this.base}/candle-db/symbols`)
    );
  }

  /** Alias for backward compatibility with strategies component */
  getAvailableSymbols(): Observable<string[]> {
    return this.getCandleDbSymbols();
  }

  getCandleDbSummary(): Observable<any[]> {
    return this.dispatch(
      () => this.mock.getCandleDbSummary(),
      () => this.http.get<any[]>(`${this.base}/candle-db/summary`)
    );
  }

  getCandleDbDates(symbol: string): Observable<string[]> {
    return this.dispatch(
      () => this.mock.getCandleDbDates(symbol),
      () => this.http.get<string[]>(`${this.base}/candle-db/${symbol}/dates`)
    );
  }

  searchSymbols(q: string): Observable<any[]> {
    return this.dispatch(
      () => this.mock.searchSymbols(q),
      () => this.http.get<any[]>(`${this.base}/symbol-master`, { params: { q } })
    );
  }

  getDownloads(): Observable<any[]> {
    return this.dispatch(
      () => this.mock.getDownloads(),
      () => this.http.get<any[]>(`${this.base}/candle-db/downloads`)
    );
  }

  startDownload(symbols: string[], from: string, to: string): Observable<any> {
    return this.dispatch(
      () => this.mock.startDownload(symbols, from, to),
      () => this.http.post<any>(`${this.base}/candle-db/download`, { symbols, from, to })
    );
  }

  getDownloadStatus(jobId: string): Observable<any> {
    return this.dispatch(
      () => this.mock.getDownloadStatus(jobId),
      () => this.http.get<any>(`${this.base}/candle-db/download/${jobId}`)
    );
  }

  runBacktest(symbol: string, from: string, to: string): Observable<any> {
    return this.dispatch(
      () => this.mock.runBacktest(symbol, from, to),
      () => this.http.post<any>(`${this.base}/backtest`, { symbol, from, to })
    );
  }

  createBacktestJob(request: BacktestJobRequest): Observable<{jobId: string}> {
    return this.dispatch(
      () => this.mock.createBacktestJob(request),
      () => this.http.post<{jobId: string}>(`${this.base}/backtest/jobs`, request)
    );
  }

  getBacktestJobResults(jobId: string): Observable<BacktestReport[]> {
    return this.dispatch(
      () => this.mock.getBacktestJobResults(jobId),
      () => this.http.get<BacktestReport[]>(`${this.base}/backtest/jobs/${jobId}/results`)
    );
  }
}
