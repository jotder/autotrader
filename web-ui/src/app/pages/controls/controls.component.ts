import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ActionResponse } from '../../core/models/api.models';
import { MatIconModule } from '@angular/material/icon';
import { environment } from '../../../environments/environment';

interface ControlAction {
  id: string;
  title: string;
  description: string;
  icon: string;
  color: string;
  buttonLabel: string;
  buttonClass: string;
  requireType?: string;
  lastResult?: string;
  pending: boolean;
}

@Component({
  selector: 'at-controls',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1>Controls</h1>
    </div>

    <div class="controls-grid">
      @for (action of actions; track action.id) {
        <div class="control-card at-card" [class.critical]="action.buttonClass === 'btn-danger'">
          <div class="control-header">
            <mat-icon [style.color]="action.color">{{ action.icon }}</mat-icon>
            <h3>{{ action.title }}</h3>
          </div>
          <p class="control-desc">{{ action.description }}</p>
          <button [class]="action.buttonClass" (click)="triggerAction(action)" [disabled]="action.pending">
            {{ action.pending ? 'Processing…' : action.buttonLabel }}
          </button>
          @if (action.lastResult) {
            <div class="control-result" [class.success]="action.lastResult.startsWith('✓')">
              {{ action.lastResult }}
            </div>
          }
        </div>
      }
    </div>

    <!-- Confirmation dialog -->
    @if (confirmAction) {
      <div class="overlay" (click)="confirmAction = null">
        <div class="confirm-dialog" (click)="$event.stopPropagation()">
          <h3>{{ confirmAction.title }}</h3>
          <p>{{ confirmAction.description }}</p>
          @if (confirmAction.requireType) {
            <input class="confirm-input" [(ngModel)]="confirmTyped"
                   [placeholder]="'Type ' + confirmAction.requireType + ' to confirm'" />
          }
          <div class="confirm-actions">
            <button class="btn-secondary" (click)="confirmAction = null; confirmTyped = ''">Cancel</button>
            <button [class]="confirmAction.buttonClass" (click)="executeAction()"
                    [disabled]="confirmAction.requireType != null && confirmTyped !== confirmAction.requireType">
              {{ confirmAction.buttonLabel }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .page-header { margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .controls-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }

    .control-card { padding: 20px; }
    .control-card.critical { border-color: rgba(248, 81, 73, 0.3); }
    .control-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
    .control-header h3 { margin: 0; font-size: 14px; font-weight: 500; }
    .control-desc { color: var(--text-secondary); font-size: 12px; margin: 0 0 12px; line-height: 1.4; }

    .btn-danger { background: var(--loss); color: #fff; border: none; padding: 8px 20px; border-radius: 4px; cursor: pointer; font-size: 13px; width: 100%; }
    .btn-danger:hover { opacity: 0.85; }
    .btn-danger:disabled { opacity: 0.4; cursor: not-allowed; }
    .btn-warning { background: var(--warning); color: #000; border: none; padding: 8px 20px; border-radius: 4px; cursor: pointer; font-size: 13px; width: 100%; }
    .btn-warning:hover { opacity: 0.85; }
    .btn-warning:disabled { opacity: 0.4; cursor: not-allowed; }
    .btn-info { background: var(--info); color: #fff; border: none; padding: 8px 20px; border-radius: 4px; cursor: pointer; font-size: 13px; width: 100%; }
    .btn-info:hover { opacity: 0.85; }
    .btn-info:disabled { opacity: 0.4; cursor: not-allowed; }

    .control-result { font-size: 11px; color: var(--text-muted); margin-top: 8px; }
    .control-result.success { color: var(--profit); }

    .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 24px; max-width: 420px; width: 90%; }
    .confirm-dialog h3 { margin: 0 0 8px; font-size: 16px; }
    .confirm-dialog p { color: var(--text-secondary); font-size: 13px; margin: 0 0 16px; }
    .confirm-input { width: 100%; padding: 8px; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 4px; color: var(--text-primary); font-family: var(--font-mono); margin-bottom: 16px; box-sizing: border-box; }
    .confirm-actions { display: flex; gap: 8px; justify-content: flex-end; }
    .btn-secondary { background: transparent; border: 1px solid var(--border); color: var(--text-primary); padding: 8px 20px; border-radius: 4px; cursor: pointer; font-size: 13px; }
  `],
})
export class ControlsComponent {
  confirmAction: ControlAction | null = null;
  confirmTyped = '';

  actions: ControlAction[] = [
    {
      id: 'kill', title: 'Kill Switch', icon: 'block', color: 'var(--loss)',
      description: 'Halt all new trade entries. Existing positions stay open with their SL/TP.',
      buttonLabel: 'Activate Kill Switch', buttonClass: 'btn-danger', pending: false,
    },
    {
      id: 'flatten', title: 'Emergency Flatten', icon: 'warning', color: 'var(--loss)',
      description: 'Close ALL open positions immediately and activate anomaly mode. Cannot be undone.',
      buttonLabel: 'Flatten All Positions', buttonClass: 'btn-danger',
      requireType: environment.confirmFlattenWord, pending: false,
    },
    {
      id: 'anomaly', title: 'Acknowledge Anomaly', icon: 'security', color: 'var(--warning)',
      description: 'Clear anomaly mode after reviewing the cause. Must be done before resuming trading.',
      buttonLabel: 'Acknowledge Anomaly', buttonClass: 'btn-warning', pending: false,
    },
    {
      id: 'reset', title: 'Reset Day', icon: 'refresh', color: 'var(--info)',
      description: 'Reset all daily risk counters: PnL, kill switch, profit lock, consecutive losses.',
      buttonLabel: 'Reset Day Counters', buttonClass: 'btn-info', pending: false,
    },
    {
      id: 'cb-reset', title: 'Reset Circuit Breaker', icon: 'electrical_services', color: 'var(--info)',
      description: 'Force circuit breaker to CLOSED state, clearing failure counters.',
      buttonLabel: 'Reset Circuit Breaker', buttonClass: 'btn-info', pending: false,
    },
    {
      id: 'token', title: 'Refresh Token', icon: 'vpn_key', color: 'var(--info)',
      description: 'Force an immediate token refresh with the broker API.',
      buttonLabel: 'Refresh Token Now', buttonClass: 'btn-info', pending: false,
    },
  ];

  constructor(private api: ApiService) {}

  triggerAction(action: ControlAction): void {
    this.confirmAction = action;
    this.confirmTyped = '';
  }

  executeAction(): void {
    if (!this.confirmAction) return;
    const action = this.confirmAction;
    action.pending = true;
    this.confirmAction = null;
    this.confirmTyped = '';

    let call;
    switch (action.id) {
      case 'kill': call = this.api.activateKillSwitch(); break;
      case 'flatten': call = this.api.emergencyFlatten(); break;
      case 'anomaly': call = this.api.acknowledgeAnomaly(); break;
      case 'reset': call = this.api.resetDay(); break;
      case 'cb-reset': call = this.api.resetCircuitBreaker(); break;
      case 'token': call = this.api.refreshToken(); break;
      default: return;
    }

    call.subscribe({
      next: (r: ActionResponse) => {
        action.pending = false;
        action.lastResult = r.success ? `✓ ${r.message}` : `✗ ${r.message}`;
      },
      error: (e: any) => {
        action.pending = false;
        action.lastResult = `✗ Error: ${e.message || 'Request failed'}`;
      },
    });
  }
}
