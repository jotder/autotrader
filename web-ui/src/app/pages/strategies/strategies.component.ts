import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, takeUntil, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import {
  StrategyVersionInfo, StrategyConfig, VersionEntry,
  ActionResponse
} from '../../core/models/api.models';

@Component({
  selector: 'at-strategies',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, MatTabsModule, MatButtonModule,
    MatListModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatTableModule, MatSortModule, MatTooltipModule
  ],
  template: `
    <div class="strat-layout">
      <!-- Left: Strategy List -->
      <div class="strat-sidebar">
        <div class="sidebar-header">
          <h2>Strategies</h2>
          <button mat-icon-button matTooltip="Refresh" (click)="loadStrategies()">
            <mat-icon>refresh</mat-icon>
          </button>
        </div>
        <mat-nav-list class="strat-list">
          @for (s of strategies; track s.strategyId) {
            <a mat-list-item (click)="selectStrategy(s)" [class.active]="selected?.strategyId === s.strategyId">
              <div matListItemTitle class="strat-name">{{ s.strategyId }}</div>
              <div matListItemLine class="strat-meta">
                <span class="badge" [class]="statusClass(s)">{{ s.status }}</span>
                <span>v{{ s.activeVersion }}</span>
                <span>{{ s.config.symbols.length }} symbols</span>
              </div>
            </a>
          }
        </mat-nav-list>
        @if (strategies.length === 0) {
          <div class="empty-state">No strategies found</div>
        }
      </div>

      <!-- Right: Editor Panel -->
      <div class="editor-panel">
        @if (!selected) {
          <div class="empty-editor">
            <mat-icon>tune</mat-icon>
            <p>Select a strategy from the sidebar to configure parameters and view version history.</p>
          </div>
        } @else {
          <div class="editor-content">
            <!-- Header -->
            <div class="editor-header">
              <div class="title-section">
                <h1>{{ selected.strategyId }}</h1>
                <div class="status-indicators">
                  @if (editingDraft) {
                    <span class="badge draft-badge">DRAFT v{{ selected.latestVersion }} (Editable)</span>
                  } @else {
                    <span class="badge active-badge">ACTIVE v{{ selected.activeVersion }} (Read-only)</span>
                  }
                  <span class="badge" [class]="selected.enabled ? 'enabled-badge' : 'disabled-badge'">
                    {{ selected.enabled ? 'Running' : 'Stopped' }}
                  </span>
                </div>
              </div>
              <div class="action-group">
                @if (!editingDraft) {
                  @if (selected.draftConfig) {
                    <button mat-flat-button color="accent" (click)="switchToDraft()">Edit Draft</button>
                  } @else {
                    <button mat-stroked-button color="accent" (click)="createDraft()">New Draft</button>
                  }
                } @else {
                  <button mat-button (click)="switchToActive()">Cancel Edits</button>
                  <button mat-flat-button color="accent" (click)="saveDraft()" [disabled]="saving || validationErrors.length > 0">
                    {{ saving ? 'Saving…' : 'Save Draft' }}
                  </button>
                }
                <button mat-stroked-button [color]="selected.enabled ? 'warn' : 'primary'" (click)="toggle()">
                  {{ selected.enabled ? 'Disable' : 'Enable' }}
                </button>
              </div>
            </div>

            <!-- Tabs -->
            <mat-tab-group animationDuration="0ms" class="editor-tabs">
              <mat-tab label="Configuration">
                <div class="tab-body config-tab">
                  @if (validationErrors.length > 0) {
                    <div class="error-banner">
                      <mat-icon>error_outline</mat-icon>
                      <div class="error-list">
                        @for (e of validationErrors; track $index) { <div>{{ e }}</div> }
                      </div>
                    </div>
                  }
                  @if (saveMessage) {
                    <div class="status-msg" [class.success]="saveSuccess">
                      <mat-icon>{{ saveSuccess ? 'check_circle' : 'error' }}</mat-icon>
                      {{ saveMessage }}
                    </div>
                  }

                  <div class="form-container" [class.readonly]="!editingDraft">
                    <!-- General Settings -->
                    <div class="form-section">
                      <h3>General Parameters</h3>
                      <div class="form-grid">
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>Timeframe</mat-label>
                          <mat-select [(ngModel)]="config.timeframe" [disabled]="!editingDraft" (selectionChange)="onFieldChange()">
                            @for (tf of ['M1','M5','M15','H1','D']; track tf) { <option [value]="tf">{{ tf }}</option> }
                          </mat-select>
                        </mat-form-field>

                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>Cooldown (min)</mat-label>
                          <input matInput type="number" [(ngModel)]="config.cooldownMinutes" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>

                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>Max Trades/Day</mat-label>
                          <input matInput type="number" [(ngModel)]="config.maxTradesPerDay" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>

                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>Start Time</mat-label>
                          <input matInput type="time" [(ngModel)]="config.activeHours.start" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>

                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>End Time</mat-label>
                          <input matInput type="time" [(ngModel)]="config.activeHours.end" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                      </div>
                    </div>

                    <!-- Symbols -->
                    <div class="form-section">
                      <h3>Active Symbols</h3>
                      <div class="symbol-grid">
                        @for (sym of availableSymbols; track sym) {
                          <div class="symbol-item" [class.selected]="config.symbols.includes(sym)"
                               (click)="editingDraft ? toggleSymbol(sym) : null">
                            <span class="mono">{{ shortSymbol(sym) }}</span>
                            @if (config.symbols.includes(sym)) { <mat-icon>check</mat-icon> }
                          </div>
                        }
                      </div>
                      <div class="selected-summary">{{ config.symbols.length }} symbols configured</div>
                    </div>

                    <!-- Indicators -->
                    <div class="form-section">
                      <h3>Indicator Logic</h3>
                      <div class="form-grid">
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>EMA Fast</mat-label>
                          <input matInput type="number" [(ngModel)]="config.indicators.emaFast" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>EMA Slow</mat-label>
                          <input matInput type="number" [(ngModel)]="config.indicators.emaSlow" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>RSI Period</mat-label>
                          <input matInput type="number" [(ngModel)]="config.indicators.rsiPeriod" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>ATR Period</mat-label>
                          <input matInput type="number" [(ngModel)]="config.indicators.atrPeriod" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                      </div>
                    </div>

                    <!-- Risk & Management -->
                    <div class="form-section">
                      <h3>Risk & Exit Strategy</h3>
                      <div class="form-grid">
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>Risk/Trade %</mat-label>
                          <input matInput type="number" step="0.1" [(ngModel)]="config.risk.riskPerTradePct" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>SL ATR Multi</mat-label>
                          <input matInput type="number" step="0.1" [(ngModel)]="config.risk.slAtrMultiplier" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>TP R-Multi</mat-label>
                          <input matInput type="number" step="0.1" [(ngModel)]="config.risk.tpRMultiple" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                        <mat-form-field appearance="outline" subscriptSizing="dynamic">
                          <mat-label>Trail Activation %</mat-label>
                          <input matInput type="number" step="0.1" [(ngModel)]="config.risk.trailingActivationPct" [disabled]="!editingDraft" (ngModelChange)="onFieldChange()">
                        </mat-form-field>
                      </div>
                    </div>
                  </div>
                </div>
              </mat-tab>

              <mat-tab label="Version History">
                <div class="tab-body">
                  @if (selected.draftConfig) {
                    <div class="version-actions">
                      <button mat-flat-button color="primary" (click)="promoteDraft()">Promote Draft to Active</button>
                    </div>
                  }
                  <table mat-table [dataSource]="versionDataSource" matSort class="version-table">
                    <ng-container matColumnDef="version">
                      <th mat-header-cell *matHeaderCellDef mat-sort-header> Version </th>
                      <td mat-cell *matCellDef="let v" class="mono"> v{{ v.version }} </td>
                    </ng-container>
                    <ng-container matColumnDef="state">
                      <th mat-header-cell *matHeaderCellDef> State </th>
                      <td mat-cell *matCellDef="let v">
                        <span class="badge" [class]="v.state">{{ v.state }}</span>
                      </td>
                    </ng-container>
                    <ng-container matColumnDef="createdAt">
                      <th mat-header-cell *matHeaderCellDef mat-sort-header> Created </th>
                      <td mat-cell *matCellDef="let v" class="text-muted"> {{ formatDate(v.createdAt) }} </td>
                    </ng-container>
                    <ng-container matColumnDef="note">
                      <th mat-header-cell *matHeaderCellDef> Note </th>
                      <td mat-cell *matCellDef="let v" class="text-muted"> {{ v.note }} </td>
                    </ng-container>

                    <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
                    <tr mat-row *matRowDef="let row; columns: displayedColumns;" [class.active-version]="row.state === 'ACTIVE'"></tr>
                  </table>
                </div>
              </mat-tab>

              <mat-tab label="Backtest Results">
                <div class="tab-body backtest-tab">
                  <div class="empty-state">
                    <mat-icon>science</mat-icon>
                    <p>No backtest data for this version yet.</p>
                    <button mat-stroked-button color="accent" (click)="runBacktest()">Run Backtest Now</button>
                  </div>
                </div>
              </mat-tab>
            </mat-tab-group>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .strat-layout { display: flex; height: calc(100vh - 120px); overflow: hidden; }

    /* Sidebar */
    .strat-sidebar { width: 280px; border-right: 1px solid var(--border); background: var(--bg-secondary); display: flex; flex-direction: column; }
    .sidebar-header { padding: 12px 16px; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; }
    .sidebar-header h2 { margin: 0; font-size: 14px; font-weight: 600; text-transform: uppercase; color: var(--text-secondary); }
    .strat-list { flex: 1; overflow-y: auto; padding: 0; }
    .strat-list a { border-bottom: 1px solid var(--border); }
    .strat-list a.active { background: var(--bg-hover) !important; border-left: 4px solid var(--accent); }
    .strat-name { font-weight: 600; font-size: 14px; color: var(--accent); }
    .strat-meta { display: flex; gap: 8px; align-items: center; margin-top: 4px; }
    .empty-state { padding: 40px 20px; text-align: center; color: var(--text-muted); font-size: 13px; }

    /* Editor */
    .editor-panel { flex: 1; overflow-y: auto; background: var(--bg-primary); }
    .editor-content { padding: 24px; }
    .empty-editor { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); opacity: 0.6; padding: 40px; text-align: center; }
    .empty-editor mat-icon { font-size: 64px; width: 64px; height: 64px; margin-bottom: 16px; }

    .editor-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    .title-section h1 { margin: 0; font-size: 24px; font-weight: 500; }
    .status-indicators { display: flex; gap: 8px; margin-top: 8px; }
    .action-group { display: flex; gap: 12px; }

    /* Badges */
    .badge { padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; text-transform: uppercase; }
    .active-s, .active-badge, .ACTIVE { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .draft-s, .draft-badge, .DRAFT { background: rgba(0, 188, 212, 0.15); color: var(--accent); }
    .enabled-badge { background: rgba(63, 185, 80, 0.1); color: var(--profit); border: 1px solid var(--profit); }
    .disabled-badge { background: rgba(248, 81, 73, 0.1); color: var(--loss); border: 1px solid var(--loss); }
    .ARCHIVED { opacity: 0.5; background: var(--bg-hover); color: var(--text-muted); }

    /* Form */
    .form-container.readonly { opacity: 0.8; pointer-events: none; }
    .form-section { margin-bottom: 32px; }
    .form-section h3 { font-size: 12px; text-transform: uppercase; color: var(--text-muted); border-bottom: 1px solid var(--border); padding-bottom: 8px; margin-bottom: 16px; }
    .form-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 20px; }

    .symbol-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(100px, 1fr)); gap: 8px; }
    .symbol-item { padding: 8px; background: var(--bg-card); border: 1px solid var(--border); border-radius: 4px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; }
    .symbol-item.selected { border-color: var(--accent); background: rgba(0, 188, 212, 0.05); color: var(--accent); }
    .symbol-item mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .selected-summary { margin-top: 8px; font-size: 11px; color: var(--text-muted); }

    /* Tables */
    .version-table { width: 100%; background: transparent; }
    .active-version { background: rgba(63, 185, 80, 0.03); }
    .version-actions { margin-bottom: 16px; }

    /* Messaging */
    .error-banner { background: rgba(248, 81, 73, 0.1); border: 1px solid var(--loss); padding: 12px; border-radius: 6px; display: flex; gap: 12px; margin-bottom: 24px; }
    .error-banner mat-icon { color: var(--loss); }
    .error-list { font-size: 13px; color: var(--loss); }
    .status-msg { padding: 12px; border-radius: 6px; margin-bottom: 24px; display: flex; align-items: center; gap: 12px; font-size: 13px; }
    .status-msg.success { background: rgba(63, 185, 80, 0.1); color: var(--profit); border: 1px solid var(--profit); }
  `],
})
export class StrategiesComponent implements OnInit, OnDestroy {
  strategies: StrategyVersionInfo[] = [];
  selected: StrategyVersionInfo | null = null;
  config!: StrategyConfig;
  editingDraft = false;
  availableSymbols: string[] = [];
  validationErrors: string[] = [];
  saving = false;
  saveMessage = '';
  saveSuccess = false;

  displayedColumns = ['version', 'state', 'createdAt', 'note'];
  versionDataSource = new MatTableDataSource<VersionEntry>([]);

  @ViewChild(MatSort) sort!: MatSort;

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadStrategies();
    this.api.getAvailableSymbols().pipe(catchError(() => of([]))).subscribe(s => this.availableSymbols = s);
  }

  loadStrategies(): void {
    this.api.getStrategies().pipe(catchError(() => of([]))).subscribe(s => {
      this.strategies = s;
      if (this.selected) {
        const updated = s.find(x => x.strategyId === this.selected?.strategyId);
        if (updated) this.selectStrategy(updated);
      }
    });
  }

  selectStrategy(s: StrategyVersionInfo): void {
    this.selected = s;
    this.editingDraft = false;
    this.config = this.deepClone(s.config);
    this.versionDataSource.data = [...s.history].reverse();
    if (this.sort) this.versionDataSource.sort = this.sort;
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
        this.loadStrategies();
        setTimeout(() => this.switchToDraft(), 100);
      }
    });
  }

  onFieldChange(): void {
    this.saveMessage = '';
    this.validationErrors = [];
    if (this.config.indicators.emaFast >= this.config.indicators.emaSlow) {
      this.validationErrors.push('EMA Fast must be less than EMA Slow');
    }
    if (this.config.symbols.length === 0) {
      this.validationErrors.push('At least one symbol is required');
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
    this.api.updateDraft(this.selected.strategyId, this.config).subscribe({
      next: (r: ActionResponse) => {
        this.saving = false;
        this.saveMessage = r.message;
        this.saveSuccess = r.success;
        if (r.success) this.loadStrategies();
      },
      error: () => {
        this.saving = false;
        this.saveMessage = 'Network error';
        this.saveSuccess = false;
      }
    });
  }

  promoteDraft(): void {
    if (!this.selected) return;
    this.api.promoteDraft(this.selected.strategyId).subscribe(r => {
      if (r.success) {
        this.saveMessage = r.message;
        this.saveSuccess = true;
        this.loadStrategies();
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
    alert('Backtest simulation started…');
  }

  statusClass(s: StrategyVersionInfo): string {
    return s.status === 'ACTIVE' ? 'active-s' : s.status === 'HAS_DRAFT' ? 'draft-s' : 'disabled-s';
  }

  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }

  formatDate(iso: string): string {
    try { return new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }); }
    catch { return iso; }
  }

  private deepClone<T>(obj: T): T { return JSON.parse(JSON.stringify(obj)); }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
