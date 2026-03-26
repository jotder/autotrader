import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Subject, takeUntil, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { StrategyVersionInfo, StrategyConfig, ValidationResult, ActionResponse } from '../../core/models/api.models';
import { MetricRowComponent } from '../../shared/components/metric-row.component';

@Component({
  selector: 'at-strategies',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, MetricRowComponent],
  template: `
    <div class="strat-layout">
      <!-- Left: Strategy List -->
      <div class="strat-sidebar">
        <div class="sidebar-header">
          <h2>Strategies</h2>
        </div>
        @for (s of strategies; track s.strategyId) {
          <div class="strat-item" [class.active]="selected?.strategyId === s.strategyId" (click)="selectStrategy(s)">
            <div class="strat-item-header">
              <span class="strat-name">{{ s.strategyId }}</span>
              <span class="badge" [class]="statusClass(s)">{{ s.status }}</span>
            </div>
            <div class="strat-item-meta">
              <span>v{{ s.activeVersion }}</span>
              <span [class]="s.enabled ? 'profit' : 'text-muted'">{{ s.enabled ? 'Enabled' : 'Disabled' }}</span>
              <span>{{ s.config.symbols.length }} symbols</span>
            </div>
          </div>
        }
        @if (strategies.length === 0) {
          <div class="empty">Loading strategies…</div>
        }
      </div>

      <!-- Right: Editor Panel -->
      <div class="editor-panel">
        @if (!selected) {
          <div class="empty-editor"><mat-icon>tune</mat-icon><p>Select a strategy to configure</p></div>
        } @else {
          <!-- Header with version info -->
          <div class="editor-header">
            <div>
              <h1>{{ selected.strategyId }}</h1>
              <div class="version-info">
                @if (editingDraft) {
                  <span class="badge draft-badge">Editing DRAFT v{{ selected.latestVersion }}</span>
                } @else {
                  <span class="badge active-badge">Viewing ACTIVE v{{ selected.activeVersion }} (read-only)</span>
                }
              </div>
            </div>
            <div class="header-actions">
              @if (!editingDraft && selected.draftConfig) {
                <button class="btn-accent" (click)="switchToDraft()">Edit Draft</button>
              }
              @if (!editingDraft && !selected.draftConfig) {
                <button class="btn-accent" (click)="createDraft()">Create Draft</button>
              }
              @if (editingDraft) {
                <button class="btn-secondary" (click)="switchToActive()">View Active</button>
                <button class="btn-accent" (click)="saveDraft()" [disabled]="saving">{{ saving ? 'Saving…' : 'Save Draft' }}</button>
              }
              <button class="btn-toggle" [class.on]="selected.enabled" (click)="toggle()">
                {{ selected.enabled ? 'Disable' : 'Enable' }}
              </button>
            </div>
          </div>

          <!-- Tabs -->
          <div class="tabs">
            @for (tab of ['Config', 'Versions', 'Performance']; track tab) {
              <button class="tab" [class.active]="activeTab === tab" (click)="activeTab = tab">{{ tab }}</button>
            }
          </div>

          <!-- Config Tab -->
          @if (activeTab === 'Config') {
            <div class="config-form" [class.readonly]="!editingDraft">
              @if (validationErrors.length > 0) {
                <div class="validation-banner">
                  @for (e of validationErrors; track $index) { <div class="val-error">{{ e }}</div> }
                </div>
              }
              @if (saveMessage) {
                <div class="save-msg" [class.success]="saveSuccess">{{ saveMessage }}</div>
              }

              <!-- General -->
              <div class="form-section">
                <h3><mat-icon>settings</mat-icon> General</h3>
                <div class="form-grid">
                  <div class="field">
                    <label>Timeframe</label>
                    <select [(ngModel)]="config.timeframe" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                      @for (tf of ['M1','M5','M15','H1','D']; track tf) { <option [value]="tf">{{ tf }}</option> }
                    </select>
                  </div>
                  <div class="field">
                    <label>Cooldown (min)</label>
                    <input type="number" [(ngModel)]="config.cooldownMinutes" [disabled]="!editingDraft" min="0" max="120" (ngModelChange)="onFieldChange()" />
                  </div>
                  <div class="field">
                    <label>Max Trades/Day</label>
                    <input type="number" [(ngModel)]="config.maxTradesPerDay" [disabled]="!editingDraft" min="1" max="50" (ngModelChange)="onFieldChange()" />
                  </div>
                  <div class="field">
                    <label>Active Hours Start</label>
                    <input type="time" [(ngModel)]="config.activeHours.start" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()" />
                  </div>
                  <div class="field">
                    <label>Active Hours End</label>
                    <input type="time" [(ngModel)]="config.activeHours.end" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()" />
                  </div>
                </div>
              </div>

              <!-- Symbols -->
              <div class="form-section">
                <h3><mat-icon>list</mat-icon> Symbols</h3>
                <div class="symbol-picker">
                  @for (sym of availableSymbols; track sym) {
                    <label class="symbol-chip" [class.selected]="config.symbols.includes(sym)" [class.disabled]="!editingDraft">
                      <input type="checkbox" [checked]="config.symbols.includes(sym)" [disabled]="!editingDraft"
                             (change)="toggleSymbol(sym)" />
                      {{ shortSymbol(sym) }}
                    </label>
                  }
                </div>
                <div class="selected-count text-muted">{{ config.symbols.length }} selected</div>
              </div>

              <!-- Indicators -->
              <div class="form-section">
                <h3><mat-icon>analytics</mat-icon> Indicators</h3>
                <div class="form-grid">
                  <div class="field" [class.error]="config.indicators.emaFast >= config.indicators.emaSlow">
                    <label>EMA Fast</label>
                    <input type="number" [(ngModel)]="config.indicators.emaFast" [disabled]="!editingDraft" min="1" max="200" (ngModelChange)="onFieldChange()" />
                  </div>
                  <div class="field" [class.error]="config.indicators.emaFast >= config.indicators.emaSlow">
                    <label>EMA Slow</label>
                    <input type="number" [(ngModel)]="config.indicators.emaSlow" [disabled]="!editingDraft" min="1" max="500" (ngModelChange)="onFieldChange()" />
                  </div>
                  <div class="field"><label>RSI Period</label><input type="number" [(ngModel)]="config.indicators.rsiPeriod" [disabled]="!editingDraft" min="1" max="100" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>ATR Period</label><input type="number" [(ngModel)]="config.indicators.atrPeriod" [disabled]="!editingDraft" min="1" max="100" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Rel Vol Period</label><input type="number" [(ngModel)]="config.indicators.relVolPeriod" [disabled]="!editingDraft" min="1" max="100" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Min Candles</label><input type="number" [(ngModel)]="config.indicators.minCandles" [disabled]="!editingDraft" min="1" max="500" (ngModelChange)="onFieldChange()" /></div>
                </div>
                @if (config.indicators.emaFast >= config.indicators.emaSlow) {
                  <div class="field-error">EMA Fast must be less than EMA Slow</div>
                }
              </div>

              <!-- Entry -->
              <div class="form-section">
                <h3><mat-icon>login</mat-icon> Entry</h3>
                <div class="form-grid">
                  <div class="field">
                    <label>Min Confidence ({{ (config.entry.minConfidence * 100).toFixed(0) }}%)</label>
                    <input type="range" [(ngModel)]="config.entry.minConfidence" [disabled]="!editingDraft" min="0" max="1" step="0.01" (ngModelChange)="onFieldChange()" />
                  </div>
                  <div class="field"><label>Rel Vol Threshold</label><input type="number" [(ngModel)]="config.entry.relVolThreshold" [disabled]="!editingDraft" min="0.1" max="5" step="0.1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field">
                    <label>Trend Strength</label>
                    <select [(ngModel)]="config.entry.trendStrength" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                      @for (ts of trendStrengths; track ts) { <option [value]="ts">{{ ts }}</option> }
                    </select>
                  </div>
                </div>
              </div>

              <!-- Risk -->
              <div class="form-section">
                <h3><mat-icon>shield</mat-icon> Risk</h3>
                <div class="form-grid">
                  <div class="field"><label>Risk/Trade %</label><input type="number" [(ngModel)]="config.risk.riskPerTradePct" [disabled]="!editingDraft" min="0.1" max="10" step="0.1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>SL ATR Multiplier</label><input type="number" [(ngModel)]="config.risk.slAtrMultiplier" [disabled]="!editingDraft" min="0.5" max="5" step="0.1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>TP R-Multiple</label><input type="number" [(ngModel)]="config.risk.tpRMultiple" [disabled]="!editingDraft" min="0.5" max="10" step="0.1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Trail Activation %</label><input type="number" [(ngModel)]="config.risk.trailingActivationPct" [disabled]="!editingDraft" min="0" max="50" step="0.1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Trail Step %</label><input type="number" [(ngModel)]="config.risk.trailingStepPct" [disabled]="!editingDraft" min="0" max="10" step="0.1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Max Exposure %</label><input type="number" [(ngModel)]="config.risk.maxExposurePct" [disabled]="!editingDraft" min="0" max="100" step="1" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Max Qty</label><input type="number" [(ngModel)]="config.risk.maxQty" [disabled]="!editingDraft" min="1" max="10000" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field"><label>Max Consecutive Losses</label><input type="number" [(ngModel)]="config.risk.maxConsecutiveLosses" [disabled]="!editingDraft" min="1" max="20" (ngModelChange)="onFieldChange()" /></div>
                </div>
              </div>

              <!-- Order -->
              <div class="form-section">
                <h3><mat-icon>receipt</mat-icon> Order</h3>
                <div class="form-grid">
                  <div class="field">
                    <label>Type</label>
                    <select [(ngModel)]="config.order.type" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                      <option value="MARKET">MARKET</option><option value="LIMIT">LIMIT</option>
                    </select>
                  </div>
                  <div class="field"><label>Slippage Tolerance</label><input type="number" [(ngModel)]="config.order.slippageTolerance" [disabled]="!editingDraft" min="0" max="1" step="0.01" (ngModelChange)="onFieldChange()" /></div>
                  <div class="field">
                    <label>Product Type</label>
                    <select [(ngModel)]="config.order.productType" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                      <option value="INTRADAY">INTRADAY</option><option value="CNC">CNC</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>
          }

          <!-- Versions Tab -->
          @if (activeTab === 'Versions') {
            <div class="versions-panel">
              <div class="version-actions">
                @if (!selected.draftConfig) {
                  <button class="btn-accent" (click)="createDraft()">Create Draft from Active</button>
                }
                @if (selected.draftConfig) {
                  <button class="btn-promote" (click)="promoteDraft()">Promote Draft → Active</button>
                }
              </div>
              <table class="at-table">
                <thead><tr><th>Version</th><th>State</th><th>Created</th><th>Note</th></tr></thead>
                <tbody>
                  @for (v of selected.history; track v.version) {
                    <tr [class.active-row]="v.state === 'ACTIVE'">
                      <td class="mono">v{{ v.version }}</td>
                      <td><span class="badge" [class]="versionStateClass(v.state)">{{ v.state }}</span></td>
                      <td class="mono text-muted">{{ formatDate(v.createdAt) }}</td>
                      <td class="text-muted">{{ v.note }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          <!-- Performance Tab -->
          @if (activeTab === 'Performance') {
            <div class="perf-panel">
              <p class="text-muted">Performance metrics from trade data. Run a backtest to see results for draft changes.</p>
              <button class="btn-accent" (click)="runBacktest()">Run Backtest with Current Config</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .strat-layout { display: grid; grid-template-columns: 260px 1fr; gap: 0; height: calc(100vh - 120px); }

    .strat-sidebar { background: var(--bg-secondary); border-right: 1px solid var(--border); overflow-y: auto; }
    .sidebar-header { padding: 14px 16px; border-bottom: 1px solid var(--border); }
    .sidebar-header h2 { margin: 0; font-size: 16px; font-weight: 500; }
    .strat-item { padding: 10px 16px; border-bottom: 1px solid var(--bg-hover); cursor: pointer; }
    .strat-item:hover { background: var(--bg-hover); }
    .strat-item.active { background: var(--bg-hover); border-left: 3px solid var(--accent); }
    .strat-item-header { display: flex; justify-content: space-between; align-items: center; }
    .strat-name { font-size: 13px; font-weight: 600; }
    .strat-item-meta { display: flex; gap: 8px; font-size: 11px; color: var(--text-muted); margin-top: 4px; }

    .editor-panel { overflow-y: auto; padding: 16px 20px; }
    .empty-editor { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 300px; color: var(--text-muted); gap: 12px; }
    .empty-editor mat-icon { font-size: 48px; opacity: 0.3; }

    .editor-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
    .editor-header h1 { margin: 0; font-size: 20px; font-weight: 500; color: var(--accent); }
    .version-info { margin-top: 4px; }
    .header-actions { display: flex; gap: 6px; }

    .badge { padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; }
    .badge.active-s, .active-badge { background: rgba(63,185,80,0.15); color: var(--profit); }
    .badge.draft-s, .draft-badge { background: rgba(0,188,212,0.15); color: var(--accent); }
    .badge.disabled-s { background: var(--bg-hover); color: var(--text-muted); }
    .badge.ACTIVE { background: rgba(63,185,80,0.15); color: var(--profit); }
    .badge.DRAFT { background: rgba(0,188,212,0.15); color: var(--accent); }
    .badge.ARCHIVED { background: var(--bg-hover); color: var(--text-muted); }

    .btn-accent { background: var(--accent); color: #000; border: none; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 12px; font-weight: 600; }
    .btn-accent:hover { opacity: 0.85; }
    .btn-accent:disabled { opacity: 0.4; cursor: not-allowed; }
    .btn-secondary { background: transparent; border: 1px solid var(--border); color: var(--text-primary); padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 12px; }
    .btn-toggle { background: transparent; border: 1px solid var(--border); color: var(--text-secondary); padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 12px; }
    .btn-toggle.on { border-color: var(--profit); color: var(--profit); }
    .btn-promote { background: var(--profit); color: #000; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; font-size: 13px; font-weight: 600; }

    .tabs { display: flex; gap: 2px; margin-bottom: 16px; border-bottom: 1px solid var(--border); }
    .tab { background: transparent; border: none; color: var(--text-secondary); padding: 8px 16px; cursor: pointer; font-size: 13px; border-bottom: 2px solid transparent; }
    .tab:hover { color: var(--text-primary); }
    .tab.active { color: var(--accent); border-bottom-color: var(--accent); }

    .config-form { }
    .config-form.readonly { opacity: 0.85; }
    .config-form.readonly input, .config-form.readonly select { cursor: not-allowed; }

    .form-section { margin-bottom: 20px; padding: 14px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; }
    .form-section h3 { margin: 0 0 12px; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; color: var(--accent); }
    .form-section h3 mat-icon { font-size: 18px; }
    .form-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 10px; }
    .field { display: flex; flex-direction: column; gap: 3px; }
    .field label { font-size: 10px; text-transform: uppercase; color: var(--text-muted); letter-spacing: 0.5px; }
    .field input, .field select { padding: 6px 8px; background: var(--bg-primary); border: 1px solid var(--border); color: var(--text-primary); border-radius: 4px; font-size: 12px; font-family: var(--font-mono); }
    .field input[type="range"] { padding: 0; }
    .field.error input { border-color: var(--loss); }
    .field-error { font-size: 11px; color: var(--loss); margin-top: 4px; }

    .symbol-picker { display: flex; flex-wrap: wrap; gap: 6px; }
    .symbol-chip { display: flex; align-items: center; gap: 4px; padding: 4px 10px; background: var(--bg-hover); border-radius: 4px; font-size: 11px; font-family: var(--font-mono); cursor: pointer; color: var(--text-secondary); }
    .symbol-chip input[type="checkbox"] { display: none; }
    .symbol-chip.selected { background: var(--accent); color: #000; font-weight: 600; }
    .symbol-chip.disabled { cursor: not-allowed; opacity: 0.6; }
    .selected-count { font-size: 11px; margin-top: 6px; }

    .validation-banner { background: rgba(248,81,73,0.08); border: 1px solid var(--loss); border-radius: 6px; padding: 8px 12px; margin-bottom: 12px; }
    .val-error { font-size: 12px; color: var(--loss); padding: 2px 0; }
    .save-msg { font-size: 12px; padding: 8px 12px; border-radius: 6px; margin-bottom: 12px; }
    .save-msg.success { background: rgba(63,185,80,0.08); border: 1px solid var(--profit); color: var(--profit); }
    .save-msg:not(.success) { background: rgba(248,81,73,0.08); border: 1px solid var(--loss); color: var(--loss); }

    .versions-panel { }
    .version-actions { margin-bottom: 16px; display: flex; gap: 8px; }
    .at-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .at-table th { text-align: left; font-size: 10px; text-transform: uppercase; color: var(--text-secondary); padding: 6px 8px; border-bottom: 1px solid var(--border); }
    .at-table td { padding: 6px 8px; border-bottom: 1px solid var(--bg-hover); }
    .at-table tr.active-row { background: rgba(63,185,80,0.05); }

    .perf-panel { padding: 20px 0; }

    .empty { color: var(--text-muted); font-size: 13px; padding: 16px; text-align: center; }
  `],
})
export class StrategiesComponent implements OnInit, OnDestroy {
  strategies: StrategyVersionInfo[] = [];
  selected: StrategyVersionInfo | null = null;
  config!: StrategyConfig;
  editingDraft = false;
  activeTab = 'Config';
  availableSymbols: string[] = [];
  validationErrors: string[] = [];
  saving = false;
  saveMessage = '';
  saveSuccess = false;

  trendStrengths = ['STRONG_BULLISH', 'BULLISH', 'NEUTRAL', 'BEARISH', 'STRONG_BEARISH'];

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getStrategies().pipe(catchError(() => of([]))).subscribe(s => this.strategies = s);
    this.api.getAvailableSymbols().pipe(catchError(() => of([]))).subscribe(s => this.availableSymbols = s);
  }

  selectStrategy(s: StrategyVersionInfo): void {
    this.selected = s;
    this.editingDraft = false;
    this.config = this.deepClone(s.config);
    this.activeTab = 'Config';
    this.validationErrors = [];
    this.saveMessage = '';
  }

  switchToDraft(): void {
    if (this.selected?.draftConfig) {
      this.config = this.deepClone(this.selected.draftConfig);
      this.editingDraft = true;
      this.validationErrors = [];
      this.saveMessage = '';
    }
  }

  switchToActive(): void {
    if (this.selected) {
      this.config = this.deepClone(this.selected.config);
      this.editingDraft = false;
      this.validationErrors = [];
    }
  }

  createDraft(): void {
    if (!this.selected) return;
    this.api.createDraft(this.selected.strategyId).subscribe(r => {
      if (r.success) {
        this.selected!.draftConfig = this.deepClone(this.selected!.config);
        this.selected!.latestVersion++;
        this.selected!.status = 'HAS_DRAFT';
        this.selected!.history.push({
          version: this.selected!.latestVersion,
          state: 'DRAFT',
          createdAt: new Date().toISOString(),
          note: 'Draft created from active',
        });
        this.switchToDraft();
      }
    });
  }

  onFieldChange(): void {
    this.saveMessage = '';
    // Client-side validation
    this.validationErrors = [];
    if (this.config.indicators.emaFast >= this.config.indicators.emaSlow) {
      this.validationErrors.push('EMA Fast must be less than EMA Slow');
    }
    if (this.config.symbols.length === 0) {
      this.validationErrors.push('At least one symbol is required');
    }
    if (this.config.risk.riskPerTradePct < 0.1 || this.config.risk.riskPerTradePct > 10) {
      this.validationErrors.push('Risk per trade must be between 0.1% and 10%');
    }
    if (this.config.risk.maxQty < 1) {
      this.validationErrors.push('Max quantity must be at least 1');
    }
  }

  toggleSymbol(sym: string): void {
    const idx = this.config.symbols.indexOf(sym);
    if (idx >= 0) { this.config.symbols.splice(idx, 1); }
    else { this.config.symbols.push(sym); }
    this.onFieldChange();
  }

  saveDraft(): void {
    if (!this.selected || this.validationErrors.length > 0) return;
    this.saving = true;
    this.saveMessage = '';
    this.api.updateDraft(this.selected.strategyId, this.config).subscribe({
      next: (r: ActionResponse) => {
        this.saving = false;
        this.saveMessage = r.message;
        this.saveSuccess = r.success;
        if (r.success) {
          this.selected!.draftConfig = this.deepClone(this.config);
        }
      },
      error: () => {
        this.saving = false;
        this.saveMessage = 'Network error — check connection';
        this.saveSuccess = false;
      },
    });
  }

  promoteDraft(): void {
    if (!this.selected) return;
    if (!confirm(`Promote draft v${this.selected.latestVersion} to active? Current active v${this.selected.activeVersion} will be archived.`)) return;
    this.api.promoteDraft(this.selected.strategyId).subscribe(r => {
      if (r.success) {
        this.saveMessage = r.message;
        this.saveSuccess = true;
        // Update local state
        this.selected!.activeVersion = this.selected!.latestVersion;
        this.selected!.config = this.deepClone(this.selected!.draftConfig!);
        this.selected!.draftConfig = undefined;
        this.selected!.status = 'ACTIVE';
        this.switchToActive();
      }
    });
  }

  toggle(): void {
    if (!this.selected) return;
    this.api.toggleStrategy(this.selected.strategyId).subscribe(r => {
      if (r.success) this.selected!.enabled = !this.selected!.enabled;
    });
  }

  runBacktest(): void {
    // Navigate to backtest page (simplified — just alert for now)
    alert(`Run backtest for ${this.selected?.strategyId} with symbols: ${this.config.symbols.join(', ')}`);
  }

  statusClass(s: StrategyVersionInfo): string {
    return s.status === 'ACTIVE' ? 'active-s' : s.status === 'HAS_DRAFT' ? 'draft-s' : 'disabled-s';
  }

  versionStateClass(state: string): string { return state; }

  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }

  formatDate(iso: string): string {
    try { return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }); }
    catch { return iso; }
  }

  private deepClone<T>(obj: T): T { return JSON.parse(JSON.stringify(obj)); }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
