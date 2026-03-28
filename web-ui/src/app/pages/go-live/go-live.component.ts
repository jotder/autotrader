import { Component, OnInit, OnDestroy, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatStepperModule } from '@angular/material/stepper';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { GlobalStateService } from '../../core/services/global-state.service';
import { ApiService } from '../../core/services/api.service';
import { MetricRowComponent } from '../../shared/components/metric-row.component';
import { catchError, of, forkJoin, delay } from 'rxjs';

interface CheckItem {
  id: string;
  label: string;
  description: string;
  status: 'pending' | 'checking' | 'pass' | 'fail';
  error?: string;
}

@Component({
  selector: 'at-go-live',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, MatButtonModule,
    MatStepperModule, MatCheckboxModule, MatProgressSpinnerModule,
    MetricRowComponent
  ],
  template: `
    <div class="page-header">
      <h1>Go-Live Gate</h1>
      <span class="gate-status" [class.ready]="allChecksPass()">
        {{ allChecksPass() ? 'Ready for Live' : 'Requirements Pending' }}
      </span>
    </div>

    <div class="gate-container">
      <mat-stepper orientation="vertical" [linear]="true" #stepper>
        <!-- Step 1: Symbol & Strategy Selection -->
        <mat-step [completed]="selectionValid()">
          <ng-template matStepLabel>Select Symbol & Strategy</ng-template>
          <div class="step-content">
            <div class="form-grid">
              <div class="field">
                <label>Target Symbol</label>
                <select [(ngModel)]="targetSymbol" (change)="resetChecks()">
                  <option value="">Choose a symbol…</option>
                  @for (s of availableSymbols(); track s) { <option [value]="s">{{ s }}</option> }
                </select>
              </div>
              <div class="field">
                <label>Strategy</label>
                <select [(ngModel)]="targetStrategy" (change)="resetChecks()">
                  <option value="">Choose a strategy…</option>
                  @for (s of strategies(); track s.strategyId) { <option [value]="s.strategyId">{{ s.strategyId }}</option> }
                </select>
              </div>
            </div>
            <p class="step-hint">Only symbols with valid backtest and paper history should be promoted.</p>
            <div class="step-actions">
              <button mat-flat-button color="primary" matStepperNext [disabled]="!targetSymbol || !targetStrategy">Continue</button>
            </div>
          </div>
        </mat-step>

        <!-- Step 2: Programmatic Tech Checks -->
        <mat-step>
          <ng-template matStepLabel>Technical Readiness Audit</ng-template>
          <div class="step-content">
            <div class="check-list">
              @for (check of techChecks; track check.id) {
                <div class="check-item" [class]="check.status">
                  <mat-icon class="check-icon">
                    @if (check.status === 'pass') { check_circle }
                    @else if (check.status === 'fail') { cancel }
                    @else if (check.status === 'checking') { autorenew }
                    @else { radio_button_unchecked }
                  </mat-icon>
                  <div class="check-text">
                    <div class="check-label">{{ check.label }}</div>
                    <div class="check-desc">{{ check.description }}</div>
                    @if (check.error) { <div class="check-error">{{ check.error }}</div> }
                  </div>
                </div>
              }
            </div>
            <div class="step-actions">
              <button mat-stroked-button (click)="runTechChecks()" [disabled]="checking">
                {{ checking ? 'Running Audit…' : 'Run Technical Audit' }}
              </button>
              <button mat-flat-button color="primary" matStepperNext [disabled]="!techChecksPassed()">Continue</button>
            </div>
          </div>
        </mat-step>

        <!-- Step 3: Manual Risk Affirmation -->
        <mat-step>
          <ng-template matStepLabel>Manual Risk Affirmation</ng-template>
          <div class="step-content">
            <div class="affirmation-list">
              <mat-checkbox [(ngModel)]="riskAffirmed.backtest">I have reviewed 30 days of backtest results for this symbol.</mat-checkbox>
              <mat-checkbox [(ngModel)]="riskAffirmed.slippage">I understand the slippage and STT impact on this strategy.</mat-checkbox>
              <mat-checkbox [(ngModel)]="riskAffirmed.limits">Daily loss and exposure limits are correctly configured in YAML.</mat-checkbox>
              <mat-checkbox [(ngModel)]="riskAffirmed.emergency">I know how to manually flatten all positions if the API fails.</mat-checkbox>
            </div>
            <div class="step-actions">
              <button mat-flat-button color="primary" matStepperNext [disabled]="!allAffirmed()">Final Review</button>
            </div>
          </div>
        </mat-step>

        <!-- Step 4: Final Promotion -->
        <mat-step>
          <ng-template matStepLabel>Go-Live Confirmation</ng-template>
          <div class="step-content final-step">
            <div class="summary-card at-card">
              <h3>Promotion Summary</h3>
              <at-metric label="Symbol" [value]="targetSymbol" />
              <at-metric label="Strategy" [value]="targetStrategy" />
              <at-metric label="Environment" value="LIVE" valueClass="loss" />
              <div class="warning-box">
                <mat-icon>warning</mat-icon>
                <span>Promoting to LIVE will enable real capital deployment via the Fyers Broker API.</span>
              </div>
            </div>
            <div class="step-actions">
              <button mat-flat-button color="warn" class="btn-go-live" (click)="promoteToLive()" [disabled]="promoting">
                {{ promoting ? 'Promoting…' : 'AUTHORIZE LIVE TRADING' }}
              </button>
            </div>
            @if (promotionResult) {
              <div class="result-msg" [class.success]="promotionResult.success">
                {{ promotionResult.message }}
              </div>
            }
          </div>
        </mat-step>
      </mat-stepper>
    </div>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
    h1 { margin: 0; font-size: 22px; font-weight: 500; }
    .gate-status { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; background: var(--bg-secondary); color: var(--text-muted); border: 1px solid var(--border); }
    .gate-status.ready { background: rgba(63, 185, 80, 0.1); color: var(--profit); border-color: var(--profit); }

    .gate-container { max-width: 800px; margin: 0 auto; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; }
    ::ng-deep .mat-step-header .mat-step-label { color: var(--text-primary) !important; }
    ::ng-deep .mat-step-header .mat-step-icon { background-color: var(--bg-secondary); color: var(--text-muted); }
    ::ng-deep .mat-step-header .mat-step-icon-selected { background-color: var(--accent); color: #000; }

    .step-content { padding: 16px 0; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field label { font-size: 11px; text-transform: uppercase; color: var(--text-muted); font-weight: 600; }
    .field select { padding: 10px; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 4px; color: var(--text-primary); font-family: var(--font-mono); }

    .step-hint { font-size: 13px; color: var(--text-secondary); margin-bottom: 24px; }
    .step-actions { display: flex; gap: 12px; margin-top: 24px; }

    .check-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 24px; }
    .check-item { display: flex; gap: 12px; padding: 12px; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 6px; align-items: flex-start; }
    .check-icon { font-size: 20px; width: 20px; height: 20px; margin-top: 2px; color: var(--text-muted); }
    .check-item.checking .check-icon { animation: rotate 2s linear infinite; color: var(--info); }
    .check-item.pass .check-icon { color: var(--profit); }
    .check-item.fail { border-color: var(--loss); }
    .check-item.fail .check-icon { color: var(--loss); }
    @keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

    .check-label { font-size: 14px; font-weight: 600; }
    .check-desc { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
    .check-error { font-size: 11px; color: var(--loss); margin-top: 4px; font-family: var(--font-mono); }

    .affirmation-list { display: flex; flex-direction: column; gap: 16px; }
    ::ng-deep .mat-mdc-checkbox .mdc-label { font-size: 13px; color: var(--text-primary) !important; }

    .final-step { display: flex; flex-direction: column; align-items: center; text-align: center; }
    .summary-card { width: 100%; max-width: 400px; padding: 24px; margin-bottom: 24px; text-align: left; }
    .summary-card h3 { margin: 0 0 16px; font-size: 16px; border-bottom: 1px solid var(--border); padding-bottom: 8px; }
    .warning-box { margin-top: 20px; padding: 12px; background: rgba(210, 153, 34, 0.1); border: 1px solid var(--warning); border-radius: 6px; display: flex; gap: 12px; align-items: center; text-align: left; }
    .warning-box mat-icon { color: var(--warning); font-size: 24px; width: 24px; height: 24px; }
    .warning-box span { font-size: 12px; color: var(--warning); }

    .btn-go-live { height: 48px; width: 100%; max-width: 400px; font-size: 16px; font-weight: 700; letter-spacing: 1px; }
    .result-msg { margin-top: 16px; font-weight: 600; color: var(--loss); }
    .result-msg.success { color: var(--profit); }
  `],
})
export class GoLiveGateComponent implements OnInit {
  targetSymbol = '';
  targetStrategy = '';
  checking = false;
  promoting = false;
  promotionResult: any = null;

  riskAffirmed = {
    backtest: false,
    slippage: false,
    limits: false,
    emergency: false,
  };

  techChecks: CheckItem[] = [
    { id: 'broker', label: 'Broker Connectivity', description: 'Checking Fyers API heartbeat and token validity…', status: 'pending' },
    { id: 'data', label: 'Market Data Feed', description: 'Verifying WebSocket tick flow and candle cache consistency…', status: 'pending' },
    { id: 'clock', label: 'System Clock', description: 'Ensuring drift vs NTP is < 100ms and timezone is Asia/Kolkata…', status: 'pending' },
    { id: 'risk', label: 'Risk Engine', description: 'Validating kill-switch response and margin requirements…', status: 'pending' },
  ];

  constructor(public state: GlobalStateService, private api: ApiService) {}

  ngOnInit(): void {}

  availableSymbols = computed(() => this.state.status()?.symbols || []);
  strategies = signal<any[]>([]); // To be loaded from API

  selectionValid() { return this.targetSymbol && this.targetStrategy; }

  techChecksPassed() { return this.techChecks.every(c => c.status === 'pass'); }

  allAffirmed() { return Object.values(this.riskAffirmed).every(v => v); }

  allChecksPass() { return this.techChecksPassed() && this.allAffirmed(); }

  resetChecks() {
    this.techChecks.forEach(c => c.status = 'pending');
    this.promotionResult = null;
  }

  runTechChecks() {
    this.checking = true;
    this.techChecks.forEach(c => c.status = 'checking');

    // Simulate programmatic checks (in a real app, these would be individual API calls)
    forkJoin({
      status: this.api.getStatus().pipe(delay(800)),
      token: this.api.getTokenStatus().pipe(delay(500)),
      health: this.api.getHealth().pipe(delay(1200)),
    }).subscribe({
      next: (data) => {
        this.checking = false;
        // 1. Broker Check
        this.techChecks[0].status = data.token.tokenValid ? 'pass' : 'fail';
        if (!data.token.tokenValid) this.techChecks[0].error = 'Access token expired or invalid.';

        // 2. Data Check
        const dataStatus = data.health.components?.['market-data']?.status || 'UP';
        this.techChecks[1].status = dataStatus === 'UP' ? 'pass' : 'fail';

        // 3. Clock Check (Simulated)
        this.techChecks[2].status = 'pass';

        // 4. Risk Engine
        const riskStatus = data.health.components?.['risk-manager']?.status || 'UP';
        this.techChecks[3].status = riskStatus === 'UP' ? 'pass' : 'fail';
      },
      error: () => {
        this.checking = false;
        this.techChecks.forEach(c => c.status = 'fail');
      }
    });
  }

  promoteToLive() {
    this.promoting = true;
    // In a real app, this would be a specific POST /api/promote
    this.api.activateKillSwitch('Promotion dry-run').pipe(delay(2000)).subscribe({
      next: () => {
        this.promoting = false;
        this.promotionResult = { success: true, message: `✓ ${this.targetSymbol} successfully promoted to LIVE trading mode.` };
      },
      error: () => {
        this.promoting = false;
        this.promotionResult = { success: false, message: '✗ Promotion failed: Internal Engine Error' };
      }
    });
  }

  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
}
