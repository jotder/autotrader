/** Engine status response from GET /api/status */
export interface StatusResponse {
  mode: string;
  running: boolean;
  symbols: string[];
  uptime?: string;
}

/** Risk summary from GET /api/risk */
export interface RiskResponse {
  dailyPnl: number;
  killSwitchActive: boolean;
  dailyProfitLocked: boolean;
  maxDailyLossInr: number;
  maxDailyProfitInr: number;
  initialCapitalInr: number;
}

/** Health response from GET /api/health */
export interface HealthResponse {
  components: Record<string, ComponentHealth>;
}

export interface ComponentHealth {
  status: 'UP' | 'DEGRADED' | 'DOWN';
  details?: Record<string, any>;
}

/** Anomaly status from GET /api/anomaly/status */
export interface AnomalyStatus {
  anomalyMode: boolean;
  reason: string | null;
  triggeredAt: string | null;
  killSwitchActive: boolean;
  detectorTriggered: boolean;
  consecutiveBrokerErrors: number;
}

/** Circuit breaker status from GET /api/circuit-breaker/status */
export interface CircuitBreakerStatus {
  available: boolean;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  consecutiveFailures: number;
  daily429Count: number;
  lastFailureTime: string | null;
  openedAt: string | null;
}

/** Token status from GET /api/token/status */
export interface TokenStatus {
  schedulerRunning: boolean;
  tokenValid: boolean;
  lastRefreshStatus: string;
  lastRefreshTime: string | null;
}

/** Action response for POST endpoints */
export interface ActionResponse {
  success: boolean;
  message: string;
}

/** Open position from GET /api/positions */
export interface Position {
  symbol: string;
  correlationId: string;
  strategyId: string;
  direction: 'BUY' | 'SELL';
  entryPrice: number;
  quantity: number;
  currentStopLoss: number;
  takeProfit: number;
  entryTime: string;
  trailingActivated: boolean;
  highWaterMark: number;
  unrealizedPnl?: number;
}
