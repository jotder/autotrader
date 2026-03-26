import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil, forkJoin, interval, switchMap, startWith, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import {
  StatusResponse, RiskResponse, HealthResponse,
  AnomalyStatus, CircuitBreakerStatus, TokenStatus,
} from '../../core/models/api.models';
import { AlertBannerComponent } from '../../shared/components/alert-banner.component';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'at-dashboard',
  standalone: true,
  imports: [CommonModule, AlertBannerComponent, StatusCardComponent, MetricRowComponent],
  template: `
    <div class="page-header">
      <h1>Dashboard</h1>
    </div>

    <at-alert-banner [anomaly]="anomaly" [cb]="cb" [risk]="risk" />

    <div class="card-grid">
      <!-- System Status -->
      <at-status-card title="System" icon="computer" [iconColor]="status?.running ? 'var(--profit)' : 'var(--loss)'">
        <at-metric label="Mode" [value]="status?.mode || '—'" />
        <at-metric label="Running" [value]="status?.running ? 'Yes' : 'No'"
                   [valueClass]="status?.running ? 'profit' : 'loss'" />
        <at-metric label="Symbols" [value]="status?.symbols?.length || 0" />
      </at-status-card>

      <!-- Risk Summary -->
      <at-status-card title="Risk" icon="shield"
                      [iconColor]="(risk?.dailyPnl ?? 0) >= 0 ? 'var(--profit)' : 'var(--loss)'">
        <at-metric label="Daily PnL" [value]="formatPnl(risk?.dailyPnl)"
                   [valueClass]="pnlClass(risk?.dailyPnl)" />
        <at-metric label="Loss Limit" [value]="formatPnl(-1 * (risk?.maxDailyLossInr ?? 0))" />
        <at-metric label="Profit Lock" [value]="risk?.dailyProfitLocked ? 'LOCKED' : 'Open'"
                   [valueClass]="risk?.dailyProfitLocked ? 'warning' : ''" />
        <div class="loss-bar-container">
          <div class="loss-bar" [style.width.%]="lossUtilPct" [class]="lossBarClass"></div>
        </div>
        <span class="text-muted" style="font-size:11px">Loss utilization: {{ lossUtilPct.toFixed(0) }}%</span>
      </at-status-card>

      <!-- Health -->
      <at-status-card title="Health" icon="monitor_heart" iconColor="var(--info)">
        @if (health?.components) {
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
        <at-metric label="State" [value]="cb?.state || '—'"
                   [valueClass]="cbStateClass" />
        <at-metric label="Failures" [value]="cb?.consecutiveFailures ?? '—'" />
        <at-metric label="429 Today" [value]="cb?.daily429Count ?? '—'" />
        <at-metric label="Last Failure" [value]="formatTime(cb?.lastFailureTime)" />
      </at-status-card>

      <!-- Anomaly -->
      <at-status-card title="Anomaly Protection" icon="security"
                      [iconColor]="anomaly?.anomalyMode ? 'var(--loss)' : 'var(--profit)'">
        <at-metric label="Status" [value]="anomaly?.anomalyMode ? 'ACTIVE' : 'Clear'"
                   [valueClass]="anomaly?.anomalyMode ? 'loss' : 'profit'" />
        <at-metric label="Kill Switch" [value]="anomaly?.killSwitchActive ? 'ON' : 'OFF'"
                   [valueClass]="anomaly?.killSwitchActive ? 'loss' : ''" />
        <at-metric label="Broker Errors" [value]="anomaly?.consecutiveBrokerErrors ?? '—'" />
        @if (anomaly?.reason) {
          <at-metric label="Reason" [value]="anomaly!.reason!" [mono]="false" />
        }
      </at-status-card>

      <!-- Token -->
      <at-status-card title="Token" icon="vpn_key"
                      [iconColor]="token?.tokenValid ? 'var(--profit)' : 'var(--loss)'">
        <at-metric label="Valid" [value]="token?.tokenValid ? 'Yes' : 'No'"
                   [valueClass]="token?.tokenValid ? 'profit' : 'loss'" />
        <at-metric label="Scheduler" [value]="token?.schedulerRunning ? 'Running' : 'Stopped'" />
        <at-metric label="Last Refresh" [value]="token?.lastRefreshStatus || '—'" />
        <at-metric label="Refreshed At" [value]="formatTime(token?.lastRefreshTime)" />
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
export class DashboardComponent implements OnInit, OnDestroy {
  status: StatusResponse | null = null;
  risk: RiskResponse | null = null;
  health: HealthResponse | null = null;
  anomaly: AnomalyStatus | null = null;
  cb: CircuitBreakerStatus | null = null;
  token: TokenStatus | null = null;

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    interval(environment.pollingIntervalMs).pipe(
      startWith(0),
      switchMap(() => forkJoin({
        status: this.api.getStatus().pipe(catchError(() => of(null))),
        risk: this.api.getRisk().pipe(catchError(() => of(null))),
        health: this.api.getHealth().pipe(catchError(() => of(null))),
        anomaly: this.api.getAnomalyStatus().pipe(catchError(() => of(null))),
        cb: this.api.getCircuitBreakerStatus().pipe(catchError(() => of(null))),
        token: this.api.getTokenStatus().pipe(catchError(() => of(null))),
      })),
      takeUntil(this.destroy$),
    ).subscribe(data => {
      if (data.status) this.status = data.status;
      if (data.risk) this.risk = data.risk;
      if (data.health) this.health = data.health;
      if (data.anomaly) this.anomaly = data.anomaly;
      if (data.cb) this.cb = data.cb;
      if (data.token) this.token = data.token;
    });
  }

  // ── Helpers ──────────────────────────────────────────────────
  get lossUtilPct(): number {
    if (!this.risk || !this.risk.maxDailyLossInr) return 0;
    return Math.min(100, Math.abs(this.risk.dailyPnl) / this.risk.maxDailyLossInr * 100);
  }

  get lossBarClass(): string {
    const pct = this.lossUtilPct;
    if (pct < 50) return 'safe';
    if (pct < 80) return 'caution';
    return 'danger';
  }

  get healthEntries(): [string, any][] {
    if (!this.health?.components) return [];
    return Object.entries(this.health.components);
  }

  get cbIconColor(): string {
    if (!this.cb) return 'var(--text-secondary)';
    return this.cb.state === 'CLOSED' ? 'var(--profit)' :
           this.cb.state === 'OPEN' ? 'var(--loss)' : 'var(--warning)';
  }

  get cbStateClass(): string {
    if (!this.cb) return '';
    return this.cb.state === 'CLOSED' ? 'profit' :
           this.cb.state === 'OPEN' ? 'loss' : 'warning';
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
