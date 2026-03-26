import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';

interface KnowledgeArticle {
  id: string;
  title: string;
  category: string;
  content: string;
}

@Component({
  selector: 'at-knowledge',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1>Knowledge Base</h1>
      <input type="text" [(ngModel)]="searchQuery" placeholder="Search articles…" class="search-input" />
    </div>

    <!-- Category tabs -->
    <div class="tabs">
      @for (cat of categories; track cat) {
        <button class="tab" [class.active]="activeCategory === cat" (click)="activeCategory = cat">{{ cat }}</button>
      }
    </div>

    <div class="kb-layout">
      <!-- Article list -->
      <div class="article-list">
        @for (a of filteredArticles; track a.id) {
          <div class="article-item" [class.active]="selectedArticle?.id === a.id" (click)="selectedArticle = a">
            <mat-icon>{{ categoryIcon(a.category) }}</mat-icon>
            <span>{{ a.title }}</span>
          </div>
        }
        @if (filteredArticles.length === 0) {
          <div class="empty">No articles match your search</div>
        }
      </div>

      <!-- Article content -->
      <div class="article-content at-card">
        @if (selectedArticle) {
          <div class="article-header">
            <span class="article-category">{{ selectedArticle.category }}</span>
            <h2>{{ selectedArticle.title }}</h2>
          </div>
          <div class="article-body">
            @for (line of selectedArticle.content.split('\\n'); track $index) {
              @if (line.startsWith('## ')) {
                <h3>{{ line.substring(3) }}</h3>
              } @else if (line.startsWith('- ')) {
                <div class="kb-bullet"><span class="bullet">•</span>{{ line.substring(2) }}</div>
              } @else if (line.startsWith('**') && line.endsWith('**')) {
                <div class="kb-bold">{{ line.slice(2, -2) }}</div>
              } @else if (line.trim()) {
                <p>{{ line }}</p>
              }
            }
          </div>
        } @else {
          <div class="empty-article">
            <mat-icon>menu_book</mat-icon>
            <p>Select an article to read</p>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .search-input { padding: 6px 12px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 4px; color: var(--text-primary); font-size: 12px; width: 250px; }

    .tabs { display: flex; gap: 2px; margin-bottom: 16px; border-bottom: 1px solid var(--border); }
    .tab { background: transparent; border: none; color: var(--text-secondary); padding: 8px 16px; cursor: pointer; font-size: 13px; border-bottom: 2px solid transparent; }
    .tab:hover { color: var(--text-primary); }
    .tab.active { color: var(--accent); border-bottom-color: var(--accent); }

    .kb-layout { display: grid; grid-template-columns: 260px 1fr; gap: 16px; }

    .article-list { display: flex; flex-direction: column; gap: 2px; }
    .article-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; color: var(--text-secondary); }
    .article-item:hover { background: var(--bg-hover); color: var(--text-primary); }
    .article-item.active { background: var(--bg-hover); color: var(--accent); border-left: 3px solid var(--accent); }
    .article-item mat-icon { font-size: 18px; color: var(--text-muted); }
    .article-item.active mat-icon { color: var(--accent); }

    .article-content { padding: 20px; min-height: 400px; }
    .article-header { margin-bottom: 16px; border-bottom: 1px solid var(--border); padding-bottom: 12px; }
    .article-category { font-size: 10px; text-transform: uppercase; letter-spacing: 1px; color: var(--accent); }
    .article-header h2 { margin: 4px 0 0; font-size: 18px; font-weight: 500; }
    .article-body p { font-size: 13px; line-height: 1.6; color: var(--text-secondary); margin: 8px 0; }
    .article-body h3 { font-size: 14px; font-weight: 600; margin: 16px 0 8px; color: var(--text-primary); }
    .kb-bullet { display: flex; gap: 6px; font-size: 13px; line-height: 1.6; color: var(--text-secondary); padding: 2px 0; }
    .bullet { color: var(--accent); }
    .kb-bold { font-weight: 600; font-size: 13px; color: var(--text-primary); margin: 8px 0; }

    .empty-article { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; color: var(--text-muted); gap: 12px; }
    .empty-article mat-icon { font-size: 48px; opacity: 0.3; }
    .empty { color: var(--text-muted); font-size: 13px; padding: 16px; text-align: center; }
  `],
})
export class KnowledgeComponent {
  searchQuery = '';
  activeCategory = 'All';
  selectedArticle: KnowledgeArticle | null = null;
  categories = ['All', 'Strategy Guides', 'Risk Management', 'Best Practices'];

  articles: KnowledgeArticle[] = [
    {
      id: 'trend-following', title: 'Trend Following', category: 'Strategy Guides',
      content: `## Overview\nTrend following strategies aim to capture sustained directional moves. The system uses EMA crossovers, RSI momentum, and ATR-based stops.\n\n## EMA Crossover Logic\n- Fast EMA (default: 20) crossing above Slow EMA (default: 50) = bullish signal\n- Fast EMA crossing below Slow EMA = bearish signal\n- Both EMAs must align with the higher timeframe (M15/H1) for confirmation\n\n## RSI Filter\n- RSI > 50 confirms bullish momentum\n- RSI < 50 confirms bearish momentum\n- Extreme RSI (>80 or <20) may indicate exhaustion — the system avoids entries at extremes\n\n## ATR-Based Stops\n- Stop-loss: Entry price ± (ATR × SL multiplier), default 2.0×\n- Take-profit: Entry price ± (SL distance × TP R-multiple), default 2.0R\n- Trailing stop activates after profit exceeds activation threshold`,
    },
    {
      id: 'compound-signal', title: 'Compound Signal Filter', category: 'Strategy Guides',
      content: `## Multi-Timeframe Alignment\nThe system requires signal alignment across M5 + M15 timeframes, with optional H1 boost.\n\n## Confidence Scoring\n- Each indicator contributes to a confidence score (0.0 to 1.0)\n- Minimum confidence threshold: configurable per strategy (default 0.85)\n- Higher confidence = stronger signal agreement\n\n## Signal Components\n- EMA trend direction (M5, M15)\n- RSI momentum confirmation\n- Relative volume (above threshold = more conviction)\n- ATR volatility filter (sufficient range for profit)\n\n## Cooldown\n- After a trade closes, the system waits a configurable cooldown (default 25 min)\n- Prevents overtrading on choppy price action`,
    },
    {
      id: 'entry-exit', title: 'Entry & Exit Signals', category: 'Strategy Guides',
      content: `## Entry Conditions\n- M5 signal aligned with M15 direction\n- Confidence above minimum threshold\n- Relative volume above threshold (default 1.2×)\n- Within active trading hours\n- No cooldown active for the symbol\n- Risk check passed (capital available, not in kill switch)\n\n## Exit Conditions\n- Stop-loss hit (ATR-based)\n- Take-profit hit (R-multiple target)\n- Trailing stop triggered (after activation threshold)\n- Market close force square-off (15:15 IST)\n- Manual exit via UI\n- Anomaly flatten (emergency)`,
    },
    {
      id: 'position-sizing', title: 'Position Sizing', category: 'Strategy Guides',
      content: `## Risk-Based Sizing\nPosition size = Risk Budget / Risk Per Unit\n- Risk Budget = Capital × Risk Per Trade % (default 2%)\n- Risk Per Unit = |Entry - Stop Loss|\n- Final Qty = min(calculated, Max Qty Per Order)\n\n## F&O Lot Sizing\n- For derivatives, quantity is rounded DOWN to nearest lot size\n- Minimum: 1 lot enforced\n- Lot sizes come from symbol master data\n\n## Exposure Limits\n- Max exposure per strategy: configurable\n- Max total exposure: sum of all open positions vs capital`,
    },
    {
      id: 'risk-per-trade', title: 'Risk Per Trade', category: 'Risk Management',
      content: `## The 2% Rule\nNever risk more than 2% of capital on a single trade. This ensures that even a streak of losses won't significantly deplete the account.\n\n## How RiskManager Sizes Positions\n1. Calculate risk budget: Capital × 2% = ₹2,000 (on ₹100K account)\n2. Calculate risk per unit: |Entry - SL| = ₹5.00\n3. Max quantity: ₹2,000 / ₹5.00 = 400 shares\n4. Cap at max qty per order if needed\n\n## Adjusting Risk\n- Conservative: reduce to 1% during drawdowns\n- Aggressive: increase to 3% during winning streaks (use with caution)\n- Per-strategy overrides via YAML config`,
    },
    {
      id: 'stop-loss', title: 'Stop-Loss Strategies', category: 'Risk Management',
      content: `## ATR-Based Stops\n- SL = Entry ± (ATR × multiplier)\n- Default multiplier: 2.0 (adjustable per strategy)\n- ATR period: 14 candles (adjustable)\n\n## Trailing Stops\n- Activation: when unrealized profit exceeds activation % (default 1.5%)\n- Step: SL moves by step % (default 0.5%) of price\n- Direction: monotonic — SL only moves in favorable direction\n- High water mark tracks best price since entry\n\n## Time-Based\n- Force square-off at 15:15 IST (market close)\n- No new trades after 15:00 IST (configurable)`,
    },
    {
      id: 'drawdown', title: 'Drawdown Protection', category: 'Risk Management',
      content: `## Daily Loss Limit\n- Max daily loss configured in .env (default: based on capital)\n- Kill switch activates automatically when breached\n- All new entries blocked; existing positions continue with SL/TP\n\n## Anomaly Detection\n- Rapid drawdown: > 5% of capital triggers emergency flatten\n- Consecutive broker errors: > 10 triggers flatten\n- Feed staleness: > 120s during market hours triggers flatten\n- Heap exhaustion: > 95% triggers flatten\n\n## Recovery\n1. Anomaly mode requires manual acknowledgement\n2. Review cause before resuming\n3. Reset day counters only after confirmation`,
    },
    {
      id: 'portfolio', title: 'Portfolio Management', category: 'Risk Management',
      content: `## Diversification\n- Trade multiple symbols to reduce single-stock risk\n- Per-strategy risk overrides allow different sizing per approach\n- Monitor per-symbol and per-strategy PnL in Transaction Center\n\n## Kill Switch Cascade\n- Daily loss limit → kill switch → block entries\n- Anomaly detected → flatten all → manual restart\n- Circuit breaker OPEN → pause broker calls → retry on HALF_OPEN\n\n## Position Reconciliation\n- Startup: compare broker positions vs engine state\n- Adopt orphaned positions, remove stale entries\n- LIVE mode only — ensures consistency after restart`,
    },
    {
      id: 'strategy-selection', title: 'Strategy Selection', category: 'Best Practices',
      content: `## Choosing a Strategy\n- Start with trend following — simplest, most robust\n- Backtest on at least 30 trading days of M1 data\n- Win rate > 40% AND profit factor > 1.5 = viable\n- Sharpe ratio > 1.0 indicates good risk-adjusted returns\n\n## Iteration Workflow\n1. Configure strategy in YAML\n2. Run backtest → review metrics\n3. Adjust parameters → re-run\n4. Paper trade for 5+ days\n5. Compare paper vs backtest metrics\n6. Go live only after go-live gate passes`,
    },
    {
      id: 'metrics-guide', title: 'Performance Metrics', category: 'Best Practices',
      content: `## Key Metrics\n- **Win Rate**: % of winning trades. Target > 40%\n- **Profit Factor**: Gross Profit / Gross Loss. Target > 1.5\n- **Sharpe Ratio**: Risk-adjusted returns. Target > 1.0 (annualized)\n- **Max Drawdown**: Worst peak-to-trough decline. Target < 20%\n- **R-Multiple**: PnL / Initial Risk. Target avg > 1.0\n- **Expectancy**: (Win% × Avg Win) - (Loss% × Avg Loss). Must be positive\n\n## Red Flags\n- Win rate < 30%: signal quality is poor\n- Profit factor < 1.0: losing money overall\n- Max drawdown > 30%: risk of ruin\n- Avg R < 0.5: cutting winners too early\n- Max consecutive losses > 5: strategy may need suspension logic`,
    },
    {
      id: 'market-timing', title: 'Market Timing', category: 'Best Practices',
      content: `## Active Hours\n- Market open: 09:15 IST\n- Avoid first 15 minutes (high volatility, wide spreads)\n- Best trading window: 09:30 — 14:30\n- No new trades after 15:00 (configurable)\n- Force square-off at 15:15\n\n## Session Patterns\n- Morning (09:30-11:30): strongest trends, highest volume\n- Midday (11:30-13:30): range-bound, lower conviction\n- Afternoon (13:30-15:00): renewed activity, pre-close positioning\n\n## Avoiding Bad Days\n- Budget day / RBI policy day: reduce position size or skip\n- Expiry day (Thursday): increased volatility in F&O\n- Kill switch preemptively if market conditions are unusual`,
    },
    {
      id: 'backtesting', title: 'Backtesting Best Practices', category: 'Best Practices',
      content: `## Data Quality\n- Use M1 candle data (highest granularity available)\n- Minimum 30 trading days for statistical significance\n- Include diverse market conditions (trending + range-bound)\n\n## Slippage Model\n- Default: 5 basis points (0.05%) on entries\n- Conservative: 10 basis points for illiquid stocks\n- Exits: fill at trigger price (no additional slippage)\n\n## Avoiding Overfitting\n- Don't optimize on the same data you test on\n- Use out-of-sample validation (train on 60%, test on 40%)\n- Fewer parameters = more robust\n- If Sharpe in backtest is > 3.0, it's probably overfit\n\n## From Backtest to Live\n- Backtest → Paper trade (5+ days) → Compare metrics\n- Expect 20-40% performance degradation in live vs backtest\n- If paper trade metrics are within 70% of backtest, proceed to live`,
    },
  ];

  get filteredArticles(): KnowledgeArticle[] {
    let result = this.articles;
    if (this.activeCategory !== 'All') {
      result = result.filter(a => a.category === this.activeCategory);
    }
    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(a => a.title.toLowerCase().includes(q) || a.content.toLowerCase().includes(q));
    }
    return result;
  }

  categoryIcon(cat: string): string {
    switch (cat) {
      case 'Strategy Guides': return 'school';
      case 'Risk Management': return 'shield';
      case 'Best Practices': return 'star';
      default: return 'article';
    }
  }
}
