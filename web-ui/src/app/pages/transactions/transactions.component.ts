import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { Subject, takeUntil, interval } from 'rxjs';
import { GlobalStateService } from '../../core/services/global-state.service';
import { TradeRecord } from '../../core/models/api.models';

type GroupBy = 'none' | 'symbol' | 'strategy' | 'mode' | 'exitReason' | 'direction';

interface FilterState {
  dateFrom: string;
  dateTo: string;
  symbol: string;
  strategy: string;
  mode: string;
  direction: string;
  result: string;
}

interface GroupSummary {
  key: string;
  trades: TradeRecord[];
  total: number;
  wins: number;
  totalPnl: number;
  winRate: number;
  avgR: number;
}

@Component({
  selector: 'at-transactions',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatTableModule, MatSortModule,
    MatButtonModule, MatSelectModule, MatChipsModule
  ],
  template: `
    <div class="page-header">
      <h1>Transaction Center</h1>
      <button mat-stroked-button (click)="exportCsv()">Export CSV</button>
    </div>

    <!-- Summary Bar -->
    <div class="summary-bar">
      <div class="summary-item">
        <span class="summary-label">Trades</span>
        <span class="summary-value mono">{{ filtered.length }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">Win Rate</span>
        <span class="summary-value mono" [class]="summaryWinRate >= 50 ? 'profit' : 'loss'">{{ summaryWinRate.toFixed(0) }}%</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">Total PnL</span>
        <span class="summary-value mono" [class]="summaryPnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(summaryPnl) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">Avg PnL</span>
        <span class="summary-value mono" [class]="summaryAvgPnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(summaryAvgPnl) }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">Avg R</span>
        <span class="summary-value mono">{{ summaryAvgR.toFixed(2) }}R</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">Profit Factor</span>
        <span class="summary-value mono">{{ summaryProfitFactor }}</span>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar">
      <div class="filter-group">
        <label>From</label>
        <input type="date" [(ngModel)]="filters.dateFrom" (ngModelChange)="applyFilters()" />
      </div>
      <div class="filter-group">
        <label>To</label>
        <input type="date" [(ngModel)]="filters.dateTo" (ngModelChange)="applyFilters()" />
      </div>
      <div class="filter-group">
        <label>Symbol</label>
        <select [(ngModel)]="filters.symbol" (ngModelChange)="applyFilters()">
          <option value="">All Symbols</option>
          @for (s of uniqueSymbols; track s) { <option [value]="s">{{ s }}</option> }
        </select>
      </div>
      <div class="filter-group">
        <label>Strategy</label>
        <select [(ngModel)]="filters.strategy" (ngModelChange)="applyFilters()">
          <option value="">All Strategies</option>
          @for (s of uniqueStrategies; track s) { <option [value]="s">{{ s }}</option> }
        </select>
      </div>
      <div class="filter-group">
        <label>Mode</label>
        <div class="filter-chips">
          @for (m of ['BT','PT','LIVE']; track m) {
            <button class="chip" [class.active]="filters.mode === m" (click)="toggleMode(m)">{{ m }}</button>
          }
        </div>
      </div>
      <div class="filter-group">
        <label>Direction</label>
        <div class="filter-chips">
          @for (d of ['BUY','SELL']; track d) {
            <button class="chip" [class.active]="filters.direction === d" (click)="toggleDirection(d)">{{ d }}</button>
          }
        </div>
      </div>
      <div class="filter-group">
        <label>Group by</label>
        <select [(ngModel)]="groupBy" (ngModelChange)="applyFilters()">
          <option value="none">No Grouping</option>
          <option value="symbol">Symbol</option>
          <option value="strategy">Strategy</option>
          <option value="mode">Mode</option>
          <option value="exitReason">Exit Reason</option>
          <option value="direction">Direction</option>
        </select>
      </div>
    </div>

    <!-- Grouped or flat table -->
    @if (groupBy === 'none') {
      <div class="table-wrap">
        <table mat-table [dataSource]="dataSource" matSort class="mat-elevation-z0">
          <ng-container matColumnDef="entryTime">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Time </th>
            <td mat-cell *matCellDef="let t" class="mono text-muted"> {{ formatTime(t.entryTime) }} </td>
          </ng-container>

          <ng-container matColumnDef="symbol">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Symbol </th>
            <td mat-cell *matCellDef="let t">
              <a class="link" (click)="navSymbol(t.symbol)">{{ shortSymbol(t.symbol) }}</a>
            </td>
          </ng-container>

          <ng-container matColumnDef="strategyId">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Strategy </th>
            <td mat-cell *matCellDef="let t">
              <a class="link" (click)="navStrategy(t.strategyId)">{{ t.strategyId }}</a>
            </td>
          </ng-container>

          <ng-container matColumnDef="mode">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Mode </th>
            <td mat-cell *matCellDef="let t">
              <span class="badge mode">{{ modeLabel(t.mode) }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="direction">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Dir </th>
            <td mat-cell *matCellDef="let t">
              <span class="badge" [class]="t.direction === 'BUY' ? 'buy' : 'sell'">{{ t.direction }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="pnl">
            <th mat-header-cell *matHeaderCellDef mat-sort-header class="r"> PnL ₹ </th>
            <td mat-cell *matCellDef="let t" class="r mono" [class]="pnlClass(t.pnl)">
              {{ formatPnl(t.pnl) }}
            </td>
          </ng-container>

          <ng-container matColumnDef="rMultipleAchieved">
            <th mat-header-cell *matHeaderCellDef mat-sort-header class="r"> R </th>
            <td mat-cell *matCellDef="let t" class="r mono">
              {{ t.rMultipleAchieved != null ? (t.rMultipleAchieved | number:'1.1-1') + 'R' : '—' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="exitReason">
            <th mat-header-cell *matHeaderCellDef mat-sort-header> Reason </th>
            <td mat-cell *matCellDef="let t">
              <span class="badge exit">{{ t.exitReason || '—' }}</span>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>
        </table>
        @if (filtered.length === 0) {
          <div class="empty">No trades match current filters</div>
        }
      </div>
    } @else {
      @for (group of groups; track group.key) {
        <div class="group-section">
          <div class="group-header">
            <span class="group-key">{{ group.key }}</span>
            <span class="group-stats mono">
              {{ group.total }} trades · {{ (group.winRate * 100).toFixed(0) }}% win ·
              <span [class]="group.totalPnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(group.totalPnl) }}</span>
              · {{ group.avgR.toFixed(1) }}R avg
            </span>
          </div>
          <div class="table-wrap">
            <table class="at-table compact">
              <thead><tr>
                <th>Time</th><th>Symbol</th><th>Strategy</th><th>Dir</th>
                <th class="r">PnL</th><th class="r">R</th><th>Reason</th>
              </tr></thead>
              <tbody>
                @for (t of group.trades; track t.correlationId) {
                  <tr>
                    <td class="mono text-muted">{{ formatTime(t.entryTime) }}</td>
                    <td><a class="link" (click)="navSymbol(t.symbol)">{{ shortSymbol(t.symbol) }}</a></td>
                    <td class="text-muted">{{ t.strategyId }}</td>
                    <td><span class="badge" [class]="t.direction === 'BUY' ? 'buy' : 'sell'">{{ t.direction }}</span></td>
                    <td class="r mono" [class]="pnlClass(t.pnl)">{{ formatPnl(t.pnl) }}</td>
                    <td class="r mono">{{ t.rMultipleAchieved != null ? (t.rMultipleAchieved | number:'1.1-1') + 'R' : '—' }}</td>
                    <td class="text-muted">{{ t.exitReason || '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
      @if (groups.length === 0) {
        <div class="empty">No trades match current filters</div>
      }
    }
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .r { text-align: right !important; justify-content: flex-end; }

    .summary-bar { display: flex; gap: 16px; padding: 10px 16px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; margin-bottom: 12px; flex-wrap: wrap; }
    .summary-item { display: flex; flex-direction: column; gap: 2px; }
    .summary-label { font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted); }
    .summary-value { font-size: 15px; font-weight: 600; }

    .filter-bar { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; padding: 12px 16px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; margin-bottom: 16px; }
    .filter-group { display: flex; flex-direction: column; gap: 4px; }
    .filter-group label { font-size: 10px; text-transform: uppercase; color: var(--text-muted); font-weight: 600; }
    .filter-group input, .filter-group select { background: var(--bg-primary); border: 1px solid var(--border); color: var(--text-primary); padding: 4px 8px; border-radius: 4px; font-size: 12px; font-family: var(--font-mono); height: 28px; }
    .filter-chips { display: flex; gap: 4px; align-items: center; }
    .chip { background: transparent; border: 1px solid var(--border); color: var(--text-secondary); padding: 2px 10px; border-radius: 12px; cursor: pointer; font-size: 11px; height: 24px; }
    .chip:hover { background: var(--bg-hover); }
    .chip.active { background: var(--accent); color: #000; border-color: var(--accent); font-weight: 600; }

    .table-wrap { overflow-x: auto; }
    .at-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .at-table.compact { font-size: 11px; }
    .at-table th { text-align: left; font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--text-secondary); padding: 5px 6px; border-bottom: 1px solid var(--border); white-space: nowrap; }
    .at-table td { padding: 5px 6px; border-bottom: 1px solid var(--bg-hover); white-space: nowrap; }
    .at-table tr:hover td { background: var(--bg-hover); }

    .badge { padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; }
    .badge.buy { background: rgba(63,185,80,0.15); color: var(--profit); }
    .badge.sell { background: rgba(248,81,73,0.15); color: var(--loss); }
    .badge.mode { background: rgba(0,188,212,0.15); color: var(--accent); }
    .badge.exit { background: var(--bg-hover); color: var(--text-secondary); }
    .link { color: var(--accent); cursor: pointer; text-decoration: none; }
    .link:hover { text-decoration: underline; }
    .empty { color: var(--text-muted); font-size: 13px; padding: 24px 16px; text-align: center; }

    .group-section { margin-bottom: 16px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
    .group-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; background: var(--bg-hover); border-bottom: 1px solid var(--border); }
    .group-key { font-size: 13px; font-weight: 600; color: var(--accent); }
    .group-stats { font-size: 11px; color: var(--text-secondary); }
  `],
})
export class TransactionsComponent implements OnInit, OnDestroy {
  displayedColumns = ['entryTime', 'symbol', 'strategyId', 'mode', 'direction', 'pnl', 'rMultipleAchieved', 'exitReason'];
  dataSource = new MatTableDataSource<TradeRecord>([]);
  filtered: TradeRecord[] = [];
  groups: GroupSummary[] = [];

  filters: FilterState = { dateFrom: '', dateTo: '', symbol: '', strategy: '', mode: '', direction: '', result: '' };
  groupBy: GroupBy = 'none';

  @ViewChild(MatSort) sort!: MatSort;

  private destroy$ = new Subject<void>();

  constructor(
    public state: GlobalStateService,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Re-apply filters whenever global trades update
    interval(1000).pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.applyFilters();
    });
  }

  // ── Unique values for dropdowns ──────────────────────────
  get uniqueSymbols(): string[] { return [...new Set(this.state.recentTrades().map(t => t.symbol))].sort(); }
  get uniqueStrategies(): string[] { return [...new Set(this.state.recentTrades().map(t => t.strategyId))].sort(); }

  // ── Filter toggles ───────────────────────────────────────
  toggleMode(m: string): void { this.filters.mode = this.filters.mode === m ? '' : m; this.applyFilters(); }
  toggleDirection(d: string): void { this.filters.direction = this.filters.direction === d ? '' : d; this.applyFilters(); }
  toggleResult(r: string): void { this.filters.result = this.filters.result === r ? '' : r; this.applyFilters(); }

  // ── Apply filters + group ────────────────────────────────
  applyFilters(): void {
    let result = [...this.state.recentTrades()];

    if (this.filters.symbol) result = result.filter(t => t.symbol === this.filters.symbol);
    if (this.filters.strategy) result = result.filter(t => t.strategyId === this.filters.strategy);
    if (this.filters.mode) result = result.filter(t => this.modeLabel(t.mode) === this.filters.mode);
    if (this.filters.direction) result = result.filter(t => t.direction === this.filters.direction);
    if (this.filters.result === 'W') result = result.filter(t => t.pnl != null && t.pnl > 0);
    if (this.filters.result === 'L') result = result.filter(t => t.pnl != null && t.pnl <= 0);
    if (this.filters.dateFrom) {
      const from = new Date(this.filters.dateFrom).getTime();
      result = result.filter(t => new Date(t.entryTime).getTime() >= from);
    }
    if (this.filters.dateTo) {
      const to = new Date(this.filters.dateTo).getTime() + 86400000;
      result = result.filter(t => new Date(t.entryTime).getTime() < to);
    }

    this.filtered = result;
    this.dataSource.data = result;
    if (this.sort) this.dataSource.sort = this.sort;

    if (this.groupBy !== 'none') {
      this.groups = this.buildGroups(result);
    } else {
      this.groups = [];
    }
  }

  private buildGroups(trades: TradeRecord[]): GroupSummary[] {
    const map = new Map<string, TradeRecord[]>();
    for (const t of trades) {
      let key: string;
      switch (this.groupBy) {
        case 'symbol': key = t.symbol; break;
        case 'strategy': key = t.strategyId; break;
        case 'mode': key = this.modeLabel(t.mode); break;
        case 'exitReason': key = t.exitReason || 'N/A'; break;
        case 'direction': key = t.direction; break;
        default: key = 'all';
      }
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(t);
    }
    return Array.from(map.entries()).map(([key, trades]) => {
      const closed = trades.filter(t => t.pnl != null);
      const wins = closed.filter(t => t.pnl! > 0).length;
      const totalPnl = closed.reduce((s, t) => s + t.pnl!, 0);
      const rVals = closed.filter(t => t.rMultipleAchieved != null).map(t => t.rMultipleAchieved!);
      return {
        key,
        trades,
        total: trades.length,
        wins,
        totalPnl,
        winRate: closed.length > 0 ? wins / closed.length : 0,
        avgR: rVals.length > 0 ? rVals.reduce((s, v) => s + v, 0) / rVals.length : 0,
      };
    }).sort((a, b) => b.totalPnl - a.totalPnl);
  }

  // ── Summary metrics ──────────────────────────────────────
  get summaryWinRate(): number {
    const closed = this.filtered.filter(t => t.pnl != null);
    if (closed.length === 0) return 0;
    return (closed.filter(t => t.pnl! > 0).length / closed.length) * 100;
  }
  get summaryPnl(): number { return this.filtered.reduce((s, t) => s + (t.pnl ?? 0), 0); }
  get summaryAvgPnl(): number { const c = this.filtered.filter(t => t.pnl != null); return c.length > 0 ? this.summaryPnl / c.length : 0; }
  get summaryAvgR(): number {
    const rs = this.filtered.filter(t => t.rMultipleAchieved != null).map(t => t.rMultipleAchieved!);
    return rs.length > 0 ? rs.reduce((s, v) => s + v, 0) / rs.length : 0;
  }
  get summaryProfitFactor(): string {
    const gross = this.filtered.filter(t => t.pnl != null && t.pnl > 0).reduce((s, t) => s + t.pnl!, 0);
    const loss = Math.abs(this.filtered.filter(t => t.pnl != null && t.pnl < 0).reduce((s, t) => s + t.pnl!, 0));
    return loss > 0 ? (gross / loss).toFixed(2) : gross > 0 ? '∞' : '—';
  }

  // ── Navigation ───────────────────────────────────────────
  navSymbol(symbol: string): void { this.router.navigate(['/symbols', symbol]); }
  navStrategy(id: string): void { this.router.navigate(['/strategies', id]); }

  // ── Export ───────────────────────────────────────────────
  exportCsv(): void {
    const headers = ['Time', 'Symbol', 'Strategy', 'Mode', 'Direction', 'Entry', 'Exit', 'Qty', 'PnL', 'PnL%', 'R', 'Hold', 'Reason'];
    const rows = this.filtered.map(t => [
      t.entryTime, t.symbol, t.strategyId, t.mode, t.direction,
      t.entryPrice, t.exitPrice ?? '', t.quantity, t.pnl ?? '', t.pnlPct ?? '',
      t.rMultipleAchieved ?? '', t.holdDuration ?? '', t.exitReason ?? '',
    ].join(','));
    const csv = [headers.join(','), ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `trades-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click(); URL.revokeObjectURL(url);
  }

  // ── Helpers ──────────────────────────────────────────────
  modeLabel(m: string): string { return m === 'BACKTEST' ? 'BT' : m === 'PAPER' ? 'PT' : 'LIVE'; }
  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
  pnlClass(v: number | null): string { return v == null ? '' : v >= 0 ? 'profit' : 'loss'; }
  formatPnl(v: number | null): string {
    if (v == null) return '—';
    const sign = v >= 0 ? '+' : '';
    return `${sign}₹${v.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }
  formatTime(iso: string | null): string {
    if (!iso) return '—';
    try { return new Date(iso).toLocaleTimeString('en-IN', { hour12: false }); } catch { return iso; }
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
