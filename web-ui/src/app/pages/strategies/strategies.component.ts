import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, takeUntil, forkJoin, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { TradeRecord } from '../../core/models/api.models';
import { MetricRowComponent } from '../../shared/components/metric-row.component';

interface StrategyView {
  id: string;
  tradeCount: number;
  wins: number;
  winRate: number;
  totalPnl: number;
  avgR: number;
  symbols: string[];
}

@Component({
  selector: 'at-strategies',
  standalone: true,
  imports: [CommonModule, MetricRowComponent],
  template: `
    <div class="page-header">
      <h1>Strategies</h1>
    </div>

    @if (strategies.length === 0) {
      <div class="empty-page">
        <p>No strategy data available. Run some trades (backtest or paper) to see strategy analytics here.</p>
      </div>
    } @else {
      <div class="strategy-grid">
        @for (s of strategies; track s.id) {
          <div class="at-card strategy-card" (click)="selectStrategy(s)">
            <div class="strategy-header">
              <span class="strategy-name">{{ s.id }}</span>
              <span class="badge" [class]="s.totalPnl >= 0 ? 'profit-badge' : 'loss-badge'">
                {{ s.totalPnl >= 0 ? '+' : '' }}₹{{ s.totalPnl.toLocaleString('en-IN', {maximumFractionDigits: 0}) }}
              </span>
            </div>
            <div class="strategy-metrics">
              <at-metric label="Trades" [value]="s.tradeCount" />
              <at-metric label="Win Rate" [value]="s.winRate.toFixed(0) + '%'" [valueClass]="s.winRate >= 50 ? 'profit' : 'loss'" />
              <at-metric label="Avg R" [value]="s.avgR.toFixed(2) + 'R'" />
              <at-metric label="Symbols" [value]="s.symbols.length" />
            </div>
            <div class="strategy-symbols">
              @for (sym of s.symbols.slice(0, 5); track sym) {
                <span class="symbol-chip">{{ shortSymbol(sym) }}</span>
              }
              @if (s.symbols.length > 5) {
                <span class="symbol-chip more">+{{ s.symbols.length - 5 }}</span>
              }
            </div>
          </div>
        }
      </div>
    }

    <!-- Strategy Detail Panel -->
    @if (selected) {
      <div class="detail-panel at-card" style="margin-top: 16px">
        <div class="detail-header">
          <h2>{{ selected.id }}</h2>
          <button class="btn-sm" (click)="selected = null">Close</button>
        </div>

        <!-- Tabs -->
        <div class="tabs">
          @for (tab of ['Performance', 'Transactions', 'Symbols']; track tab) {
            <button class="tab" [class.active]="activeTab === tab" (click)="activeTab = tab">{{ tab }}</button>
          }
        </div>

        @if (activeTab === 'Performance') {
          <div class="tab-content">
            <div class="perf-grid">
              <at-metric label="Total Trades" [value]="selected.tradeCount" />
              <at-metric label="Winners" [value]="selected.wins" [valueClass]="'profit'" />
              <at-metric label="Losers" [value]="selected.tradeCount - selected.wins" [valueClass]="'loss'" />
              <at-metric label="Win Rate" [value]="selected.winRate.toFixed(1) + '%'" [valueClass]="selected.winRate >= 50 ? 'profit' : 'loss'" />
              <at-metric label="Total PnL" [value]="formatPnl(selected.totalPnl)" [valueClass]="selected.totalPnl >= 0 ? 'profit' : 'loss'" />
              <at-metric label="Avg R-Multiple" [value]="selected.avgR.toFixed(2) + 'R'" />
            </div>
          </div>
        }
        @if (activeTab === 'Transactions') {
          <div class="tab-content">
            <table class="at-table">
              <thead><tr>
                <th>Time</th><th>Symbol</th><th>Dir</th><th>Mode</th><th class="r">PnL</th><th class="r">R</th><th>Reason</th>
              </tr></thead>
              <tbody>
                @for (t of selectedTrades; track t.correlationId) {
                  <tr>
                    <td class="mono text-muted">{{ formatTime(t.entryTime) }}</td>
                    <td><a class="link" (click)="navSymbol(t.symbol)">{{ shortSymbol(t.symbol) }}</a></td>
                    <td><span class="badge" [class]="t.direction === 'BUY' ? 'buy' : 'sell'">{{ t.direction }}</span></td>
                    <td><span class="badge mode">{{ modeLabel(t.mode) }}</span></td>
                    <td class="r mono" [class]="pnlClass(t.pnl)">{{ formatPnl(t.pnl) }}</td>
                    <td class="r mono">{{ t.rMultipleAchieved != null ? t.rMultipleAchieved.toFixed(1) + 'R' : '—' }}</td>
                    <td class="text-muted">{{ t.exitReason || '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
            @if (selectedTrades.length === 0) { <div class="empty">No trades for this strategy</div> }
          </div>
        }
        @if (activeTab === 'Symbols') {
          <div class="tab-content">
            <table class="at-table">
              <thead><tr>
                <th>Symbol</th><th class="r">Trades</th><th class="r">Win Rate</th><th class="r">PnL</th>
              </tr></thead>
              <tbody>
                @for (sym of selectedSymbolStats; track sym.symbol) {
                  <tr>
                    <td><a class="link" (click)="navSymbol(sym.symbol)">{{ shortSymbol(sym.symbol) }}</a></td>
                    <td class="r mono">{{ sym.count }}</td>
                    <td class="r mono" [class]="sym.winRate >= 50 ? 'profit' : 'loss'">{{ sym.winRate.toFixed(0) }}%</td>
                    <td class="r mono" [class]="sym.pnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(sym.pnl) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .page-header { margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .empty-page { color: var(--text-muted); font-size: 13px; padding: 40px 16px; text-align: center; }

    .strategy-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
    .strategy-card { padding: 14px; cursor: pointer; transition: border-color 0.2s; }
    .strategy-card:hover { border-color: var(--accent); }
    .strategy-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
    .strategy-name { font-size: 14px; font-weight: 600; color: var(--accent); }
    .profit-badge { background: rgba(63,185,80,0.15); color: var(--profit); padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
    .loss-badge { background: rgba(248,81,73,0.15); color: var(--loss); padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
    .strategy-metrics { margin-bottom: 8px; }
    .strategy-symbols { display: flex; gap: 4px; flex-wrap: wrap; }
    .symbol-chip { background: var(--bg-hover); padding: 2px 6px; border-radius: 3px; font-size: 10px; color: var(--text-secondary); font-family: var(--font-mono); }
    .symbol-chip.more { color: var(--accent); }

    .detail-panel { padding: 16px; }
    .detail-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .detail-header h2 { margin: 0; font-size: 16px; color: var(--accent); }
    .btn-sm { padding: 4px 12px; font-size: 12px; border: 1px solid var(--border); background: transparent; color: var(--text-primary); border-radius: 4px; cursor: pointer; }

    .tabs { display: flex; gap: 2px; margin-bottom: 12px; border-bottom: 1px solid var(--border); }
    .tab { background: transparent; border: none; color: var(--text-secondary); padding: 8px 16px; cursor: pointer; font-size: 13px; border-bottom: 2px solid transparent; }
    .tab:hover { color: var(--text-primary); }
    .tab.active { color: var(--accent); border-bottom-color: var(--accent); }

    .tab-content { min-height: 100px; }
    .perf-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }

    .at-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .at-table th { text-align: left; font-size: 10px; text-transform: uppercase; color: var(--text-secondary); padding: 5px 6px; border-bottom: 1px solid var(--border); }
    .at-table td { padding: 5px 6px; border-bottom: 1px solid var(--bg-hover); }
    .at-table tr:hover td { background: var(--bg-hover); }
    .r { text-align: right; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; }
    .badge.buy { background: rgba(63,185,80,0.15); color: var(--profit); }
    .badge.sell { background: rgba(248,81,73,0.15); color: var(--loss); }
    .badge.mode { background: rgba(0,188,212,0.15); color: var(--accent); }
    .link { color: var(--accent); cursor: pointer; }
    .link:hover { text-decoration: underline; }
    .empty { color: var(--text-muted); font-size: 13px; padding: 16px; text-align: center; }
  `],
})
export class StrategiesComponent implements OnInit, OnDestroy {
  strategies: StrategyView[] = [];
  allTrades: TradeRecord[] = [];
  selected: StrategyView | null = null;
  activeTab = 'Performance';

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    forkJoin({
      trades: this.api.getTrades().pipe(catchError(() => of([]))),
    }).pipe(takeUntil(this.destroy$)).subscribe(data => {
      this.allTrades = data.trades;
      this.buildStrategies();
    });
  }

  private buildStrategies(): void {
    const map = new Map<string, TradeRecord[]>();
    for (const t of this.allTrades) {
      if (!map.has(t.strategyId)) map.set(t.strategyId, []);
      map.get(t.strategyId)!.push(t);
    }
    this.strategies = Array.from(map.entries()).map(([id, trades]) => {
      const closed = trades.filter(t => t.pnl != null);
      const wins = closed.filter(t => t.pnl! > 0).length;
      const totalPnl = closed.reduce((s, t) => s + t.pnl!, 0);
      const rs = closed.filter(t => t.rMultipleAchieved != null).map(t => t.rMultipleAchieved!);
      return {
        id,
        tradeCount: trades.length,
        wins,
        winRate: closed.length > 0 ? (wins / closed.length) * 100 : 0,
        totalPnl,
        avgR: rs.length > 0 ? rs.reduce((s, v) => s + v, 0) / rs.length : 0,
        symbols: [...new Set(trades.map(t => t.symbol))],
      };
    }).sort((a, b) => b.totalPnl - a.totalPnl);
  }

  selectStrategy(s: StrategyView): void {
    this.selected = s;
    this.activeTab = 'Performance';
  }

  get selectedTrades(): TradeRecord[] {
    if (!this.selected) return [];
    return this.allTrades.filter(t => t.strategyId === this.selected!.id)
      .sort((a, b) => new Date(b.entryTime).getTime() - new Date(a.entryTime).getTime());
  }

  get selectedSymbolStats(): { symbol: string; count: number; winRate: number; pnl: number }[] {
    if (!this.selected) return [];
    const trades = this.allTrades.filter(t => t.strategyId === this.selected!.id);
    const map = new Map<string, TradeRecord[]>();
    for (const t of trades) {
      if (!map.has(t.symbol)) map.set(t.symbol, []);
      map.get(t.symbol)!.push(t);
    }
    return Array.from(map.entries()).map(([symbol, ts]) => {
      const closed = ts.filter(t => t.pnl != null);
      const wins = closed.filter(t => t.pnl! > 0).length;
      return {
        symbol,
        count: ts.length,
        winRate: closed.length > 0 ? (wins / closed.length) * 100 : 0,
        pnl: closed.reduce((s, t) => s + t.pnl!, 0),
      };
    }).sort((a, b) => b.pnl - a.pnl);
  }

  navSymbol(s: string): void { this.router.navigate(['/symbols', s]); }
  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
  modeLabel(m: string): string { return m === 'BACKTEST' ? 'BT' : m === 'PAPER' ? 'PT' : 'LIVE'; }
  pnlClass(v: number | null): string { return v == null ? '' : v >= 0 ? 'profit' : 'loss'; }
  formatPnl(v: number | null): string {
    if (v == null) return '—';
    return `${v >= 0 ? '+' : ''}₹${v.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  }
  formatTime(iso: string): string {
    try { return new Date(iso).toLocaleTimeString('en-IN', { hour12: false }); } catch { return iso; }
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
