import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { GlobalStateService } from '../../core/services/global-state.service';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';

@Component({
  selector: 'at-paper-trade',
  standalone: true,
  imports: [CommonModule, MatTableModule, StatusCardComponent, MetricRowComponent],
  template: `
    <div class="page-header">
      <h1>Paper Trade Center</h1>
      <span class="live-dot" [class.stopped]="!state.isRunning()"></span>
    </div>

    <div class="paper-grid">
      <!-- Active Positions -->
      <at-status-card title="Active Positions" icon="show_chart"
                      [iconColor]="state.positions().length > 0 ? 'var(--accent)' : 'var(--text-secondary)'">
        <table mat-table [dataSource]="state.positions()" class="mat-elevation-z0 compact-table">
          <ng-container matColumnDef="symbol">
            <th mat-header-cell *matHeaderCellDef> Symbol </th>
            <td mat-cell *matCellDef="let p" class="mono"> {{ shortSymbol(p.symbol) }} </td>
          </ng-container>

          <ng-container matColumnDef="direction">
            <th mat-header-cell *matHeaderCellDef> Dir </th>
            <td mat-cell *matCellDef="let p">
              <span class="badge" [class]="p.direction === 'BUY' ? 'buy' : 'sell'">{{ p.direction }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="pnl">
            <th mat-header-cell *matHeaderCellDef class="r"> PnL </th>
            <td mat-cell *matCellDef="let p" class="r mono" [class]="pnlClass(p.unrealizedPnl)">
              {{ formatPnl(p.unrealizedPnl) }}
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="positionColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: positionColumns;"></tr>
        </table>
        @if (state.positions().length === 0) {
          <div class="empty-row">No open positions</div>
        }
      </at-status-card>

      <!-- Session Stats -->
      <at-status-card title="Session Stats" icon="bar_chart" iconColor="var(--info)">
        <at-metric label="Trades Today" [value]="state.recentTrades().length" />
        <at-metric label="Winners" [value]="winners()" [valueClass]="'profit'" />
        <at-metric label="Win Rate" [value]="winRate()" />
        <at-metric label="Realized PnL" [value]="formatPnl(totalPnl())" [valueClass]="pnlClass(totalPnl())" />
        <at-metric label="Best Trade" [value]="formatPnl(bestPnl())" [valueClass]="'profit'" />
      </at-status-card>

      <!-- Risk Utilization -->
      <at-status-card title="Risk Utilization" icon="shield" iconColor="var(--warning)">
        <at-metric label="Daily PnL" [value]="formatPnl(state.dailyPnl())" [valueClass]="pnlClass(state.dailyPnl())" />
        <at-metric label="Loss Limit" [value]="formatPnl(-1 * (state.risk()?.maxDailyLossInr ?? 0))" />
        <div class="bar-section">
          <div class="bar-label">Loss Utilization</div>
          <div class="bar-container">
            <div class="bar-fill" [style.width.%]="lossUtilPct()" [class]="barClass()"></div>
          </div>
          <div class="bar-value">{{ lossUtilPct().toFixed(0) }}%</div>
        </div>
        <at-metric label="Kill Switch" [value]="state.isKillSwitchActive() ? 'ON' : 'OFF'"
                   [valueClass]="state.isKillSwitchActive() ? 'loss' : ''" />
      </at-status-card>
    </div>

    <!-- Recent Exits -->
    <at-status-card title="Recent Exits" icon="logout" iconColor="var(--text-secondary)"
                    style="margin-top: 16px">
      <table mat-table [dataSource]="recentExits()" class="mat-elevation-z0">
        <ng-container matColumnDef="exitTime">
          <th mat-header-cell *matHeaderCellDef> Time </th>
          <td mat-cell *matCellDef="let t" class="mono text-muted"> {{ formatTime(t.exitTime) }} </td>
        </ng-container>

        <ng-container matColumnDef="symbol">
          <th mat-header-cell *matHeaderCellDef> Symbol </th>
          <td mat-cell *matCellDef="let t" class="mono"> {{ shortSymbol(t.symbol) }} </td>
        </ng-container>

        <ng-container matColumnDef="direction">
          <th mat-header-cell *matHeaderCellDef> Dir </th>
          <td mat-cell *matCellDef="let t">
            <span class="badge" [class]="t.direction === 'BUY' ? 'buy' : 'sell'">{{ t.direction }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="pnl">
          <th mat-header-cell *matHeaderCellDef class="r"> PnL </th>
          <td mat-cell *matCellDef="let t" class="r mono" [class]="pnlClass(t.pnl)">
            {{ formatPnl(t.pnl) }}
          </td>
        </ng-container>

        <ng-container matColumnDef="reason">
          <th mat-header-cell *matHeaderCellDef> Reason </th>
          <td mat-cell *matCellDef="let t" class="text-muted"> {{ t.exitReason || '—' }} </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="exitColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: exitColumns;"></tr>
      </table>
      @if (recentExits().length === 0) {
        <div class="empty-row">No exits today</div>
      }
    </at-status-card>
  `,
  styles: [`
    .page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .live-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--profit); animation: pulse 2s infinite; }
    .live-dot.stopped { background: var(--loss); animation: none; }
    @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

    .paper-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }
    .r { text-align: right !important; justify-content: flex-end; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 11px; font-weight: 600; }
    .badge.buy { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .badge.sell { background: rgba(248, 81, 73, 0.15); color: var(--loss); }
    .empty-row { text-align: center; padding: 24px !important; color: var(--text-muted); font-size: 13px; }

    .bar-section { margin: 8px 0; }
    .bar-label { font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; }
    .bar-container { height: 6px; background: var(--bg-hover); border-radius: 3px; overflow: hidden; }
    .bar-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
    .bar-fill.safe { background: var(--profit); }
    .bar-fill.caution { background: var(--warning); }
    .bar-fill.danger { background: var(--loss); }
    .bar-value { font-size: 11px; color: var(--text-muted); margin-top: 2px; }

    .compact-table ::ng-deep .mat-mdc-cell { padding: 4px 8px !important; font-size: 12px; }
  `],
})
export class PaperTradeComponent {
  positionColumns = ['symbol', 'direction', 'pnl'];
  exitColumns = ['exitTime', 'symbol', 'direction', 'pnl', 'reason'];

  readonly winners = computed(() => this.state.recentTrades().filter(t => t.pnl != null && t.pnl > 0).length);
  readonly losers = computed(() => this.state.recentTrades().filter(t => t.pnl != null && t.pnl <= 0).length);
  readonly winRate = computed(() => {
    const total = this.winners() + this.losers();
    return total > 0 ? `${((this.winners() / total) * 100).toFixed(0)}%` : '—';
  });
  readonly totalPnl = computed(() => this.state.recentTrades().reduce((s, t) => s + (t.pnl ?? 0), 0));
  readonly bestPnl = computed(() => {
    const pnls = this.state.recentTrades().filter(t => t.pnl != null).map(t => t.pnl!);
    return pnls.length > 0 ? Math.max(...pnls) : null;
  });
  readonly recentExits = computed(() =>
    this.state.recentTrades().filter(t => t.status === 'CLOSED').slice(-5).reverse()
  );
  readonly lossUtilPct = computed(() => {
    const risk = this.state.risk();
    if (!risk || !risk.maxDailyLossInr) return 0;
    return Math.min(100, Math.abs(risk.dailyPnl) / risk.maxDailyLossInr * 100);
  });
  readonly barClass = computed(() => {
    const p = this.lossUtilPct();
    return p < 50 ? 'safe' : p < 80 ? 'caution' : 'danger';
  });

  constructor(public state: GlobalStateService) {}

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
}
