import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatRadioModule } from '@angular/material/radio';
import { ApiService } from '../../core/services/api.service';
import { ConnectionService, ConnectionMode } from '../../core/services/connection.service';
import { catchError, of } from 'rxjs';
import { StatusCardComponent } from '../../shared/components/status-card.component';

@Component({
  selector: 'at-config',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatIconModule, MatTabsModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatTableModule,
    MatRadioModule, StatusCardComponent
  ],
  template: `
    <div class="page-header">
      <h1>System Configuration</h1>
      <div class="header-actions">
        <button mat-stroked-button color="accent" (click)="loadAll()">
          <mat-icon>refresh</mat-icon> Reload
        </button>
      </div>
    </div>

    <mat-tab-group class="config-tabs">
      <!-- UI & Backend (Dynamic Connection) -->
      <mat-tab label="UI & Backend">
        <div class="tab-content config-grid">
          <at-status-card title="Connection Settings" icon="settings_ethernet" iconColor="var(--accent)">
            <div class="connection-form">
              <div class="field">
                <label>Connection Mode</label>
                <mat-radio-group [(ngModel)]="localMode" (change)="updateConnection()" class="radio-group">
                  <mat-radio-button value="live">Live Backend</mat-radio-button>
                  <mat-radio-button value="mock">Demo / Mock Mode</mat-radio-button>
                </mat-radio-group>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Backend API URL</mat-label>
                <input matInput [(ngModel)]="localUrl" placeholder="/api">
                <mat-hint>Relative (e.g. /api) or Absolute (e.g. http://localhost:7777/api)</mat-hint>
              </mat-form-field>

              <button mat-flat-button color="accent" (click)="updateConnection()" style="margin-top: 16px">
                Apply Connection Settings
              </button>
            </div>
          </at-status-card>

          <at-status-card title="Operational State" icon="info" iconColor="var(--info)">
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
          <span>Switching modes resets the polling feeds. "Live Backend" requires the Spring Boot engine to be running at the specified URL.</span>
        </div>
      </mat-tab>

      <!-- Risk & Capital -->
      <mat-tab label="Risk & Limits">
        <div class="tab-content config-grid">
          <at-status-card title="Capital & Thresholds" icon="account_balance" iconColor="var(--accent)">
            <div class="config-list">
              @for (field of riskCapitalFields; track field.key) {
                <div class="config-row">
                  <span class="label">{{ field.label }}</span>
                  <span class="value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
                </div>
              }
            </div>
          </at-status-card>

          <at-status-card title="Position Sizing" icon="pie_chart" iconColor="var(--info)">
            <div class="config-list">
              @for (field of riskSizingFields; track field.key) {
                <div class="config-row">
                  <span class="label">{{ field.label }}</span>
                  <span class="value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
                </div>
              }
            </div>
          </at-status-card>

          <at-status-card title="Trading Schedule" icon="timer" iconColor="var(--warning)">
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
          <span>These limits are defined in <code>config/defaults.yaml</code>. Changes require an engine restart.</span>
        </div>
      </mat-tab>

      <!-- Symbol Management -->
      <mat-tab label="Symbol Universe">
        <div class="tab-content symbols-layout">
          <div class="side-panel">
            <at-status-card title="Active Selection" icon="list" iconColor="var(--accent)">
              <div class="symbol-scroll">
                @for (s of activeSymbols; track s) {
                  <div class="symbol-chip">
                    <span class="mono">{{ s }}</span>
                  </div>
                }
                @if (activeSymbols.length === 0) {
                  <div class="empty">No active symbols</div>
                }
              </div>
            </at-status-card>
          </div>

          <div class="main-panel">
            <at-status-card title="Symbol Master Explorer" icon="search" iconColor="var(--info)">
              <mat-form-field appearance="outline" class="search-field" subscriptSizing="dynamic">
                <mat-label>Search Exchange Master</mat-label>
                <input matInput [(ngModel)]="symbolSearch" (ngModelChange)="searchSymbols()" placeholder="e.g. SBIN, NIFTY…">
                <mat-icon matSuffix>search</mat-icon>
              </mat-form-field>

              @if (symbolResults.length > 0) {
                <table mat-table [dataSource]="symbolResults.slice(0, 15)" class="master-table">
                  <ng-container matColumnDef="ticker">
                    <th mat-header-cell *matHeaderCellDef> Ticker </th>
                    <td mat-cell *matCellDef="let s" class="mono"> {{ s.symbolTicker }} </td>
                  </ng-container>
                  <ng-container matColumnDef="exchange">
                    <th mat-header-cell *matHeaderCellDef> Exchange </th>
                    <td mat-cell *matCellDef="let s"> {{ s.exchange }} </td>
                  </ng-container>
                  <ng-container matColumnDef="segment">
                    <th mat-header-cell *matHeaderCellDef> Segment </th>
                    <td mat-cell *matCellDef="let s"> {{ s.segment }} </td>
                  </ng-container>
                  <ng-container matColumnDef="lot">
                    <th mat-header-cell *matHeaderCellDef class="r"> Lot </th>
                    <td mat-cell *matCellDef="let s" class="r mono"> {{ s.minLotSize }} </td>
                  </ng-container>

                  <tr mat-header-row *matHeaderRowDef="['ticker', 'exchange', 'segment', 'lot']"></tr>
                  <tr mat-row *matRowDef="let row; columns: ['ticker', 'exchange', 'segment', 'lot'];"></tr>
                </table>
                <div class="table-footer" *ngIf="symbolResults.length > 15">
                  Showing top 15 of {{ symbolResults.length }} matches
                </div>
              }
            </at-status-card>
          </div>
        </div>
      </mat-tab>

      <!-- Dimension Tables -->
      <mat-tab label="Dimensions">
        <div class="tab-content dimensions-layout">
          <div class="dim-toolbar">
            <mat-form-field appearance="outline" subscriptSizing="dynamic">
              <mat-label>Dimension Table</mat-label>
              <mat-select [(ngModel)]="selectedDim" (selectionChange)="loadDimension()">
                @for (d of dimTables; track d) { <mat-option [value]="d">{{ d | titlecase }}</mat-option> }
              </mat-select>
            </mat-form-field>
          </div>

          <div class="table-container at-card">
            <table mat-table [dataSource]="dimData" class="dim-table">
              @for (col of dimColumns; track col) {
                <ng-container [matColumnDef]="col">
                  <th mat-header-cell *matHeaderCellDef> {{ col }} </th>
                  <td mat-cell *matCellDef="let row" class="mono"> {{ row[col] ?? '—' }} </td>
                </ng-container>
              }
              <tr mat-header-row *matHeaderRowDef="dimColumns"></tr>
              <tr mat-row *matRowDef="let row; columns: dimColumns;"></tr>
            </table>
          </div>
        </div>
      </mat-tab>

      <!-- Environment -->
      <mat-tab label="Runtime Env">
        <div class="tab-content">
          <at-status-card title="Engine Runtime Properties" icon="settings_suggest" iconColor="var(--accent)">
            <div class="config-list env-list">
              @for (entry of statusEntries; track entry[0]) {
                <div class="config-row">
                  <span class="label">{{ entry[0] }}</span>
                  <span class="value mono">{{ formatStatusValue(entry[1]) }}</span>
                </div>
              }
            </div>
          </at-status-card>
        </div>
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 22px; font-weight: 500; }

    .tab-content { padding: 20px 0; }
    .config-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }

    .config-list { display: flex; flex-direction: column; }
    .config-row { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--border); }
    .config-row:last-child { border-bottom: none; }
    .config-row .label { font-size: 12px; color: var(--text-secondary); }
    .config-row .value { font-size: 13px; font-weight: 600; color: var(--text-primary); }

    .yaml-hint { margin-top: 24px; display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-muted); padding: 12px; background: var(--bg-secondary); border-radius: 6px; }
    .yaml-hint mat-icon { font-size: 18px; width: 18px; height: 18px; }

    .symbols-layout { display: grid; grid-template-columns: 240px 1fr; gap: 20px; }
    .symbol-scroll { max-height: 400px; overflow-y: auto; display: flex; flex-wrap: wrap; gap: 6px; }
    .symbol-chip { background: var(--bg-hover); padding: 4px 10px; border-radius: 4px; font-size: 11px; border: 1px solid var(--border); }

    .search-field { width: 100%; margin-bottom: 16px; }
    .master-table { width: 100%; background: transparent; }
    .table-footer { padding: 8px; font-size: 11px; color: var(--text-muted); text-align: center; }
    .r { text-align: right !important; justify-content: flex-end; }

    .dimensions-layout { display: flex; flex-direction: column; gap: 16px; }
    .dim-toolbar { display: flex; }
    .table-container { overflow-x: auto; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; }
    .dim-table { width: 100%; background: transparent; }

    .env-list { max-width: 600px; }
  `],
})
export class ConfigComponent implements OnInit {
  activeTab = 'Risk';

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
  dimColumns: string[] = [];

  // Environment
  engineStatus: any = null;

  // Dynamic Connection (UI Local State)
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
    // Reload data after applying settings
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
      this.dimColumns = data.length > 0 ? Object.keys(data[0]) : [];
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
      case 'num': return String(v);
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
