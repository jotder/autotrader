import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil, forkJoin, interval, switchMap, startWith, catchError, of } from 'rxjs';
import { ApiService } from '../core/services/api.service';
import { StatusResponse, RiskResponse, AnomalyStatus, CircuitBreakerStatus } from '../core/models/api.models';
import { environment } from '../../environments/environment';

@Component({
  selector: 'at-status-bar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="status-bar" [class.alert]="anomaly?.anomalyMode || anomaly?.killSwitchActive">
      <div class="status-item">
        <span class="label">Mode</span>
        <span class="value">{{ status?.mode || '—' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Running</span>
        <span class="dot" [class.green]="status?.running" [class.red]="!status?.running"></span>
        <span class="value">{{ status?.running ? 'Yes' : 'No' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Daily PnL</span>
        <span class="value mono" [class.profit]="(risk?.dailyPnl ?? 0) >= 0"
              [class.loss]="(risk?.dailyPnl ?? 0) < 0">
          {{ formatPnl(risk?.dailyPnl) }}
        </span>
      </div>
      <div class="status-item">
        <span class="label">Kill Switch</span>
        <span class="dot" [class.red]="anomaly?.killSwitchActive" [class.green]="!anomaly?.killSwitchActive"></span>
        <span class="value">{{ anomaly?.killSwitchActive ? 'ON' : 'OFF' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Anomaly</span>
        <span class="dot" [class.red]="anomaly?.anomalyMode" [class.green]="!anomaly?.anomalyMode"></span>
        <span class="value">{{ anomaly?.anomalyMode ? 'ACTIVE' : 'CLEAR' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Circuit</span>
        <span class="dot"
              [class.green]="cb?.state === 'CLOSED'"
              [class.amber]="cb?.state === 'HALF_OPEN'"
              [class.red]="cb?.state === 'OPEN'"></span>
        <span class="value">{{ cb?.state || '—' }}</span>
      </div>
      <div class="status-item last-updated">
        <span class="label">Updated</span>
        <span class="value mono" [class.warning]="isStale">{{ lastUpdated || '—' }}</span>
      </div>
    </div>
  `,
  styles: [`
    .status-bar {
      display: flex;
      align-items: center;
      gap: 20px;
      padding: 8px 16px;
      background: var(--bg-secondary);
      border-bottom: 1px solid var(--border);
      font-size: 13px;
      flex-wrap: wrap;
    }
    .status-bar.alert {
      border-bottom: 2px solid var(--loss);
      animation: pulse-border 2s infinite;
    }
    @keyframes pulse-border {
      0%, 100% { border-bottom-color: var(--loss); }
      50% { border-bottom-color: transparent; }
    }
    .status-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .label {
      color: var(--text-muted);
      font-size: 11px;
      text-transform: uppercase;
    }
    .value {
      color: var(--text-primary);
      font-weight: 500;
    }
    .last-updated {
      margin-left: auto;
    }
  `],
})
export class StatusBarComponent implements OnInit, OnDestroy {
  status: StatusResponse | null = null;
  risk: RiskResponse | null = null;
  anomaly: AnomalyStatus | null = null;
  cb: CircuitBreakerStatus | null = null;
  lastUpdated: string | null = null;
  isStale = false;

  private destroy$ = new Subject<void>();
  private lastUpdateTime: number = 0;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    interval(environment.pollingIntervalMs).pipe(
      startWith(0),
      switchMap(() => forkJoin({
        status: this.api.getStatus().pipe(catchError(() => of(null))),
        risk: this.api.getRisk().pipe(catchError(() => of(null))),
        anomaly: this.api.getAnomalyStatus().pipe(catchError(() => of(null))),
        cb: this.api.getCircuitBreakerStatus().pipe(catchError(() => of(null))),
      })),
      takeUntil(this.destroy$),
    ).subscribe(data => {
      if (data.status) this.status = data.status;
      if (data.risk) this.risk = data.risk;
      if (data.anomaly) this.anomaly = data.anomaly;
      if (data.cb) this.cb = data.cb;
      this.lastUpdateTime = Date.now();
      this.lastUpdated = new Date().toLocaleTimeString('en-IN', { hour12: false });
      this.isStale = false;
    });

    // Staleness check
    interval(1000).pipe(takeUntil(this.destroy$)).subscribe(() => {
      if (this.lastUpdateTime > 0) {
        this.isStale = (Date.now() - this.lastUpdateTime) > environment.staleThresholdMs;
      }
    });
  }

  formatPnl(value: number | undefined | null): string {
    if (value == null) return '—';
    const sign = value >= 0 ? '+' : '';
    return `${sign}₹${value.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
