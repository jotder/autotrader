import { Injectable, signal, computed } from '@angular/core';
import {
  StatusResponse, RiskResponse, HealthResponse, AnomalyStatus,
  CircuitBreakerStatus, TokenStatus, Position, TradeRecord, ManagedOrder
} from '../models/api.models';

/**
 * Reactive global state for the trading application using Angular Signals.
 * Centralizes all polling-derived data to ensure consistency across components.
 */
@Injectable({ providedIn: 'root' })
export class GlobalStateService {
  // ── Engine & System State ─────────────────────────────────────
  readonly status = signal<StatusResponse | null>(null);
  readonly health = signal<HealthResponse | null>(null);
  readonly anomaly = signal<AnomalyStatus | null>(null);
  readonly circuitBreaker = signal<CircuitBreakerStatus | null>(null);
  readonly token = signal<TokenStatus | null>(null);

  // ── Trading & Risk State ──────────────────────────────────────
  readonly risk = signal<RiskResponse | null>(null);
  readonly positions = signal<Position[]>([]);
  readonly activeOrders = signal<ManagedOrder[]>([]);
  readonly recentTrades = signal<TradeRecord[]>([]);

  // ── Computed States (Convenience) ─────────────────────────────
  readonly dailyPnl = computed(() => this.risk()?.dailyPnl ?? 0);
  readonly isKillSwitchActive = computed(() => this.risk()?.killSwitchActive ?? false);
  readonly isAnomalyMode = computed(() => this.anomaly()?.anomalyMode ?? false);
  readonly isCircuitOpen = computed(() => this.circuitBreaker()?.state === 'OPEN');
  readonly isRunning = computed(() => this.status()?.running ?? false);
  readonly executionMode = computed(() => this.status()?.mode ?? 'UNKNOWN');

  // ── Last Updated Tracking ────────────────────────────────────
  readonly now = signal<number>(Date.now());
  readonly lastUpdated = signal<Date>(new Date());
  readonly isStale = computed(() => {
    return (this.now() - this.lastUpdated().getTime()) > 15000;
  });

  /**
   * Updates the global state with new data.
   * Called primarily by the PollingService.
   */
  updateState(updates: Partial<{
    status: StatusResponse;
    health: HealthResponse;
    anomaly: AnomalyStatus;
    circuitBreaker: CircuitBreakerStatus;
    token: TokenStatus;
    risk: RiskResponse;
    positions: Position[];
    activeOrders: ManagedOrder[];
    recentTrades: TradeRecord[];
  }>) {
    if (updates.status) this.status.set(updates.status);
    if (updates.health) this.health.set(updates.health);
    if (updates.anomaly) this.anomaly.set(updates.anomaly);
    if (updates.circuitBreaker) this.circuitBreaker.set(updates.circuitBreaker);
    if (updates.token) this.token.set(updates.token);
    if (updates.risk) this.risk.set(updates.risk);
    if (updates.positions) this.positions.set(updates.positions);
    if (updates.activeOrders) this.activeOrders.set(updates.activeOrders);
    if (updates.recentTrades) this.recentTrades.set(updates.recentTrades);

    this.lastUpdated.set(new Date());
  }
}
