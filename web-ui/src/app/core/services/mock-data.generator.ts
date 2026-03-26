import {
  StatusResponse, RiskResponse, HealthResponse, AnomalyStatus,
  CircuitBreakerStatus, TokenStatus, ActionResponse, Position,
  TradeRecord, ManagedOrder, OrdersResponse,
} from '../models/api.models';

// ── Helpers ─────────────────────────────────────────────────

function rand(min: number, max: number): number { return Math.random() * (max - min) + min; }
function randInt(min: number, max: number): number { return Math.floor(rand(min, max + 1)); }
function pick<T>(arr: T[]): T { return arr[randInt(0, arr.length - 1)]; }
function coinFlip(pct: number): boolean { return Math.random() * 100 < pct; }
function uuid(): string { return `${Date.now()}-${randInt(1000, 9999)}`; }
function isoNow(): string { return new Date().toISOString(); }
function isoMinutesAgo(min: number): string { return new Date(Date.now() - min * 60000).toISOString(); }

// ── Symbol Pool ─────────────────────────────────────────────

const SYMBOLS = [
  'NSE:SBIN-EQ', 'NSE:RELIANCE-EQ', 'NSE:INFY-EQ', 'NSE:HDFCBANK-EQ',
  'NSE:TCS-EQ', 'NSE:ICICIBANK-EQ', 'NSE:NIFTY25MARFUT', 'NSE:BANKNIFTY25MARFUT',
];
const STRATEGIES = ['trend-follow-v2', 'mean-revert-v1', 'momentum-v1'];
const MODES: ('BACKTEST' | 'PAPER' | 'LIVE')[] = ['BACKTEST', 'PAPER', 'LIVE'];
const DIRECTIONS: ('BUY' | 'SELL')[] = ['BUY', 'SELL'];
const EXIT_REASONS = ['STOP_LOSS', 'TAKE_PROFIT', 'TRAILING_STOP', 'FORCE_SQUAREOFF', 'MANUAL'];
const HEALTH_COMPONENTS = ['websocket-feed', 'candle-service', 'strategy-evaluator', 'position-monitor', 'risk-manager'];

// ── Status & Health ─────────────────────────────────────────

export function mockStatus(): StatusResponse {
  return {
    mode: coinFlip(85) ? 'PAPER' : 'LIVE',
    running: coinFlip(90),
    symbols: SYMBOLS.slice(0, randInt(3, 7)),
    uptime: `${randInt(1, 8)}h ${randInt(0, 59)}m`,
  };
}

export function mockRisk(): RiskResponse {
  const capital = 500000;
  const maxLoss = 10000;
  const maxProfit = 20000;
  // Weighted: 40% profit, 30% mild loss, 15% caution, 10% danger, 5% locked
  const r = Math.random();
  let pnl: number;
  let profitLocked = false;
  if (r < 0.40) { pnl = rand(500, 5000); }
  else if (r < 0.70) { pnl = rand(-2000, -200); }
  else if (r < 0.85) { pnl = rand(-5000, -3000); }
  else if (r < 0.95) { pnl = rand(-8000, -6000); }
  else { pnl = rand(15000, 20000); profitLocked = true; }

  return {
    dailyPnl: Math.round(pnl),
    killSwitchActive: coinFlip(10),
    dailyProfitLocked: profitLocked,
    maxDailyLossInr: maxLoss,
    maxDailyProfitInr: maxProfit,
    initialCapitalInr: capital,
  };
}

export function mockHealth(): HealthResponse {
  const components: Record<string, { status: 'UP' | 'DEGRADED' | 'DOWN'; details?: any }> = {};
  for (const c of HEALTH_COMPONENTS) {
    const r = Math.random();
    components[c] = {
      status: r < 0.85 ? 'UP' : r < 0.95 ? 'DEGRADED' : 'DOWN',
      details: { lastCheck: isoNow() },
    };
  }
  return { components };
}

export function mockAnomaly(): AnomalyStatus {
  const r = Math.random();
  if (r < 0.05) {
    return {
      anomalyMode: true,
      reason: pick(['Rapid drawdown: 5.2% of capital', 'Broker error cascade: 12 consecutive errors', 'All tick feeds stale > 120s']),
      triggeredAt: isoMinutesAgo(randInt(5, 60)),
      killSwitchActive: true,
      detectorTriggered: true,
      consecutiveBrokerErrors: randInt(10, 15),
    };
  }
  return {
    anomalyMode: false,
    reason: null,
    triggeredAt: null,
    killSwitchActive: coinFlip(10),
    detectorTriggered: false,
    consecutiveBrokerErrors: randInt(0, 3),
  };
}

export function mockCircuitBreaker(): CircuitBreakerStatus {
  const r = Math.random();
  let state: 'CLOSED' | 'OPEN' | 'HALF_OPEN' = 'CLOSED';
  if (r < 0.08) state = 'OPEN';
  else if (r < 0.15) state = 'HALF_OPEN';
  return {
    available: true,
    state,
    consecutiveFailures: state === 'CLOSED' ? 0 : randInt(3, 10),
    daily429Count: randInt(0, 5),
    lastFailureTime: state !== 'CLOSED' ? isoMinutesAgo(randInt(1, 30)) : null,
    openedAt: state === 'OPEN' ? isoMinutesAgo(randInt(1, 15)) : null,
  };
}

export function mockToken(): TokenStatus {
  const valid = coinFlip(90);
  return {
    schedulerRunning: true,
    tokenValid: valid,
    lastRefreshStatus: valid ? 'SUCCESS' : 'FAILED',
    lastRefreshTime: isoMinutesAgo(randInt(10, 300)),
  };
}

// ── Positions ───────────────────────────────────────────────

export function mockPositions(): Position[] {
  const r = Math.random();
  let count = 0;
  if (r < 0.20) count = 0;
  else if (r < 0.60) count = randInt(1, 2);
  else count = randInt(3, 5);

  return Array.from({ length: count }, () => {
    const symbol = pick(SYMBOLS);
    const direction = pick(DIRECTIONS);
    const entry = rand(200, 3000);
    const slDist = rand(5, 30);
    const sl = direction === 'BUY' ? entry - slDist : entry + slDist;
    const tp = direction === 'BUY' ? entry + slDist * 2 : entry - slDist * 2;
    const pnlPct = rand(-3, 5);
    const unrealized = entry * (pnlPct / 100) * randInt(10, 100);
    return {
      symbol,
      correlationId: uuid(),
      strategyId: pick(STRATEGIES),
      direction,
      entryPrice: Math.round(entry * 100) / 100,
      quantity: randInt(10, 200),
      currentStopLoss: Math.round(sl * 100) / 100,
      takeProfit: Math.round(tp * 100) / 100,
      entryTime: isoMinutesAgo(randInt(5, 240)),
      trailingActivated: coinFlip(30),
      highWaterMark: Math.round((entry + Math.abs(entry * pnlPct / 100)) * 100) / 100,
      unrealizedPnl: Math.round(unrealized),
    };
  });
}

// ── Orders ──────────────────────────────────────────────────

export function mockOrders(): OrdersResponse {
  const activeCount = coinFlip(50) ? 0 : randInt(1, 3);
  const active: ManagedOrder[] = Array.from({ length: activeCount }, () => ({
    clientOrderId: `ORD-${uuid()}`,
    correlationId: uuid(),
    symbol: pick(SYMBOLS),
    side: pick(['BUY', 'SELL']),
    state: pick(['SUBMITTED', 'PENDING_CONFIRMATION']),
    submittedAt: isoMinutesAgo(randInt(1, 10)),
    filledAt: null,
    fillPrice: null,
    quantity: randInt(10, 100),
  }));
  const completed: ManagedOrder[] = Array.from({ length: randInt(3, 8) }, () => {
    const price = rand(200, 3000);
    return {
      clientOrderId: `ORD-${uuid()}`,
      correlationId: uuid(),
      symbol: pick(SYMBOLS),
      side: pick(['BUY', 'SELL']),
      state: 'FILLED',
      submittedAt: isoMinutesAgo(randInt(10, 360)),
      filledAt: isoMinutesAgo(randInt(5, 350)),
      fillPrice: Math.round(price * 100) / 100,
      quantity: randInt(10, 100),
    };
  });
  return { activeCount, active, completed };
}

// ── Trades ──────────────────────────────────────────────────

let _cachedTrades: TradeRecord[] | null = null;

export function mockTrades(forceNew = false): TradeRecord[] {
  if (_cachedTrades && !forceNew) return _cachedTrades;
  const count = randInt(30, 80);
  _cachedTrades = Array.from({ length: count }, (_, i) => {
    const symbol = pick(SYMBOLS);
    const strategy = pick(STRATEGIES);
    const mode = pick([...Array(6).fill('PAPER'), ...Array(3).fill('BACKTEST'), 'LIVE']) as any;
    const direction = pick(DIRECTIONS);
    const entry = rand(200, 3000);
    const isWinner = coinFlip(48);
    const slDist = rand(5, 30);
    const pnlAbs = isWinner ? rand(100, 3000) : rand(-2000, -50);
    const qty = randInt(10, 200);
    const exitPrice = direction === 'BUY' ? entry + pnlAbs / qty : entry - pnlAbs / qty;
    const holdMin = randInt(5, 240);
    const exitReason = pick(
      isWinner
        ? ['TAKE_PROFIT', 'TAKE_PROFIT', 'TRAILING_STOP', 'TRAILING_STOP', 'MANUAL']
        : ['STOP_LOSS', 'STOP_LOSS', 'STOP_LOSS', 'FORCE_SQUAREOFF', 'MANUAL']
    );
    const entryTime = new Date(Date.now() - randInt(0, 7 * 86400000) - holdMin * 60000);
    const exitTime = new Date(entryTime.getTime() + holdMin * 60000);

    return {
      correlationId: `TRD-${i}-${uuid()}`,
      symbol,
      strategyId: strategy,
      mode,
      direction,
      entryPrice: Math.round(entry * 100) / 100,
      exitPrice: Math.round(exitPrice * 100) / 100,
      quantity: qty,
      pnl: Math.round(pnlAbs),
      pnlPct: Math.round((pnlAbs / (entry * qty)) * 10000) / 100,
      rMultipleAchieved: Math.round((pnlAbs / (slDist * qty)) * 100) / 100,
      holdDuration: `${holdMin}m`,
      entryTime: entryTime.toISOString(),
      exitTime: exitTime.toISOString(),
      exitReason,
      status: 'CLOSED' as const,
      entryConfidence: Math.round(rand(0.70, 0.95) * 100) / 100,
      maxAdverseExcursion: Math.round(rand(50, 1500)),
      maxFavorableExcursion: Math.round(rand(100, 3000)),
    };
  });
  return _cachedTrades;
}

// ── Action Responses ────────────────────────────────────────

export function mockAction(successRate: number, successMsg: string, failMsg: string): ActionResponse {
  if (coinFlip(successRate)) return { success: true, message: successMsg };
  return { success: false, message: failMsg };
}

// ── Backtest ────────────────────────────────────────────────

export function mockCandleDbSymbols(): string[] {
  return SYMBOLS.slice(0, randInt(3, 5));
}

export function mockCandleDbDates(): string[] {
  const dates: string[] = [];
  const start = new Date(2026, 2, 1); // March 1, 2026
  for (let i = 0; i < randInt(15, 25); i++) {
    const d = new Date(start.getTime() + i * 86400000);
    if (d.getDay() > 0 && d.getDay() < 6) { // weekdays only
      dates.push(d.toISOString().slice(0, 10));
    }
  }
  return dates;
}

export function mockBacktestResult(): any {
  const total = randInt(25, 60);
  const wins = Math.round(total * rand(0.40, 0.55));
  const losses = total - wins;
  const grossProfit = rand(5000, 25000);
  const grossLoss = rand(3000, 20000);
  const totalPnl = grossProfit - grossLoss;

  const curve: number[] = [];
  let running = 0;
  for (let i = 0; i < total; i++) {
    running += rand(-500, 700);
    curve.push(Math.round(running));
  }

  return {
    overall: {
      total, wins, losses,
      winRate: wins / total,
      totalPnl: Math.round(totalPnl),
      grossProfit: Math.round(grossProfit),
      grossLoss: Math.round(grossLoss),
      profitFactor: Math.round((grossProfit / grossLoss) * 100) / 100,
      expectancy: Math.round(totalPnl / total),
      sharpe: Math.round(rand(0.5, 2.0) * 100) / 100,
      maxDrawdown: Math.round(rand(0.05, 0.20) * 100) / 100,
      avgR: Math.round(rand(0.5, 1.5) * 100) / 100,
      avgWin: Math.round(grossProfit / wins),
      avgLoss: Math.round(grossLoss / losses),
      avgHoldMinutes: Math.round(rand(15, 120)),
      maxConsecLosses: randInt(2, 6),
    },
    byStrategy: Object.fromEntries(STRATEGIES.slice(0, 2).map(s => [s, {
      total: randInt(10, 30), wins: randInt(5, 15), losses: randInt(3, 15),
      winRate: rand(0.35, 0.60),
      totalPnl: Math.round(rand(-3000, 8000)),
      profitFactor: Math.round(rand(0.8, 2.5) * 100) / 100,
      sharpe: Math.round(rand(0.3, 2.0) * 100) / 100,
      avgR: Math.round(rand(0.3, 1.8) * 100) / 100,
    }])),
    bySymbol: Object.fromEntries(SYMBOLS.slice(0, 3).map(s => [s, {
      total: randInt(5, 20), wins: randInt(3, 10), losses: randInt(2, 10),
      winRate: rand(0.35, 0.60),
      totalPnl: Math.round(rand(-2000, 5000)),
      profitFactor: Math.round(rand(0.7, 2.0) * 100) / 100,
      avgR: Math.round(rand(0.3, 1.5) * 100) / 100,
    }])),
    equityCurve: curve,
    suggestions: [
      'Win rate below 45% — consider raising minimum confidence threshold to 0.88',
      'Profit factor below 1.5 — review SL placement, may be too tight',
      'Max consecutive losses = 5 — strategy-level suspension working as expected',
      'Trailing stop exits outnumber TP exits — trailing logic is capturing runners well',
      'Average hold time 45min — consider extending for trend-following strategies',
    ].slice(0, randInt(3, 5)),
  };
}

// ── Config / Dimensions ─────────────────────────────────────

export function mockDimensionTable(table: string): any[] {
  const data: Record<string, any[]> = {
    exchanges: [{ code: 10, name: 'NSE' }, { code: 11, name: 'MCX' }, { code: 12, name: 'BSE' }],
    segments: [{ code: 10, name: 'CM' }, { code: 11, name: 'FO' }, { code: 12, name: 'CD' }, { code: 20, name: 'COM' }],
    instrument_types: [
      { code: 0, name: 'EQUITY', segment: 'CM' },
      { code: 1, name: 'EQUITY_FUTURE', segment: 'FO' },
      { code: 2, name: 'EQUITY_OPTION', segment: 'FO' },
      { code: 3, name: 'CURRENCY_FUTURE', segment: 'CD' },
      { code: 4, name: 'COMMODITY_FUTURE', segment: 'COM' },
    ],
    order_types: [{ code: 1, name: 'LIMIT' }, { code: 2, name: 'MARKET' }, { code: 3, name: 'STOP' }, { code: 4, name: 'STOP_LIMIT' }],
    order_sides: [{ code: 1, name: 'BUY' }, { code: -1, name: 'SELL' }],
    order_status: [{ code: 1, name: 'PENDING' }, { code: 2, name: 'TRADED' }, { code: 5, name: 'REJECTED' }, { code: 6, name: 'CANCELLED' }],
    product_types: [{ code: 'INTRADAY', name: 'Intraday' }, { code: 'MARGIN', name: 'Margin' }, { code: 'CNC', name: 'Delivery' }],
    holding_types: [{ code: 'T1', name: 'T+1 Holdings' }, { code: 'HLD', name: 'Holdings' }],
    position_sides: [{ code: 1, name: 'LONG' }, { code: -1, name: 'SHORT' }, { code: 0, name: 'CLOSED' }],
  };
  return data[table] || [];
}

export function mockSymbolMasterSearch(query: string): any[] {
  const all = [
    { symbolTicker: 'NSE:SBIN-EQ', exchange: 'NSE', segment: 'CM', minLotSize: 1, tickSize: 0.05 },
    { symbolTicker: 'NSE:RELIANCE-EQ', exchange: 'NSE', segment: 'CM', minLotSize: 1, tickSize: 0.05 },
    { symbolTicker: 'NSE:INFY-EQ', exchange: 'NSE', segment: 'CM', minLotSize: 1, tickSize: 0.05 },
    { symbolTicker: 'NSE:HDFCBANK-EQ', exchange: 'NSE', segment: 'CM', minLotSize: 1, tickSize: 0.05 },
    { symbolTicker: 'NSE:TCS-EQ', exchange: 'NSE', segment: 'CM', minLotSize: 1, tickSize: 0.05 },
    { symbolTicker: 'NSE:ICICIBANK-EQ', exchange: 'NSE', segment: 'CM', minLotSize: 1, tickSize: 0.05 },
    { symbolTicker: 'NSE:NIFTY25MARFUT', exchange: 'NSE', segment: 'FO', minLotSize: 75, tickSize: 0.05 },
    { symbolTicker: 'NSE:BANKNIFTY25MARFUT', exchange: 'NSE', segment: 'FO', minLotSize: 30, tickSize: 0.05 },
    { symbolTicker: 'NSE:NIFTY25MAR23000CE', exchange: 'NSE', segment: 'FO', minLotSize: 75, tickSize: 0.05 },
    { symbolTicker: 'NSE:SBIN25MARFUT', exchange: 'NSE', segment: 'FO', minLotSize: 1500, tickSize: 0.05 },
  ];
  const q = query.toLowerCase();
  return all.filter(s => s.symbolTicker.toLowerCase().includes(q));
}

export function mockSymbolParse(symbol: string): any {
  const isFut = symbol.includes('FUT');
  const isOpt = symbol.match(/\d{2}(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\d+[CP]E/);
  return {
    exchange: symbol.split(':')[0] || 'NSE',
    segment: isFut || isOpt ? 'FO' : 'CM',
    symbolType: isFut ? 'EQUITY_FUTURE' : isOpt ? 'EQUITY_OPTION' : 'EQUITY',
    underlying: isFut || isOpt ? symbol.replace(/\d.*/, '') : null,
    expiry: isFut || isOpt ? '2026-03-27' : null,
    strikePrice: isOpt ? 23000 : null,
    optionType: isOpt ? 'CE' : null,
  };
}

export function mockSymbolProfile(): any | null {
  if (coinFlip(40)) return null; // 40% no profile
  return {
    avgDailyRange: `₹${randInt(10, 80)}`,
    avgVolume: `${randInt(100, 5000)}K`,
    volatility: `${rand(1, 5).toFixed(1)}%`,
    trendStrength: pick(['STRONG', 'MODERATE', 'WEAK']),
    bestSession: pick(['Morning (09:30-11:30)', 'Afternoon (13:30-15:00)']),
    avgSpread: `₹${rand(0.05, 0.50).toFixed(2)}`,
    daysAnalyzed: randInt(10, 25),
  };
}

export function mockDownloads(): any[] {
  return [
    { jobId: 'dl-001', status: pick(['RUNNING', 'COMPLETED', 'COMPLETED']), symbols: ['NSE:SBIN-EQ'], progress: '100%', startTime: isoMinutesAgo(30) },
    { jobId: 'dl-002', status: pick(['RUNNING', 'COMPLETED', 'FAILED']), symbols: ['NSE:RELIANCE-EQ', 'NSE:INFY-EQ'], progress: coinFlip(50) ? '67%' : '100%', startTime: isoMinutesAgo(15) },
  ];
}
