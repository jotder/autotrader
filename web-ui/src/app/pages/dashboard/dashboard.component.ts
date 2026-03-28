import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GlobalStateService } from '../../core/services/global-state.service';
import { AlertBannerComponent } from '../../shared/components/alert-banner.component';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';

@Component({
  selector: 'at-dashboard',
  standalone: true,
  imports: [CommonModule, AlertBannerComponent, StatusCardComponent, MetricRowComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <h1>Dashboard</h1>
    </div>

    <at-alert-banner [anomaly]="state.anomaly()" [cb]="state.circuitBreaker()" [risk]="state.risk()" />

    <div class="card-grid">
      <!-- System Status -->
      <at-status-card title="System" icon="computer" [iconColor]="state.isRunning() ? 'var(--profit)' : 'var(--loss)'">
        <at-metric label="Mode" [value]="state.executionMode()" />
        <at-metric label="Running" [value]="state.isRunning() ? 'Yes' : 'No'"
                   [valueClass]="state.isRunning() ? 'profit' : 'loss'" />
        <at-metric label="Symbols" [value]="state.status()?.symbols?.length || 0" />
      </at-status-card>

      <!-- Risk Summary -->
      <at-status-card title="Risk" icon="shield"
                      [iconColor]="state.dailyPnl() >= 0 ? 'var(--profit)' : 'var(--loss)'">
        <at-metric label="Daily PnL" [value]="formatPnl(state.dailyPnl())"
                   [valueClass]="pnlClass(state.dailyPnl())" />
        <at-metric label="Loss Limit" [value]="formatPnl(-1 * (state.risk()?.maxDailyLossInr ?? 0))" />
        <at-metric label="Profit Lock" [value]="state.risk()?.dailyProfitLocked ? 'LOCKED' : 'Open'"
                   [valueClass]="state.risk()?.dailyProfitLocked ? 'warning' : ''" />
        <div class="loss-bar-container">
          <div class="loss-bar" [style.width.%]="lossUtilPct" [class]="lossBarClass"></div>
        </div>
        <span class="text-muted" style="font-size:11px">Loss utilization: {{ lossUtilPct.toFixed(0) }}%</span>
      </at-status-card>

      <!-- Health -->
      <at-status-card title="Health" icon="monitor_heart" iconColor="var(--info)">
        @if (state.health()?.components) {
          @for (entry of healthEntries; track entry[0]) {
            <div class="health-row">
              <span class="dot" [class]="healthDotClass(entry[1].status)"></span>
              <span class="health-name">{{ entry[0] }}</span>
              <span class="health-status text-muted">{{ entry[1].status }}</span>
            </div>
          }
        } @else {
          <span class="text-muted">No health data</span>
        }
      </at-status-card>

      <!-- Circuit Breaker -->
      <at-status-card title="Circuit Breaker" icon="electrical_services"
                      [iconColor]="cbIconColor">
        <at-metric label="State" [value]="state.circuitBreaker()?.state || '—'"
                   [valueClass]="cbStateClass" />
        <at-metric label="Failures" [value]="state.circuitBreaker()?.consecutiveFailures ?? '—'" />
        <at-metric label="429 Today" [value]="state.circuitBreaker()?.daily429Count ?? '—'" />
        <at-metric label="Last Failure" [value]="formatTime(state.circuitBreaker()?.lastFailureTime)" />
      </at-status-card>

      <!-- Anomaly -->
      <at-status-card title="Anomaly Protection" icon="security"
                      [iconColor]="state.isAnomalyMode() ? 'var(--loss)' : 'var(--profit)'">
        <at-metric label="Status" [value]="state.isAnomalyMode() ? 'ACTIVE' : 'Clear'"
                   [valueClass]="state.isAnomalyMode() ? 'loss' : 'profit'" />
        <at-metric label="Kill Switch" [value]="state.isKillSwitchActive() ? 'ON' : 'OFF'"
                   [valueClass]="state.isKillSwitchActive() ? 'loss' : ''" />
        <at-metric label="Broker Errors" [value]="state.anomaly()?.consecutiveBrokerErrors ?? '—'" />
        @if (state.anomaly()?.reason) {
          <at-metric label="Reason" [value]="state.anomaly()!.reason!" [mono]="false" />
        }
      </at-status-card>

      <!-- Token -->
      <at-status-card title="Token" icon="vpn_key"
                      [iconColor]="state.token()?.tokenValid ? 'var(--profit)' : 'var(--loss)'">
        <at-metric label="Valid" [value]="state.token()?.tokenValid ? 'Yes' : 'No'"
                   [valueClass]="state.token()?.tokenValid ? 'profit' : 'loss'" />
        <at-metric label="Scheduler" [value]="state.token()?.schedulerRunning ? 'Running' : 'Stopped'" />
        <at-metric label="Last Refresh" [value]="state.token()?.lastRefreshStatus || '—'" />
        <at-metric label="Refreshed At" [value]="formatTime(state.token()?.lastRefreshTime)" />
      </at-status-card>
    </div>
  `,
  styles: [`
    .page-header { margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .card-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
    }

    .loss-bar-container {
      height: 4px;
      background: var(--bg-hover);
      border-radius: 2px;
      margin: 8px 0 4px;
      overflow: hidden;
    }
    .loss-bar {
      height: 100%;
      border-radius: 2px;
      transition: width 0.3s ease;
    }
    .loss-bar.safe { background: var(--profit); }
    .loss-bar.caution { background: var(--warning); }
    .loss-bar.danger { background: var(--loss); }

    .health-row {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 2px 0;
      font-size: 12px;
    }
    .health-name {
      flex: 1;
      color: var(--text-primary);
    }
    .health-status {
      font-size: 11px;
    }
  `],
})
export class DashboardComponent {
  constructor(public state: GlobalStateService) {}

  // ── Helpers ──────────────────────────────────────────────────
  get lossUtilPct(): number {
    const risk = this.state.risk();
    if (!risk || !risk.maxDailyLossInr) return 0;
    return Math.min(100, Math.abs(risk.dailyPnl) / risk.maxDailyLossInr * 100);
  }

  get lossBarClass(): string {
    const pct = this.lossUtilPct;
    if (pct < 50) return 'safe';
    if (pct < 80) return 'caution';
    return 'danger';
  }

  get healthEntries(): [string, any][] {
    const health = this.state.health();
    if (!health?.components) return [];
    return Object.entries(health.components);
  }

  get cbIconColor(): string {
    const cb = this.state.circuitBreaker();
    if (!cb) return 'var(--text-secondary)';
    return cb.state === 'CLOSED' ? 'var(--profit)' :
           cb.state === 'OPEN' ? 'var(--loss)' : 'var(--warning)';
  }

  get cbStateClass(): string {
    const cb = this.state.circuitBreaker();
    if (!cb) return '';
    return cb.state === 'CLOSED' ? 'profit' :
           cb.state === 'OPEN' ? 'loss' : 'warning';
  }

  formatPnl(value: number | undefined | null): string {
    if (value == null) return '—';
    const sign = value >= 0 ? '+' : '';
    return `${sign}₹${value.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }

  pnlClass(value: number | undefined | null): string {
    if (value == null) return '';
    return value >= 0 ? 'profit' : 'loss';
  }

  formatTime(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return new Date(iso).toLocaleTimeString('en-IN', { hour12: false });
    } catch {
      return iso;
    }
  }

  healthDotClass(status: string): string {
    return status === 'UP' ? 'green' : status === 'DEGRADED' ? 'amber' : 'red';
  }
}
