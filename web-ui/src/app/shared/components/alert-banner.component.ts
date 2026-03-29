import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { AnomalyStatus, CircuitBreakerStatus, RiskResponse } from '../../core/models/api.models';

export interface Alert {
  severity: 'critical' | 'warning';
  icon: string;
  message: string;
}

@Component({
  selector: 'at-alert-banner',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    @if (alerts.length > 0) {
      <div class="alert-banner" [class.critical]="hasCritical">
        @for (alert of alerts; track alert.message) {
          <div class="alert-item" [class]="alert.severity">
            <mat-icon>{{ alert.icon }}</mat-icon>
            <span class="alert-msg">{{ alert.message }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .alert-banner {
      padding: 8px 16px;
      border-radius: 8px;
      margin-bottom: 16px;
      border: 1px solid var(--warning);
      background: rgba(210, 153, 34, 0.08);
    }
    .alert-banner.critical {
      border-color: var(--loss);
      background: rgba(248, 81, 73, 0.08);
      animation: pulse-bg 2s infinite;
    }
    @keyframes pulse-bg {
      0%, 100% { background: rgba(248, 81, 73, 0.08); }
      50% { background: rgba(248, 81, 73, 0.15); }
    }
    .alert-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 0;
      font-size: 13px;
    }
    .alert-item.critical { color: var(--loss); }
    .alert-item.critical mat-icon { color: var(--loss); }
    .alert-item.warning { color: var(--warning); }
    .alert-item.warning mat-icon { color: var(--warning); }
    .alert-msg { flex: 1; }
    mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `],
})
export class AlertBannerComponent {
  @Input() anomaly: AnomalyStatus | null = null;
  @Input() cb: CircuitBreakerStatus | null = null;
  @Input() risk: RiskResponse | null = null;

  get alerts(): Alert[] {
    const list: Alert[] = [];

    if (this.anomaly?.anomalyMode) {
      list.push({
        severity: 'critical',
        icon: 'warning',
        message: `ANOMALY ACTIVE: ${this.anomaly.reason || 'Unknown reason'} — manual restart required`,
      });
    }

    if (this.anomaly?.killSwitchActive && !this.anomaly?.anomalyMode) {
      list.push({
        severity: 'critical',
        icon: 'block',
        message: 'Kill switch ON — all new entries blocked',
      });
    }

    if (this.cb?.state === 'OPEN') {
      list.push({
        severity: 'critical',
        icon: 'power_off',
        message: `Circuit breaker OPEN — broker API unavailable (${this.cb.consecutiveFailures} failures)`,
      });
    }

    if (this.cb?.state === 'HALF_OPEN') {
      list.push({
        severity: 'warning',
        icon: 'sync',
        message: 'Circuit breaker HALF_OPEN — probing broker API',
      });
    }

    if (this.risk && this.risk.dailyPnl <= -this.risk.maxDailyLossInr * 0.8) {
      list.push({
        severity: 'warning',
        icon: 'trending_down',
        message: `Daily loss approaching limit: ₹${Math.abs(this.risk.dailyPnl).toFixed(0)} / ₹${this.risk.maxDailyLossInr.toFixed(0)}`,
      });
    }

    return list;
  }

  get hasCritical(): boolean {
    return this.alerts.some(a => a.severity === 'critical');
  }
}
