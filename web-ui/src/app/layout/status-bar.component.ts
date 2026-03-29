import { Component, computed, signal, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GlobalStateService } from '../core/services/global-state.service';
import { ThemeService } from '../core/services/theme.service';
import { environment } from '../../environments/environment';
import { DxButtonModule } from 'devextreme-angular/ui/button';

@Component({
  selector: 'at-status-bar',
  standalone: true,
  imports: [CommonModule, DxButtonModule],
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
      
      <div class="status-item last-updated">
        <span class="label">Updated</span>
        <span class="value mono" [class.warning]="isStale()">{{ lastUpdatedText() }}</span>
      </div>

      <div class="theme-toggle">
        <dx-button
          [icon]="theme.theme() === 'dark' ? 'sun' : 'moon'"
          stylingMode="text"
          (onClick)="theme.toggleTheme()"
          [hint]="'Switch to ' + (theme.theme() === 'dark' ? 'light' : 'dark') + ' theme'">
        </dx-button>
      </div>
    </div>
  `,
  styles: [`
    .status-bar {
      display: flex;
      align-items: center;
      gap: 20px;
      padding: 4px 16px;
      border-bottom: 1px solid rgba(255,255,255,0.1);
      font-size: 13px;
    }
    .status-bar.alert {
      border-bottom: 2px solid #f85149;
    }
    .status-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .label {
      opacity: 0.6;
      font-size: 11px;
      text-transform: uppercase;
    }
    .value {
      font-weight: 500;
    }
    .last-updated {
      margin-left: auto;
    }
    .theme-toggle {
      margin-left: 10px;
    }
    ::ng-deep .theme-toggle .dx-button {
      height: 32px;
      width: 32px;
    }
  `],
})
export class StatusBarComponent {
  public state = inject(GlobalStateService);
  public theme = inject(ThemeService);

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

function signalInterval<T>(ms: number, factory: () => T) {
  const s = signal<T>(factory());
  setInterval(() => s.set(factory()), ms);
  return s;
}
