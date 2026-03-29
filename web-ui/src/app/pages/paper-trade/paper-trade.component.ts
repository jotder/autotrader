import { Component, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GlobalStateService } from '../../core/services/global-state.service';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';
import { MatIconModule } from '@angular/material/icon';

// DevExtreme
import { DxDataGridModule } from 'devextreme-angular/ui/data-grid';
import { DxProgressBarModule } from 'devextreme-angular/ui/progress-bar';

@Component({
  selector: 'at-paper-trade',
  standalone: true,
  imports: [
    CommonModule, StatusCardComponent, MetricRowComponent, MatIconModule,
    DxDataGridModule, DxProgressBarModule
  ],
  template: `
    <div class="page-header">
      <h1>Paper Trade Center</h1>
      <span class="live-dot" [class.stopped]="!state.isRunning()"></span>
    </div>

    <div class="paper-grid">
      <!-- Active Positions -->
      <at-status-card title="Active Positions" icon="show_chart"
                      [iconColor]="state.positions().length > 0 ? 'var(--accent)' : 'var(--text-secondary)'">
        <dx-data-grid
          [dataSource]="state.positions()"
          [showBorders]="false"
          [rowAlternationEnabled]="true"
          [columnAutoWidth]="true">
          <dxi-column dataField="symbol" cellTemplate="symbolTemplate"></dxi-column>
          <dxi-column dataField="direction" cellTemplate="dirTemplate" [width]="60"></dxi-column>
          <dxi-column dataField="unrealizedPnl" caption="PnL" cellTemplate="pnlTemplate" alignment="right"></dxi-column>

          <div *dxTemplate="let data of 'symbolTemplate'">
            <span class="mono">{{ shortSymbol(data.value) }}</span>
          </div>
          <div *dxTemplate="let data of 'dirTemplate'">
            <span class="badge" [class.buy]="data.value === 'BUY'" [class.sell]="data.value === 'SELL'">{{ data.value.charAt(0) }}</span>
          </div>
          <div *dxTemplate="let data of 'pnlTemplate'">
            <span class="mono font-bold" [class.profit]="data.value >= 0" [class.loss]="data.value < 0">
              {{ formatPnl(data.value) }}
            </span>
          </div>
        </dx-data-grid>
      </at-status-card>

      <!-- Session Stats -->
      <at-status-card title="Session Stats" icon="bar_chart" iconColor="var(--info)">
        <at-metric label="Trades Today" [value]="state.recentTrades().length" />
        <at-metric label="Winners" [value]="winners()" [valueClass]="'profit'" />
        <at-metric label="Win Rate" [value]="winRate()" />
        <at-metric label="Realized PnL" [value]="formatPnl(totalPnl())" [valueClass]="totalPnl() >= 0 ? 'profit' : 'loss'" />
        <at-metric label="Best Trade" [value]="formatPnl(bestPnl())" [valueClass]="'profit'" />
      </at-status-card>

      <!-- Risk Utilization -->
      <at-status-card title="Risk Utilization" icon="shield" iconColor="var(--warning)">
        <at-metric label="Daily PnL" [value]="formatPnl(state.dailyPnl())" [valueClass]="state.dailyPnl() >= 0 ? 'profit' : 'loss'" />
        <at-metric label="Loss Limit" [value]="formatPnl(-1 * (state.risk()?.maxDailyLossInr ?? 0))" />
        
        <div class="dx-field">
          <div class="dx-field-label">Loss Utilization</div>
          <div class="dx-field-value">
            <dx-progress-bar
              [min]="0"
              [max]="100"
              [value]="lossUtilPct()"
              [class]="barClass()">
            </dx-progress-bar>
          </div>
        </div>

        <at-metric label="Kill Switch" [value]="state.isKillSwitchActive() ? 'ON' : 'OFF'"
                   [valueClass]="state.isKillSwitchActive() ? 'loss' : ''" />
      </at-status-card>
    </div>

    <!-- Recent Exits -->
    <at-status-card title="Recent Exits" icon="logout" iconColor="var(--text-secondary)"
                    style="margin-top: 16px">
      <dx-data-grid
        [dataSource]="recentExits()"
        [showBorders]="false"
        [rowAlternationEnabled]="true"
        [columnAutoWidth]="true">
        <dxi-column dataField="exitTime" caption="Time" dataType="datetime" format="HH:mm:ss" [width]="90"></dxi-column>
        <dxi-column dataField="symbol" cellTemplate="symbolTemplate"></dxi-column>
        <dxi-column dataField="direction" cellTemplate="dirTemplate" [width]="60"></dxi-column>
        <dxi-column dataField="pnl" cellTemplate="pnlTemplate" alignment="right"></dxi-column>
        <dxi-column dataField="exitReason" caption="Reason"></dxi-column>

        <div *dxTemplate="let data of 'symbolTemplate'">
          <span class="mono">{{ shortSymbol(data.value) }}</span>
        </div>
        <div *dxTemplate="let data of 'dirTemplate'">
          <span class="badge" [class.buy]="data.value === 'BUY'" [class.sell]="data.value === 'SELL'">{{ data.value.charAt(0) }}</span>
        </div>
        <div *dxTemplate="let data of 'pnlTemplate'">
          <span class="mono font-bold" [class.profit]="data.value >= 0" [class.loss]="data.value < 0">
            {{ formatPnl(data.value) }}
          </span>
        </div>
      </dx-data-grid>
    </at-status-card>
  `,
  styles: [`
    .page-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .live-dot { width: 8px; height: 8px; border-radius: 50%; background: #3fb950; animation: pulse 2s infinite; }
    .live-dot.stopped { background: #f85149; animation: none; }
    @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

    .paper-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; background: rgba(255,255,255,0.1); }
    .badge.buy { color: var(--profit); }
    .badge.sell { color: var(--loss); }

    .dx-field { margin-top: 8px; }
    .dx-field-label { font-size: 11px; opacity: 0.7; }

    ::ng-deep .safe .dx-progressbar-range { background-color: #3fb950; }
    ::ng-deep .caution .dx-progressbar-range { background-color: #d29922; }
    ::ng-deep .danger .dx-progressbar-range { background-color: #f85149; }

    ::ng-deep .dx-datagrid { background-color: transparent !important; }
  `],
})
export class PaperTradeComponent {
  readonly winners = computed(() => this.state.recentTrades().filter(t => t.pnl != null && t.pnl > 0).length);
  readonly losers = computed(() => this.state.recentTrades().filter(t => t.pnl != null && t.pnl <= 0).length);
  readonly winRate = computed(() => {
    const total = this.winners() + this.losers();
    return total > 0 ? `${((this.winners() / total) * 100).toFixed(0)}%` : '—';
  });
  readonly totalPnl = computed(() => this.state.recentTrades().reduce((s, t) => s + (t.pnl ?? 0), 0));
  readonly bestPnl = computed(() => {
    const pnls = this.state.recentTrades().filter(t => t.pnl != null).map(t => t.pnl!);
    return pnls.length > 0 ? Math.max(...pnls) : 0;
  });
  readonly recentExits = computed(() =>
    this.state.recentTrades().filter(t => t.status === 'CLOSED').slice(-10).reverse()
  );
  readonly lossUtilPct = computed(() => {
    const risk = this.state.risk();
    if (!risk || !risk.maxDailyLossInr) return 0;
    return Math.min(100, Math.abs(this.state.dailyPnl()) / risk.maxDailyLossInr * 100);
  });
  readonly barClass = computed(() => {
    const p = this.lossUtilPct();
    return p < 50 ? 'safe' : p < 80 ? 'caution' : 'danger';
  });

  constructor(public state: GlobalStateService) {}

  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
  formatPnl(v: number | null | undefined): string {
    if (v == null) return '—';
    return `${v >= 0 ? '+' : ''}₹${v.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  }
}
