import { Component, computed, signal, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GlobalStateService } from '../core/services/global-state.service';
import { environment } from '../../environments/environment';

@Component({
  selector: 'at-status-bar',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `

    <div class="status-bar" [class.alert]="state.isAnomalyMode() || state.isKillSwitchActive()">
      <div class="status-item">
        <span class="label">Mode</span>
        <span class="value">{{ state.executionMode() }}</span>
      </div>
      <div class="status-item">
        <span class="label">Running</span>
        <span class="dot" [class.green]="state.isRunning()" [class.red]="!state.isRunning()"></span>
        <span class="value">{{ state.isRunning() ? 'Yes' : 'No' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Daily PnL</span>
        <span class="value mono" [class.profit]="state.dailyPnl() >= 0"
              [class.loss]="state.dailyPnl() < 0">
          {{ formatPnl(state.dailyPnl()) }}
        </span>
      </div>
      <div class="status-item">
        <span class="label">Kill Switch</span>
        <span class="dot" [class.red]="state.isKillSwitchActive()" [class.green]="!state.isKillSwitchActive()"></span>
        <span class="value">{{ state.isKillSwitchActive() ? 'ON' : 'OFF' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Anomaly</span>
        <span class="dot" [class.red]="state.isAnomalyMode()" [class.green]="!state.isAnomalyMode()"></span>
        <span class="value">{{ state.isAnomalyMode() ? 'ACTIVE' : 'CLEAR' }}</span>
      </div>
      <div class="status-item">
        <span class="label">Circuit</span>
        <span class="dot"
              [class.green]="state.circuitBreaker()?.state === 'CLOSED'"
              [class.amber]="state.circuitBreaker()?.state === 'HALF_OPEN'"
              [class.red]="state.circuitBreaker()?.state === 'OPEN'"></span>
        <span class="value">{{ state.circuitBreaker()?.state || '—' }}</span>
      </div>
      <div class="status-item last-updated">
        <span class="label">Updated</span>
        <span class="value mono" [class.warning]="isStale()">{{ lastUpdatedText() }}</span>
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
export class StatusBarComponent {
  public state = inject(GlobalStateService);

  readonly lastUpdatedText = computed(() => {
    return this.state.lastUpdated().toLocaleTimeString('en-IN', { hour12: false });
  });

  readonly isStale = signalInterval(1000, () => {
    const lastUpdate = this.state.lastUpdated().getTime();
    return (Date.now() - lastUpdate) > environment.staleThresholdMs;
  });

  constructor() {}

  formatPnl(value: number | undefined | null): string {
    if (value == null) return '—';
    const sign = value >= 0 ? '+' : '';
    return `${sign}₹${value.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }
}

/**
 * Helper to create a signal that updates on an interval.
 * In a real app, this might be a utility.
 */
function signalInterval<T>(ms: number, factory: () => T) {
  const s = signal<T>(factory());
  setInterval(() => s.set(factory()), ms);
  return s;
}
