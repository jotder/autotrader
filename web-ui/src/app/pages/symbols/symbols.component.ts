import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil, switchMap, forkJoin, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { TradeRecord } from '../../core/models/api.models';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';

@Component({
  selector: 'at-symbols',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusCardComponent, MetricRowComponent],
  template: `
    <div class="page-header">
      <h1>{{ selectedSymbol ? shortSymbol(selectedSymbol) + ' — 360° View' : 'Symbols' }}</h1>
      @if (selectedSymbol) {
        <button class="btn-sm" (click)="clearSelection()">Back to list</button>
      }
    </div>

    <!-- Symbol search/list mode -->
    @if (!selectedSymbol) {
      <div class="search-bar">
        <input type="text" [(ngModel)]="searchQuery" (ngModelChange)="filterSymbols()" placeholder="Search symbols…" class="search-input" />
      </div>
      <div class="symbol-list">
        @for (sym of filteredSymbols; track sym.symbol) {
          <div class="symbol-row at-card" (click)="selectSymbol(sym.symbol)">
            <div class="symbol-info">
              <span class="symbol-name mono">{{ shortSymbol(sym.symbol) }}</span>
              <span class="symbol-full text-muted">{{ sym.symbol }}</span>
            </div>
            <div class="symbol-stats">
              <span class="stat mono">{{ sym.tradeCount }} trades</span>
              <span class="stat mono" [class]="sym.totalPnl >= 0 ? 'profit' : 'loss'">
                {{ sym.totalPnl >= 0 ? '+' : '' }}₹{{ sym.totalPnl.toLocaleString('en-IN', {maximumFractionDigits: 0}) }}
              </span>
              <span class="stat mono" [class]="sym.winRate >= 50 ? 'profit' : 'loss'">{{ sym.winRate.toFixed(0) }}% win</span>
            </div>
          </div>
        }
        @if (filteredSymbols.length === 0) {
          <div class="empty">{{ tradedSymbols.length === 0 ? 'No traded symbols yet' : 'No matches' }}</div>
        }
      </div>
    }

    <!-- Symbol detail (360° view) -->
    @if (selectedSymbol) {
      <div class="tabs">
        @for (tab of ['Overview', 'Trades', 'Strategies', 'Profile']; track tab) {
          <button class="tab" [class.active]="activeTab === tab" (click)="activeTab = tab">{{ tab }}</button>
        }
      </div>

      @if (activeTab === 'Overview') {
        <div class="overview-grid">
          <at-status-card title="Symbol Info" icon="info" iconColor="var(--accent)">
            <at-metric label="Full Symbol" [value]="selectedSymbol" />
            <at-metric label="Traded" [value]="symbolTrades.length + ' times'" />
            @if (parsedSymbol) {
              <at-metric label="Exchange" [value]="parsedSymbol.exchange || '—'" />
              <at-metric label="Segment" [value]="parsedSymbol.segment || '—'" />
              <at-metric label="Type" [value]="parsedSymbol.symbolType || '—'" />
              @if (parsedSymbol.underlying) { <at-metric label="Underlying" [value]="parsedSymbol.underlying" /> }
              @if (parsedSymbol.expiry) { <at-metric label="Expiry" [value]="parsedSymbol.expiry" /> }
              @if (parsedSymbol.strikePrice) { <at-metric label="Strike" [value]="parsedSymbol.strikePrice" /> }
              @if (parsedSymbol.optionType) { <at-metric label="Option" [value]="parsedSymbol.optionType" /> }
            }
          </at-status-card>
          <at-status-card title="Performance" icon="analytics" iconColor="var(--info)">
            <at-metric label="Total PnL" [value]="formatPnl(symbolPnl)" [valueClass]="symbolPnl >= 0 ? 'profit' : 'loss'" />
            <at-metric label="Win Rate" [value]="symbolWinRate.toFixed(0) + '%'" [valueClass]="symbolWinRate >= 50 ? 'profit' : 'loss'" />
            <at-metric label="Avg R" [value]="symbolAvgR.toFixed(2) + 'R'" />
            <at-metric label="Best Trade" [value]="formatPnl(symbolBest)" [valueClass]="'profit'" />
            <at-metric label="Worst Trade" [value]="formatPnl(symbolWorst)" [valueClass]="'loss'" />
          </at-status-card>
        </div>
      }

      @if (activeTab === 'Trades') {
        <div class="table-wrap">
          <table class="at-table">
            <thead><tr>
              <th>Time</th><th>Strategy</th><th>Mode</th><th>Dir</th>
              <th class="r">Entry</th><th class="r">Exit</th><th class="r">PnL</th><th class="r">R</th><th>Reason</th>
            </tr></thead>
            <tbody>
              @for (t of symbolTrades; track t.correlationId) {
                <tr>
                  <td class="mono text-muted">{{ formatTime(t.entryTime) }}</td>
                  <td>{{ t.strategyId }}</td>
                  <td><span class="badge mode">{{ modeLabel(t.mode) }}</span></td>
                  <td><span class="badge" [class]="t.direction === 'BUY' ? 'buy' : 'sell'">{{ t.direction }}</span></td>
                  <td class="r mono">{{ t.entryPrice | number:'1.2-2' }}</td>
                  <td class="r mono">{{ t.exitPrice != null ? (t.exitPrice | number:'1.2-2') : '—' }}</td>
                  <td class="r mono" [class]="pnlClass(t.pnl)">{{ formatPnl(t.pnl) }}</td>
                  <td class="r mono">{{ t.rMultipleAchieved != null ? t.rMultipleAchieved.toFixed(1) + 'R' : '—' }}</td>
                  <td class="text-muted">{{ t.exitReason || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
          @if (symbolTrades.length === 0) { <div class="empty">No trades for this symbol</div> }
        </div>
      }

      @if (activeTab === 'Strategies') {
        <table class="at-table">
          <thead><tr>
            <th>Strategy</th><th class="r">Trades</th><th class="r">Win Rate</th><th class="r">PnL</th><th class="r">Avg R</th>
          </tr></thead>
          <tbody>
            @for (s of symbolStrategyStats; track s.strategy) {
              <tr>
                <td class="link" (click)="navStrategy(s.strategy)">{{ s.strategy }}</td>
                <td class="r mono">{{ s.count }}</td>
                <td class="r mono" [class]="s.winRate >= 50 ? 'profit' : 'loss'">{{ s.winRate.toFixed(0) }}%</td>
                <td class="r mono" [class]="s.pnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(s.pnl) }}</td>
                <td class="r mono">{{ s.avgR.toFixed(2) }}R</td>
              </tr>
            }
          </tbody>
        </table>
        @if (symbolStrategyStats.length === 0) { <div class="empty">No strategy data</div> }
      }

      @if (activeTab === 'Profile') {
        @if (profile) {
          <at-status-card title="Statistical Profile" icon="query_stats" iconColor="var(--accent)">
            @for (entry of profileEntries; track entry[0]) {
              <at-metric [label]="entry[0]" [value]="entry[1]" />
            }
          </at-status-card>
        } @else {
          <div class="empty">No profile data available. Download M1 candle data for this symbol first.</div>
        }
      }
    }
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .btn-sm { padding: 4px 12px; font-size: 12px; border: 1px solid var(--border); background: transparent; color: var(--text-primary); border-radius: 4px; cursor: pointer; }

    .search-bar { margin-bottom: 12px; }
    .search-input { width: 100%; max-width: 400px; padding: 8px 12px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 6px; color: var(--text-primary); font-size: 13px; font-family: var(--font-mono); }

    .symbol-list { display: flex; flex-direction: column; gap: 6px; }
    .symbol-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; cursor: pointer; transition: border-color 0.2s; }
    .symbol-row:hover { border-color: var(--accent); }
    .symbol-info { display: flex; flex-direction: column; gap: 2px; }
    .symbol-name { font-size: 14px; font-weight: 600; }
    .symbol-full { font-size: 11px; }
    .symbol-stats { display: flex; gap: 16px; }
    .stat { font-size: 12px; }

    .tabs { display: flex; gap: 2px; margin-bottom: 16px; border-bottom: 1px solid var(--border); }
    .tab { background: transparent; border: none; color: var(--text-secondary); padding: 8px 16px; cursor: pointer; font-size: 13px; border-bottom: 2px solid transparent; }
    .tab:hover { color: var(--text-primary); }
    .tab.active { color: var(--accent); border-bottom-color: var(--accent); }

    .overview-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }

    .table-wrap { overflow-x: auto; }
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
    .empty { color: var(--text-muted); font-size: 13px; padding: 24px; text-align: center; }
  `],
})
export class SymbolsComponent implements OnInit, OnDestroy {
  allTrades: TradeRecord[] = [];
  tradedSymbols: { symbol: string; tradeCount: number; totalPnl: number; winRate: number }[] = [];
  filteredSymbols = this.tradedSymbols;
  searchQuery = '';
  selectedSymbol: string | null = null;
  activeTab = 'Overview';
  parsedSymbol: any = null;
  profile: any = null;

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService, private router: Router, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.api.getTrades().pipe(catchError(() => of([]))).subscribe(trades => {
      this.allTrades = trades;
      this.buildSymbolList();
    });

    // Check route param for direct symbol link
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const sym = params.get('symbol');
      if (sym) this.selectSymbol(sym);
    });
  }

  private buildSymbolList(): void {
    const map = new Map<string, TradeRecord[]>();
    for (const t of this.allTrades) {
      if (!map.has(t.symbol)) map.set(t.symbol, []);
      map.get(t.symbol)!.push(t);
    }
    this.tradedSymbols = Array.from(map.entries()).map(([symbol, trades]) => {
      const closed = trades.filter(t => t.pnl != null);
      const wins = closed.filter(t => t.pnl! > 0).length;
      return {
        symbol,
        tradeCount: trades.length,
        totalPnl: closed.reduce((s, t) => s + t.pnl!, 0),
        winRate: closed.length > 0 ? (wins / closed.length) * 100 : 0,
      };
    }).sort((a, b) => b.totalPnl - a.totalPnl);
    this.filterSymbols();
  }

  filterSymbols(): void {
    const q = this.searchQuery.toLowerCase();
    this.filteredSymbols = q ? this.tradedSymbols.filter(s => s.symbol.toLowerCase().includes(q)) : this.tradedSymbols;
  }

  selectSymbol(symbol: string): void {
    this.selectedSymbol = symbol;
    this.activeTab = 'Overview';
    this.parsedSymbol = null;
    this.profile = null;
    this.api.parseSymbol(symbol).pipe(catchError(() => of(null))).subscribe(p => this.parsedSymbol = p);
    this.api.getSymbolProfile(symbol).pipe(catchError(() => of(null))).subscribe(p => this.profile = p);
  }

  clearSelection(): void { this.selectedSymbol = null; }

  get symbolTrades(): TradeRecord[] {
    return this.allTrades.filter(t => t.symbol === this.selectedSymbol)
      .sort((a, b) => new Date(b.entryTime).getTime() - new Date(a.entryTime).getTime());
  }

  get symbolPnl(): number { return this.symbolTrades.reduce((s, t) => s + (t.pnl ?? 0), 0); }
  get symbolWinRate(): number {
    const c = this.symbolTrades.filter(t => t.pnl != null);
    return c.length > 0 ? (c.filter(t => t.pnl! > 0).length / c.length) * 100 : 0;
  }
  get symbolAvgR(): number {
    const rs = this.symbolTrades.filter(t => t.rMultipleAchieved != null).map(t => t.rMultipleAchieved!);
    return rs.length > 0 ? rs.reduce((s, v) => s + v, 0) / rs.length : 0;
  }
  get symbolBest(): number | null {
    const pnls = this.symbolTrades.filter(t => t.pnl != null).map(t => t.pnl!);
    return pnls.length > 0 ? Math.max(...pnls) : null;
  }
  get symbolWorst(): number | null {
    const pnls = this.symbolTrades.filter(t => t.pnl != null).map(t => t.pnl!);
    return pnls.length > 0 ? Math.min(...pnls) : null;
  }
  get symbolStrategyStats(): { strategy: string; count: number; winRate: number; pnl: number; avgR: number }[] {
    const map = new Map<string, TradeRecord[]>();
    for (const t of this.symbolTrades) {
      if (!map.has(t.strategyId)) map.set(t.strategyId, []);
      map.get(t.strategyId)!.push(t);
    }
    return Array.from(map.entries()).map(([strategy, ts]) => {
      const closed = ts.filter(t => t.pnl != null);
      const wins = closed.filter(t => t.pnl! > 0).length;
      const rs = closed.filter(t => t.rMultipleAchieved != null).map(t => t.rMultipleAchieved!);
      return {
        strategy,
        count: ts.length,
        winRate: closed.length > 0 ? (wins / closed.length) * 100 : 0,
        pnl: closed.reduce((s, t) => s + t.pnl!, 0),
        avgR: rs.length > 0 ? rs.reduce((s, v) => s + v, 0) / rs.length : 0,
      };
    }).sort((a, b) => b.pnl - a.pnl);
  }
  get profileEntries(): [string, any][] {
    if (!this.profile) return [];
    return Object.entries(this.profile).filter(([k]) => k !== 'symbol');
  }

  navStrategy(id: string): void { this.router.navigate(['/strategies', id]); }
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
