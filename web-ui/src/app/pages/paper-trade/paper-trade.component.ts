import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil, interval, switchMap, startWith, catchError, of, forkJoin } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { Position, RiskResponse, TradeRecord } from '../../core/models/api.models';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'at-paper-trade',
  standalone: true,
  imports: [CommonModule, StatusCardComponent, MetricRowComponent],
  template: `
    <div class="page-header">
      <h1>Paper Trade</h1>
      <span class="live-dot"></span>
    </div>

    <div class="paper-grid">
      <!-- Active Positions (compact) -->
      <at-status-card title="Active Positions" icon="show_chart"
                      [iconColor]="positions.length > 0 ? 'var(--accent)' : 'var(--text-secondary)'">
        @if (positions.length === 0) {
          <div class="empty">No open positions</div>
        } @else {
          <div class="table-wrap">
            <table class="at-table compact">
              <thead>
                <tr>
                  <th>Symbol</th>
                  <th>Dir</th>
                  <th class="r">Entry</th>
                  <th class="r">SL</th>
                  <th class="r">PnL</th>
                  <th>Trail</th>
                </tr>
              </thead>
              <tbody>
                @for (p of positions; track p.correlationId) {
                  <tr>
                    <td class="mono">{{ shortSymbol(p.symbol) }}</td>
                    <td><span class="badge" [class]="p.direction === 'BUY' ? 'buy' : 'sell'">{{ p.direction }}</span></td>
                    <td class="r mono">{{ p.entryPrice | number:'1.2-2' }}</td>
                    <td class="r mono">{{ p.currentStopLoss | number:'1.2-2' }}</td>
                    <td class="r mono" [class]="pnlClass(p.unrealizedPnl)">{{ formatPnl(p.unrealizedPnl) }}</td>
                    <td>{{ p.trailingActivated ? '✓' : '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </at-status-card>

      <!-- Session Stats -->
      <at-status-card title="Session Stats" icon="bar_chart" iconColor="var(--info)">
        <at-metric label="Trades Today" [value]="todayTrades.length" />
        <at-metric label="Winners" [value]="winners" [valueClass]="'profit'" />
        <at-metric label="Losers" [value]="losers" [valueClass]="losers > 0 ? 'loss' : ''" />
        <at-metric label="Win Rate" [value]="winRate" />
        <at-metric label="Realized PnL" [value]="formatPnl(totalPnl)" [valueClass]="pnlClass(totalPnl)" />
        <at-metric label="Best Trade" [value]="formatPnl(bestPnl)" [valueClass]="'profit'" />
        <at-metric label="Worst Trade" [value]="formatPnl(worstPnl)" [valueClass]="'loss'" />
      </at-status-card>

      <!-- Risk Utilization -->
      <at-status-card title="Risk Utilization" icon="shield" iconColor="var(--warning)">
        @if (risk) {
          <at-metric label="Daily PnL" [value]="formatPnl(risk.dailyPnl)" [valueClass]="pnlClass(risk.dailyPnl)" />
          <at-metric label="Loss Limit" [value]="formatPnl(-risk.maxDailyLossInr)" />
          <div class="bar-section">
            <div class="bar-label">Loss Utilization</div>
            <div class="bar-container">
              <div class="bar-fill" [style.width.%]="lossUtilPct" [class]="barClass"></div>
            </div>
            <div class="bar-value">{{ lossUtilPct.toFixed(0) }}%</div>
          </div>
          <at-metric label="Kill Switch" [value]="risk.killSwitchActive ? 'ON' : 'OFF'"
                     [valueClass]="risk.killSwitchActive ? 'loss' : ''" />
          <at-metric label="Profit Lock" [value]="risk.dailyProfitLocked ? 'LOCKED' : 'Open'" />
        } @else {
          <div class="empty">No risk data</div>
        }
      </at-status-card>
    </div>

    <!-- Recent Exits -->
    <at-status-card title="Recent Exits" icon="logout" iconColor="var(--text-secondary)"
                    style="margin-top: 16px">
      @if (recentExits.length === 0) {
        <div class="empty">No exits today</div>
      } @else {
        <div class="table-wrap">
          <table class="at-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Symbol</th>
                <th>Strategy</th>
                <th>Dir</th>
                <th class="r">PnL</th>
                <th class="r">R</th>
                <th>Reason</th>
                <th>Hold</th>
              </tr>
            </thead>
            <tbody>
              @for (t of recentExits; track t.correlationId) {
                <tr>
                  <td class="mono text-muted">{{ formatTime(t.exitTime) }}</td>
                  <td class="mono">{{ shortSymbol(t.symbol) }}</td>
                  <td class="text-muted">{{ t.strategyId }}</td>
                  <td><span class="badge" [class]="t.direction === 'BUY' ? 'buy' : 'sell'">{{ t.direction }}</span></td>
                  <td class="r mono" [class]="pnlClass(t.pnl)">{{ formatPnl(t.pnl) }}</td>
                  <td class="r mono">{{ t.rMultipleAchieved != null ? t.rMultipleAchieved.toFixed(1) + 'R' : '—' }}</td>
                  <td class="text-muted">{{ t.exitReason || '—' }}</td>
                  <td class="mono text-muted">{{ t.holdDuration || '—' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </at-status-card>
  `,
  styles: [`
    .page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .live-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--profit); animation: pulse 2s infinite; }
    @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

    .paper-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }

    .table-wrap { overflow-x: auto; }
    .at-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .at-table.compact { font-size: 12px; }
    .at-table th { text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--text-secondary); padding: 4px 6px; border-bottom: 1px solid var(--border); }
    .at-table td { padding: 4px 6px; border-bottom: 1px solid var(--bg-hover); }
    .at-table tr:hover td { background: var(--bg-hover); }
    .r { text-align: right; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 11px; font-weight: 600; }
    .badge.buy { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .badge.sell { background: rgba(248, 81, 73, 0.15); color: var(--loss); }
    .empty { color: var(--text-muted); font-size: 13px; padding: 8px 0; }

    .bar-section { margin: 8px 0; }
    .bar-label { font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; }
    .bar-container { height: 6px; background: var(--bg-hover); border-radius: 3px; overflow: hidden; }
    .bar-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
    .bar-fill.safe { background: var(--profit); }
    .bar-fill.caution { background: var(--warning); }
    .bar-fill.danger { background: var(--loss); }
    .bar-value { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
  `],
})
export class PaperTradeComponent implements OnInit, OnDestroy {
  positions: Position[] = [];
  risk: RiskResponse | null = null;
  todayTrades: TradeRecord[] = [];

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    interval(environment.pollingIntervalMs).pipe(
      startWith(0),
      switchMap(() => forkJoin({
        positions: this.api.getPositions().pipe(catchError(() => of([]))),
        risk: this.api.getRisk().pipe(catchError(() => of(null))),
        trades: this.api.getTrades().pipe(catchError(() => of([]))),
      })),
      takeUntil(this.destroy$),
    ).subscribe(data => {
      this.positions = data.positions as Position[];
      if (data.risk) this.risk = data.risk as RiskResponse;
      this.todayTrades = (data.trades as TradeRecord[]) || [];
    });
  }

  get winners(): number { return this.todayTrades.filter(t => t.pnl != null && t.pnl > 0).length; }
  get losers(): number { return this.todayTrades.filter(t => t.pnl != null && t.pnl <= 0).length; }
  get winRate(): string {
    const total = this.winners + this.losers;
    return total > 0 ? `${((this.winners / total) * 100).toFixed(0)}%` : '—';
  }
  get totalPnl(): number { return this.todayTrades.reduce((s, t) => s + (t.pnl ?? 0), 0); }
  get bestPnl(): number | null {
    const pnls = this.todayTrades.filter(t => t.pnl != null).map(t => t.pnl!);
    return pnls.length > 0 ? Math.max(...pnls) : null;
  }
  get worstPnl(): number | null {
    const pnls = this.todayTrades.filter(t => t.pnl != null).map(t => t.pnl!);
    return pnls.length > 0 ? Math.min(...pnls) : null;
  }
  get recentExits(): TradeRecord[] {
    return this.todayTrades.filter(t => t.status === 'CLOSED').slice(-10).reverse();
  }
  get lossUtilPct(): number {
    if (!this.risk || !this.risk.maxDailyLossInr) return 0;
    return Math.min(100, Math.abs(this.risk.dailyPnl) / this.risk.maxDailyLossInr * 100);
  }
  get barClass(): string {
    const p = this.lossUtilPct;
    return p < 50 ? 'safe' : p < 80 ? 'caution' : 'danger';
  }

  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
  pnlClass(v: number | null | undefined): string { return v == null ? '' : v >= 0 ? 'profit' : 'loss'; }
  formatPnl(v: number | null | undefined): string {
    if (v == null) return '—';
    return `${v >= 0 ? '+' : ''}₹${v.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }
  formatTime(iso: string | null): string {
    if (!iso) return '—';
    try { return new Date(iso).toLocaleTimeString('en-IN', { hour12: false }); } catch { return iso; }
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
