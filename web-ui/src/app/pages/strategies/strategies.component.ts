import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import {
  StrategyVersionInfo, StrategyConfig, VersionEntry,
  ActionResponse
} from '../../core/models/api.models';
import { MatIconModule } from '@angular/material/icon';

// DevExtreme
import { DxListModule } from 'devextreme-angular/ui/list';
import { DxTabsModule } from 'devextreme-angular/ui/tabs';
import { DxButtonModule } from 'devextreme-angular/ui/button';
import { DxFormModule } from 'devextreme-angular/ui/form';
import { DxDataGridModule } from 'devextreme-angular/ui/data-grid';
import { DxTagBoxModule } from 'devextreme-angular/ui/tag-box';
import { DxScrollViewModule } from 'devextreme-angular/ui/scroll-view';
import { DxSelectBoxModule } from 'devextreme-angular/ui/select-box';
import { DxDateBoxModule } from 'devextreme-angular/ui/date-box';

@Component({
  selector: 'at-strategies',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule,
    DxListModule, DxTabsModule, DxButtonModule, DxFormModule, 
    DxDataGridModule, DxTagBoxModule, DxScrollViewModule,
    DxSelectBoxModule, DxDateBoxModule
  ],
  template: `
    <div class="strat-layout">
      <!-- Left: Strategy List -->
      <div class="strat-sidebar">
        <div class="sidebar-header">
          <h2>Strategies</h2>
          <dx-button icon="refresh" stylingMode="text" (onClick)="loadStrategies()"></dx-button>
        </div>
        <dx-list
          [items]="strategies"
          [activeStateEnabled]="true"
          [hoverStateEnabled]="true"
          [focusStateEnabled]="true"
          selectionMode="single"
          keyExpr="strategyId"
          (onSelectionChanged)="onStrategySelected($event)">
          <div *dxTemplate="let s of 'item'">
            <div class="strat-item-content">
              <div class="strat-name">{{ s.strategyId }}</div>
              <div class="strat-meta">
                <span class="badge" [class]="statusClass(s)">{{ s.status }}</span>
                <span class="text-muted">v{{ s.activeVersion }}</span>
              </div>
            </div>
          </div>
        </dx-list>
        @if (strategies.length === 0) {
          <div class="empty-state">No strategies found</div>
        }
      </div>

      <!-- Right: Editor Panel -->
      <div class="editor-panel">
        @if (!selected) {
          <div class="empty-editor">
            <mat-icon>tune</mat-icon>
            <p>Select a strategy from the sidebar to configure parameters.</p>
          </div>
        } @else {
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
                  <dx-button text="Edit Draft" type="default" stylingMode="contained" (onClick)="switchToDraft()"></dx-button>
                } @else {
                  <dx-button text="New Draft" type="default" stylingMode="outlined" (onClick)="createDraft()"></dx-button>
                }
              } @else {
                <dx-button text="Cancel" stylingMode="text" (onClick)="switchToActive()"></dx-button>
                <dx-button text="Save Draft" type="default" stylingMode="contained" 
                           [disabled]="saving || validationErrors.length > 0" (onClick)="saveDraft()">
                </dx-button>
              }
              <dx-button [text]="selected.enabled ? 'Disable' : 'Enable'" 
                         [type]="selected.enabled ? 'danger' : 'success'" 
                         stylingMode="outlined" (onClick)="toggle()">
              </dx-button>
            </div>
          </div>

          <dx-tabs
            [items]="tabItems"
            [(selectedIndex)]="selectedTabIndex"
            [scrollByContent]="true"
            [showNavButtons]="true">
          </dx-tabs>

          <dx-scroll-view class="tab-content-scroll">
            <div class="tab-body">
              
              <!-- Tab 0: Configuration -->
              @if (selectedTabIndex === 0) {
                <div class="config-pane">
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

                  <dx-form [formData]="config" [readOnly]="!editingDraft" labelLocation="top" [colCount]="2">
                    <dxi-item itemType="group" caption="General Parameters" [colSpan]="2" [colCount]="3">
                      <dxi-item dataField="timeframe">
                        <dxo-label text="Timeframe"></dxo-label>
                        <dx-select-box [items]="['M1','M5','M15','H1','D']" [(value)]="config.timeframe" (onValueChanged)="onFieldChange()"></dx-select-box>
                      </dxi-item>
                      <dxi-item dataField="cooldownMinutes" editorType="dxNumberBox">
                        <dxo-label text="Cooldown (min)"></dxo-label>
                      </dxi-item>
                      <dxi-item dataField="maxTradesPerDay" editorType="dxNumberBox">
                        <dxo-label text="Max Trades/Day"></dxo-label>
                      </dxi-item>
                      <dxi-item dataField="activeHours.start" editorType="dxDateBox" [editorOptions]="{ type: 'time' }">
                        <dxo-label text="Start Time"></dxo-label>
                      </dxi-item>
                      <dxi-item dataField="activeHours.end" editorType="dxDateBox" [editorOptions]="{ type: 'time' }">
                        <dxo-label text="End Time"></dxo-label>
                      </dxi-item>
                    </dxi-item>

                    <dxi-item itemType="group" caption="Active Symbols" [colSpan]="2">
                      <dx-tag-box
                        [items]="availableSymbols"
                        [(value)]="config.symbols"
                        [searchEnabled]="true"
                        placeholder="Add symbols..."
                        (onValueChanged)="onFieldChange()">
                      </dx-tag-box>
                    </dxi-item>

                    <dxi-item itemType="group" caption="Indicator Logic" [colCount]="2" [colSpan]="2">
                      <dxi-item dataField="indicators.emaFast" editorType="dxNumberBox"><dxo-label text="EMA Fast"></dxo-label></dxi-item>
                      <dxi-item dataField="indicators.emaSlow" editorType="dxNumberBox"><dxo-label text="EMA Slow"></dxo-label></dxi-item>
                      <dxi-item dataField="indicators.rsiPeriod" editorType="dxNumberBox"><dxo-label text="RSI Period"></dxo-label></dxi-item>
                      <dxi-item dataField="indicators.atrPeriod" editorType="dxNumberBox"><dxo-label text="ATR Period"></dxo-label></dxi-item>
                    </dxi-item>

                    <dxi-item itemType="group" caption="Risk & Exit Strategy" [colCount]="2" [colSpan]="2">
                      <dxi-item dataField="risk.riskPerTradePct" editorType="dxNumberBox" [editorOptions]="{ step: 0.1 }">
                        <dxo-label text="Risk/Trade %"></dxo-label>
                      </dxi-item>
                      <dxi-item dataField="risk.slAtrMultiplier" editorType="dxNumberBox" [editorOptions]="{ step: 0.1 }">
                        <dxo-label text="SL ATR Multi"></dxo-label>
                      </dxi-item>
                      <dxi-item dataField="risk.tpRMultiple" editorType="dxNumberBox" [editorOptions]="{ step: 0.1 }">
                        <dxo-label text="TP R-Multi"></dxo-label>
                      </dxi-item>
                      <dxi-item dataField="risk.trailingActivationPct" editorType="dxNumberBox" [editorOptions]="{ step: 0.1 }">
                        <dxo-label text="Trail Activation %"></dxo-label>
                      </dxi-item>
                    </dxi-item>
                  </dx-form>
                </div>
              }

              <!-- Tab 1: Version History -->
              @if (selectedTabIndex === 1) {
                <div class="history-pane">
                  @if (selected.draftConfig) {
                    <div class="version-actions">
                      <dx-button text="Promote Draft to Active" type="success" stylingMode="contained" (onClick)="promoteDraft()"></dx-button>
                    </div>
                  }
                  <dx-data-grid
                    [dataSource]="selected.history"
                    [showBorders]="true"
                    [rowAlternationEnabled]="true">
                    <dxi-column dataField="version" caption="Ver" [width]="60" sortOrder="desc"></dxi-column>
                    <dxi-column dataField="state" cellTemplate="stateTemplate" [width]="100"></dxi-column>
                    <dxi-column dataField="createdAt" dataType="datetime" format="dd MMM, HH:mm" caption="Date"></dxi-column>
                    <dxi-column dataField="note" caption="Notes"></dxi-column>

                    <div *dxTemplate="let d of 'stateTemplate'">
                      <span class="badge" [class]="d.value">{{ d.value }}</span>
                    </div>
                  </dx-data-grid>
                </div>
              }

              <!-- Tab 2: Backtest Results -->
              @if (selectedTabIndex === 2) {
                <div class="backtest-pane">
                  <div class="empty-state">
                    <mat-icon>science</mat-icon>
                    <p>No backtest data for this version yet.</p>
                    <dx-button text="Run Backtest Now" type="default" stylingMode="outlined" (onClick)="runBacktest()"></dx-button>
                  </div>
                </div>
              }

            </div>
          </dx-scroll-view>
        }
      </div>
    </div>
  `,
  styles: [`
    .strat-layout { display: flex; height: 100%; overflow: hidden; background: var(--bg-primary); }

    .strat-sidebar { width: 260px; border-right: 1px solid var(--border); background: var(--bg-secondary); display: flex; flex-direction: column; flex-shrink: 0; }
    .sidebar-header { padding: 16px; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; }
    .sidebar-header h2 { margin: 0; font-size: 14px; font-weight: 600; text-transform: uppercase; color: var(--text-secondary); }
    
    .strat-item-content { padding: 4px 0; }
    .strat-name { font-weight: 600; color: var(--accent); }
    .strat-meta { display: flex; gap: 8px; margin-top: 4px; font-size: 11px; }

    .editor-panel { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
    .editor-header { padding: 24px 24px 16px; display: flex; justify-content: space-between; align-items: flex-start; }
    .title-section h1 { margin: 0; font-size: 24px; font-weight: 500; }
    .status-indicators { display: flex; gap: 8px; margin-top: 8px; }
    .action-group { display: flex; gap: 12px; }

    .tab-content-scroll { flex: 1; }
    .tab-body { padding: 24px; max-width: 1000px; }

    .badge { padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: 600; text-transform: uppercase; }
    .active-s, .ACTIVE { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .draft-s, .HAS_DRAFT, .DRAFT { background: rgba(0, 188, 212, 0.15); color: var(--accent); }
    .enabled-badge { background: rgba(63, 185, 80, 0.1); color: var(--profit); border: 1px solid var(--profit); }
    .disabled-badge { background: rgba(248, 81, 73, 0.1); color: var(--loss); border: 1px solid var(--loss); }
    
    .error-banner { background: rgba(248, 81, 73, 0.1); border: 1px solid var(--loss); padding: 12px; border-radius: 6px; display: flex; gap: 12px; margin-bottom: 24px; color: var(--loss); font-size: 13px; }
    .status-msg { padding: 12px; border-radius: 6px; margin-bottom: 24px; display: flex; align-items: center; gap: 12px; font-size: 13px; background: var(--bg-hover); }
    .status-msg.success { background: rgba(63, 185, 80, 0.1); color: var(--profit); border: 1px solid var(--profit); }

    .empty-editor, .empty-state { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); padding: 40px; text-align: center; }
    .empty-editor mat-icon, .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; margin-bottom: 16px; opacity: 0.5; }

    ::ng-deep .dx-form-group-caption { font-size: 11px !important; text-transform: uppercase; color: var(--text-muted) !important; font-weight: 600; }
    ::ng-deep .dx-tab-selected { color: var(--accent) !important; }
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

  tabItems = [
    { text: 'Configuration', icon: 'preferences' },
    { text: 'Version History', icon: 'history' },
    { text: 'Backtest Results', icon: 'chart' }
  ];
  selectedTabIndex = 0;

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
        if (updated) this.selected = updated;
      }
    });
  }

  onStrategySelected(e: any): void {
    const s = e.addedItems[0];
    if (!s) return;
    this.selected = s;
    this.editingDraft = false;
    this.config = this.deepClone(s.config);
    this.validationErrors = [];
    this.saveMessage = '';
    this.selectedTabIndex = 0;
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
        setTimeout(() => this.switchToDraft(), 150);
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
      if (r.success) {
        this.selected!.enabled = !this.selected!.enabled;
      }
    });
  }

  runBacktest(): void {
    alert('Backtest simulation started…');
  }

  statusClass(s: StrategyVersionInfo): string {
    return s.status === 'ACTIVE' ? 'active-s' : s.status === 'HAS_DRAFT' ? 'draft-s' : 'disabled-s';
  }

  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }

  private deepClone<T>(obj: T): T { return JSON.parse(JSON.stringify(obj)); }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
