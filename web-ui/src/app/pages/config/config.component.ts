import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/services/api.service';
import { ConnectionService, ConnectionMode } from '../../core/services/connection.service';
import { catchError, of } from 'rxjs';
import { StatusCardComponent } from '../../shared/components/status-card.component';

// DevExtreme
import { DxTabsModule } from 'devextreme-angular/ui/tabs';
import { DxButtonModule } from 'devextreme-angular/ui/button';
import { DxFormModule } from 'devextreme-angular/ui/form';
import { DxDataGridModule } from 'devextreme-angular/ui/data-grid';
import { DxRadioGroupModule } from 'devextreme-angular/ui/radio-group';
import { DxTextBoxModule } from 'devextreme-angular/ui/text-box';
import { DxSelectBoxModule } from 'devextreme-angular/ui/select-box';
import { DxScrollViewModule } from 'devextreme-angular/ui/scroll-view';

@Component({
  selector: 'at-config',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, StatusCardComponent,
    DxTabsModule, DxButtonModule, DxFormModule, DxDataGridModule, 
    DxRadioGroupModule, DxTextBoxModule, DxSelectBoxModule, DxScrollViewModule
  ],
  template: `
    <div class="page-header">
      <h1>System Configuration</h1>
      <div class="header-actions">
        <dx-button icon="refresh" text="Reload" (onClick)="loadAll()"></dx-button>
      </div>
    </div>

    <dx-tabs
      [items]="tabItems"
      [(selectedIndex)]="selectedTabIndex"
      [scrollByContent]="true">
    </dx-tabs>

    <dx-scroll-view class="config-scroll-view">
      <div class="tab-content">
        
        <!-- Tab 0: UI & Backend -->
        @if (selectedTabIndex === 0) {
          <div class="config-grid">
            <at-status-card title="Connection Settings" icon="settings_ethernet">
              <div class="dx-fieldset">
                <div class="dx-field">
                  <div class="dx-field-label">Connection Mode</div>
                  <div class="dx-field-value">
                    <dx-radio-group
                      [items]="modeOptions"
                      displayExpr="text"
                      valueExpr="value"
                      [(value)]="localMode">
                    </dx-radio-group>
                  </div>
                </div>
                <div class="dx-field">
                  <div class="dx-field-label">Backend API URL</div>
                  <div class="dx-field-value">
                    <dx-text-box [(value)]="localUrl" placeholder="/api"></dx-text-box>
                  </div>
                </div>
              </div>
              <dx-button
                text="Apply Connection Settings"
                type="default"
                stylingMode="contained"
                (onClick)="updateConnection()">
              </dx-button>
            </at-status-card>

            <at-status-card title="Operational State" icon="info">
              <div class="config-list">
                <div class="config-row">
                  <span class="label">Current Mode</span>
                  <span class="value">{{ connection.mode() | uppercase }}</span>
                </div>
                <div class="config-row">
                  <span class="label">Base URL</span>
                  <span class="value mono">{{ connection.backendUrl() }}</span>
                </div>
                <div class="config-row">
                  <span class="label">Source</span>
                  <span class="value">{{ connection.isMock() ? 'Mock Generator' : 'Real HTTP' }}</span>
                </div>
              </div>
            </at-status-card>
          </div>
          <div class="yaml-hint warning">
            <mat-icon>warning</mat-icon>
            <span>Switching modes resets polling. "Live Backend" requires the Spring Boot engine to be running.</span>
          </div>
        }

        <!-- Tab 1: Risk & Limits -->
        @if (selectedTabIndex === 1) {
          <div class="config-grid">
            <at-status-card title="Capital & Thresholds" icon="account_balance">
              <div class="config-list">
                @for (field of riskCapitalFields; track field.key) {
                  <div class="config-row">
                    <span class="label">{{ field.label }}</span>
                    <span class="value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
                  </div>
                }
              </div>
            </at-status-card>

            <at-status-card title="Position Sizing" icon="pie_chart">
              <div class="config-list">
                @for (field of riskSizingFields; track field.key) {
                  <div class="config-row">
                    <span class="label">{{ field.label }}</span>
                    <span class="value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
                  </div>
                }
              </div>
            </at-status-card>

            <at-status-card title="Trading Schedule" icon="timer">
              <div class="config-list">
                @for (field of riskTimingFields; track field.key) {
                  <div class="config-row">
                    <span class="label">{{ field.label }}</span>
                    <span class="value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
                  </div>
                }
              </div>
            </at-status-card>
          </div>
          <div class="yaml-hint">
            <mat-icon>info</mat-icon>
            <span>Limits are defined in config/defaults.yaml. Restart engine to apply changes.</span>
          </div>
        }

        <!-- Tab 2: Symbol Universe -->
        @if (selectedTabIndex === 2) {
          <div class="symbols-layout">
            <at-status-card title="Active Selection" icon="list" class="side-panel">
              <div class="symbol-grid">
                @for (s of activeSymbols; track s) {
                  <div class="symbol-badge mono">{{ s }}</div>
                }
              </div>
            </at-status-card>

            <at-status-card title="Symbol Master Explorer" icon="search" class="main-panel">
              <div class="dx-field search-field">
                <dx-text-box
                  mode="search"
                  placeholder="Search symbols (e.g. SBIN)..."
                  [(value)]="symbolSearch"
                  (onValueChanged)="searchSymbols()">
                </dx-text-box>
              </div>

              <dx-data-grid
                [dataSource]="symbolResults"
                [showBorders]="true"
                [rowAlternationEnabled]="true"
                [height]="400">
                <dxi-column dataField="symbolTicker" caption="Ticker" [width]="150" cellTemplate="tickerTemplate"></dxi-column>
                <dxi-column dataField="exchange" caption="Ex" [width]="60"></dxi-column>
                <dxi-column dataField="segment" caption="Seg" [width]="60"></dxi-column>
                <dxi-column dataField="symbolDetails" caption="Name"></dxi-column>
                <dxi-column dataField="minLotSize" caption="Lot" [width]="60" alignment="right"></dxi-column>

                <div *dxTemplate="let d of 'tickerTemplate'">
                  <span class="mono font-bold">{{ d.value }}</span>
                </div>
              </dx-data-grid>
            </at-status-card>
          </div>
        }

        <!-- Tab 3: Dimension Tables -->
        @if (selectedTabIndex === 3) {
          <div class="dimensions-layout">
            <div class="dx-field select-dim">
              <div class="dx-field-label">Select Table</div>
              <div class="dx-field-value">
                <dx-select-box
                  [items]="dimTables"
                  [(value)]="selectedDim"
                  (onValueChanged)="loadDimension()">
                </dx-select-box>
              </div>
            </div>

            <dx-data-grid
              [dataSource]="dimData"
              [showBorders]="true"
              [rowAlternationEnabled]="true"
              [columnAutoWidth]="true">
            </dx-data-grid>
          </div>
        }

        <!-- Tab 4: Runtime Env -->
        @if (selectedTabIndex === 4) {
          <at-status-card title="Engine Runtime Properties" icon="settings_suggest">
            <div class="config-list env-list">
              @for (entry of statusEntries; track entry[0]) {
                <div class="config-row">
                  <span class="label">{{ entry[0] }}</span>
                  <span class="value mono">{{ formatStatusValue(entry[1]) }}</span>
                </div>
              }
            </div>
          </at-status-card>
        }

      </div>
    </dx-scroll-view>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .config-scroll-view { height: calc(100vh - 180px); }
    .tab-content { padding: 24px 0; }
    
    .config-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    .config-list { display: flex; flex-direction: column; }
    .config-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid rgba(255,255,255,0.05); }
    .config-row .label { font-size: 12px; opacity: 0.6; }
    .config-row .value { font-size: 13px; font-weight: 600; }

    .yaml-hint { margin-top: 24px; display: flex; align-items: center; gap: 8px; font-size: 12px; opacity: 0.6; padding: 12px; background: rgba(255,255,255,0.03); border-radius: 6px; }
    .yaml-hint mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .symbols-layout { display: grid; grid-template-columns: 260px 1fr; gap: 20px; align-items: start; }
    .symbol-grid { display: flex; flex-wrap: wrap; gap: 6px; }
    .symbol-badge { background: rgba(255,255,255,0.05); padding: 4px 8px; border-radius: 4px; font-size: 11px; }

    .search-field { margin-bottom: 16px; }
    .select-dim { width: 300px; margin-bottom: 16px; }
    
    .env-list { max-width: 700px; }
    .font-bold { font-weight: 600; }

    ::ng-deep .dx-tab-selected { color: var(--accent) !important; }
    ::ng-deep .dx-datagrid { background-color: transparent !important; }
  `],
})
export class ConfigComponent implements OnInit {
  tabItems = [
    { text: 'UI & Backend', icon: 'globe' },
    { text: 'Risk & Limits', icon: 'shield' },
    { text: 'Symbol Universe', icon: 'tags' },
    { text: 'Dimensions', icon: 'columnfield' },
    { text: 'Runtime Env', icon: 'variable' }
  ];
  selectedTabIndex = 1;

  modeOptions = [
    { text: 'Live Backend', value: 'live' },
    { text: 'Demo Mode', value: 'mock' }
  ];

  // Risk config
  riskConfig: any = {};
  riskCapitalFields = [
    { key: 'initialCapitalInr', label: 'Initial Capital', format: 'inr' },
    { key: 'maxDailyLossInr', label: 'Max Daily Loss', format: 'inr' },
    { key: 'maxDailyProfitInr', label: 'Max Daily Profit', format: 'inr' },
  ];
  riskSizingFields = [
    { key: 'riskPerTradePct', label: 'Risk Per Trade', format: 'pct' },
    { key: 'maxQtyPerOrder', label: 'Max Qty Per Order', format: 'num' },
    { key: 'maxConsecutiveLossesPerStrategy', label: 'Max Consecutive Losses', format: 'num' },
  ];
  riskTimingFields = [
    { key: 'trailingActivationPct', label: 'Trailing Activation', format: 'pct' },
    { key: 'trailingStepPct', label: 'Trailing Step', format: 'pct' },
    { key: 'noNewTradesAfter', label: 'Trade Cutoff', format: 'str' },
    { key: 'marketCloseTime', label: 'Market Close', format: 'str' },
  ];

  // Symbols
  activeSymbols: string[] = [];
  symbolSearch = '';
  symbolResults: any[] = [];

  // Dimensions
  dimTables = ['exchanges', 'segments', 'instrument_types', 'holding_types', 'order_sides', 'order_status', 'order_types', 'product_types', 'position_sides'];
  selectedDim = 'exchanges';
  dimData: any[] = [];

  // Environment
  engineStatus: any = null;

  // Dynamic Connection
  public connection = inject(ConnectionService);
  localMode: ConnectionMode = this.connection.mode();
  localUrl = this.connection.backendUrl();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.loadAll();
  }

  updateConnection(): void {
    this.connection.setMode(this.localMode);
    this.connection.setBackendUrl(this.localUrl);
    this.loadAll();
  }

  loadAll(): void {
    this.api.getRisk().pipe(catchError(() => of({}))).subscribe(r => this.riskConfig = r);
    this.api.getStatus().pipe(catchError(() => of({ symbols: [] }))).subscribe(s => {
      this.activeSymbols = s.symbols || [];
      this.engineStatus = s;
    });
    this.loadDimension();
  }

  loadDimension(): void {
    this.api.getDimensionTable(this.selectedDim).pipe(catchError(() => of([]))).subscribe(data => {
      this.dimData = data;
    });
  }

  searchSymbols(): void {
    if (this.symbolSearch.length < 2) { this.symbolResults = []; return; }
    this.api.searchSymbolMaster({ q: this.symbolSearch }).pipe(catchError(() => of([]))).subscribe(r => this.symbolResults = r);
  }

  formatValue(obj: any, key: string, format: string): string {
    const v = obj?.[key];
    if (v == null) return '—';
    switch (format) {
      case 'inr': return `₹${Number(v).toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
      case 'pct': return `${(Number(v) * 100).toFixed(1)}%`;
      default: return String(v);
    }
  }

  get statusEntries(): [string, any][] {
    if (!this.engineStatus) return [];
    return Object.entries(this.engineStatus);
  }

  formatStatusValue(v: any): string {
    if (v == null) return '—';
    if (Array.isArray(v)) return v.length > 0 ? v.join(', ') : '(none)';
    if (typeof v === 'boolean') return v ? 'Yes' : 'No';
    return String(v);
  }
}
