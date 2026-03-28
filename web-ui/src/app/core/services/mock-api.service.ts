import { Injectable } from '@angular/core';
import { Observable, of, delay, throwError } from 'rxjs';
import {
  StatusResponse, RiskResponse, HealthResponse, AnomalyStatus,
  CircuitBreakerStatus, TokenStatus, ActionResponse, Position,
  TradeRecord, OrdersResponse,
  StrategyVersionInfo, StrategyConfig, ValidationResult,
} from '../models/api.models';
import {
  mockStatus, mockRisk, mockHealth, mockAnomaly, mockCircuitBreaker,
  mockToken, mockPositions, mockOrders, mockTrades, mockAction,
  mockCandleDbSymbols, mockCandleDbDates, mockBacktestResult,
  mockDimensionTable, mockSymbolMasterSearch, mockSymbolParse,
  mockSymbolProfile, mockDownloads,
  mockStrategyList, mockStrategyDefaults, mockValidateStrategy, mockAvailableSymbols,
} from './mock-data.generator';

function mockDelay(): number { return 200 + Math.random() * 600; }

/**
 * Mock API service that returns realistic dummy data.
 * Swap in via environment.useMocks = true.
 */
@Injectable({ providedIn: 'root' })
export class MockApiService {

  // ── Read endpoints ────────────────────────────────────────
  getStatus(): Observable<StatusResponse> {
    return of(mockStatus()).pipe(delay(mockDelay()));
  }

  getRisk(): Observable<RiskResponse> {
    return of(mockRisk()).pipe(delay(mockDelay()));
  }

  getHealth(): Observable<HealthResponse> {
    return of(mockHealth()).pipe(delay(mockDelay()));
  }

  getAnomalyStatus(): Observable<AnomalyStatus> {
    return of(mockAnomaly()).pipe(delay(mockDelay()));
  }

  getCircuitBreakerStatus(): Observable<CircuitBreakerStatus> {
    return of(mockCircuitBreaker()).pipe(delay(mockDelay()));
  }

  getTokenStatus(): Observable<TokenStatus> {
    return of(mockToken()).pipe(delay(mockDelay()));
  }

  getPositions(): Observable<Position[]> {
    return of(mockPositions()).pipe(delay(mockDelay()));
  }

  getTrades(): Observable<TradeRecord[]> {
    return of(mockTrades()).pipe(delay(mockDelay()));
  }

  getOrders(): Observable<OrdersResponse> {
    return of(mockOrders()).pipe(delay(mockDelay()));
  }

  // ── Action endpoints ──────────────────────────────────────
  activateKillSwitch(_reason?: string): Observable<ActionResponse> {
    return of(mockAction(90, 'Kill switch activated', 'Kill switch already active')).pipe(delay(mockDelay()));
  }

  emergencyFlatten(_reason?: string): Observable<ActionResponse> {
    return of(mockAction(85, 'Emergency flatten: closed 3 positions', 'No positions to close')).pipe(delay(mockDelay()));
  }

  acknowledgeAnomaly(): Observable<ActionResponse> {
    return of(mockAction(90, 'Anomaly acknowledged and cleared', 'No active anomaly to acknowledge')).pipe(delay(mockDelay()));
  }

  resetDay(): Observable<ActionResponse> {
    return of(mockAction(80, 'Day reset complete', 'Cannot reset in anomaly mode — acknowledge first')).pipe(delay(mockDelay()));
  }

  resetCircuitBreaker(): Observable<ActionResponse> {
    return of(mockAction(90, 'Circuit breaker reset to CLOSED', 'Circuit breaker already CLOSED')).pipe(delay(mockDelay()));
  }

  refreshToken(): Observable<ActionResponse> {
    return of(mockAction(75, 'Token refreshed successfully', 'Broker API unreachable — retry later')).pipe(delay(mockDelay()));
  }

  exitPosition(_correlationId: string): Observable<ActionResponse> {
    return of(mockAction(85, 'Position exit submitted', 'Position not found or already closed')).pipe(delay(mockDelay()));
  }

  // ── Data endpoints ────────────────────────────────────────
  getMetrics(): Observable<any> {
    return of(mockBacktestResult()).pipe(delay(mockDelay()));
  }

  getDimensions(): Observable<any> {
    return of({
      exchanges: mockDimensionTable('exchanges'),
      segments: mockDimensionTable('segments'),
      instrument_types: mockDimensionTable('instrument_types'),
    }).pipe(delay(mockDelay()));
  }

  getDimensionTable(table: string): Observable<any[]> {
    return of(mockDimensionTable(table)).pipe(delay(mockDelay()));
  }

  searchSymbolMaster(params: Record<string, string>): Observable<any[]> {
    return of(mockSymbolMasterSearch(params['q'] || '')).pipe(delay(mockDelay()));
  }

  parseSymbol(symbol: string): Observable<any> {
    return of(mockSymbolParse(symbol)).pipe(delay(mockDelay()));
  }

  getSymbolProfile(_symbol: string): Observable<any> {
    return of(mockSymbolProfile()).pipe(delay(mockDelay()));
  }

  getCandleDbSymbols(): Observable<string[]> {
    return of(mockCandleDbSymbols()).pipe(delay(mockDelay()));
  }

  getCandleDbSummary(): Observable<any[]> {
    return of([
      { symbol: 'NSE:SBIN-EQ', startDate: '2026-03-01', endDate: '2026-03-25', count: 20 },
      { symbol: 'NSE:RELIANCE-EQ', startDate: '2026-03-10', endDate: '2026-03-25', count: 12 },
    ]).pipe(delay(mockDelay()));
  }

  getCandleDbDates(_symbol: string): Observable<string[]> {
    return of(mockCandleDbDates()).pipe(delay(mockDelay()));
  }

  searchSymbols(q: string): Observable<any[]> {
    return of(mockSymbolMasterSearch(q)).pipe(delay(mockDelay()));
  }

  getDownloads(): Observable<any[]> {
    return of(mockDownloads()).pipe(delay(mockDelay()));
  }

  startDownload(_symbols: string[], _from: string, _to: string): Observable<any> {
    if (Math.random() < 0.15) {
      return throwError(() => ({ error: { message: 'Broker API rate limited — try again in 60s' } })).pipe(delay(mockDelay()));
    }
    return of({ jobId: `dl-${Date.now()}`, status: 'RUNNING' }).pipe(delay(mockDelay()));
  }

  getDownloadStatus(_jobId: string): Observable<any> {
    return of({ jobId: _jobId, status: Math.random() < 0.8 ? 'COMPLETED' : 'RUNNING', progress: '75%' }).pipe(delay(mockDelay()));
  }

  runBacktest(_symbol: string, _from: string, _to: string): Observable<any> {
    if (Math.random() < 0.20) {
      return throwError(() => ({ error: { message: 'No M1 data found for the selected date range' } })).pipe(delay(800));
    }
    return of(mockBacktestResult()).pipe(delay(1500 + Math.random() * 1500));
  }

  // ── Strategy version endpoints ────────────────────────────
  getStrategies(): Observable<StrategyVersionInfo[]> {
    return of(mockStrategyList()).pipe(delay(mockDelay()));
  }

  getStrategy(id: string): Observable<StrategyVersionInfo> {
    const all = mockStrategyList();
    const found = all.find(s => s.strategyId === id) || all[0];
    return of(found).pipe(delay(mockDelay()));
  }

  createDraft(_id: string): Observable<ActionResponse> {
    return of(mockAction(90, 'Draft v2 created from active v1', 'Draft already exists')).pipe(delay(mockDelay()));
  }

  updateDraft(_id: string, config: StrategyConfig): Observable<ActionResponse> {
    const validation = mockValidateStrategy(config);
    if (!validation.valid) {
      return of({ success: false, message: `Validation failed: ${validation.errors.join('; ')}` }).pipe(delay(mockDelay()));
    }
    return of(mockAction(90, 'Draft updated successfully', 'Failed to write YAML')).pipe(delay(mockDelay()));
  }

  promoteDraft(_id: string): Observable<ActionResponse> {
    return of(mockAction(85, 'Draft v2 promoted to active. Previous v1 archived.', 'No draft to promote')).pipe(delay(mockDelay()));
  }

  validateStrategy(config: StrategyConfig): Observable<ValidationResult> {
    return of(mockValidateStrategy(config)).pipe(delay(300));
  }

  toggleStrategy(_id: string): Observable<ActionResponse> {
    return of(mockAction(90, 'Strategy toggled', 'Strategy not found')).pipe(delay(mockDelay()));
  }

  getStrategyDefaults(): Observable<StrategyConfig> {
    return of(mockStrategyDefaults()).pipe(delay(mockDelay()));
  }

  getAvailableSymbols(): Observable<string[]> {
    return of(mockAvailableSymbols()).pipe(delay(mockDelay()));
  }
}
