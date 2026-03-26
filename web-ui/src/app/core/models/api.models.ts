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

/** Closed trade from GET /api/trades */
export interface TradeRecord {
  correlationId: string;
  symbol: string;
  strategyId: string;
  mode: 'BACKTEST' | 'PAPER' | 'LIVE';
  direction: 'BUY' | 'SELL';
  entryPrice: number;
  exitPrice: number | null;
  quantity: number;
  pnl: number | null;
  pnlPct: number | null;
  rMultipleAchieved: number | null;
  holdDuration: string | null;
  entryTime: string;
  exitTime: string | null;
  exitReason: string | null;
  status: 'OPEN' | 'CLOSED' | 'CANCELLED';
  entryConfidence: number;
  maxAdverseExcursion: number;
  maxFavorableExcursion: number;
}

/** Order from GET /api/orders */
export interface ManagedOrder {
  clientOrderId: string;
  correlationId: string;
  symbol: string;
  side: string;
  state: string;
  submittedAt: string;
  filledAt: string | null;
  fillPrice: number | null;
  quantity: number;
}

/** Orders response from GET /api/orders */
export interface OrdersResponse {
  activeCount: number;
  active: ManagedOrder[];
  completed: ManagedOrder[];
}

// ── Strategy Configuration & Versioning ───────────────────

export interface StrategyConfig {
  enabled: boolean;
  symbols: string[];
  timeframe: string;
  cooldownMinutes: number;
  maxTradesPerDay: number;
  activeHours: { start: string; end: string };
  indicators: {
    emaFast: number;
    emaSlow: number;
    rsiPeriod: number;
    atrPeriod: number;
    relVolPeriod: number;
    minCandles: number;
  };
  entry: {
    minConfidence: number;
    relVolThreshold: number;
    trendStrength: string;
  };
  risk: {
    riskPerTradePct: number;
    slAtrMultiplier: number;
    tpRMultiple: number;
    trailingActivationPct: number;
    trailingStepPct: number;
    maxExposurePct: number;
    maxQty: number;
    maxConsecutiveLosses: number;
  };
  order: {
    type: string;
    slippageTolerance: number;
    productType: string;
  };
}

export interface VersionEntry {
  version: number;
  state: 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  note: string;
}

export interface StrategyVersionInfo {
  strategyId: string;
  activeVersion: number;
  latestVersion: number;
  enabled: boolean;
  status: 'ACTIVE' | 'HAS_DRAFT' | 'DISABLED';
  history: VersionEntry[];
  config: StrategyConfig;        // active version config
  draftConfig?: StrategyConfig;  // draft config if exists
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}
