import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/services/api.service';
import { catchError, of } from 'rxjs';
import { StatusCardComponent } from '../../shared/components/status-card.component';

@Component({
  selector: 'at-config',
  standalone: true,
  imports: [CommonModule, FormsModule, MatIconModule, StatusCardComponent],
  template: `
    <div class="page-header">
      <h1>Configuration</h1>
      <span class="text-muted" style="font-size: 12px">Read-only view — edit via YAML files + hot-reload</span>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      @for (tab of tabs; track tab) {
        <button class="tab" [class.active]="activeTab === tab" (click)="activeTab = tab">{{ tab }}</button>
      }
    </div>

    <!-- Risk Config -->
    @if (activeTab === 'Risk') {
      <div class="config-grid">
        <at-status-card title="Capital & Limits" icon="account_balance" iconColor="var(--accent)">
          @for (field of riskCapitalFields; track field.key) {
            <div class="config-row">
              <span class="config-label">{{ field.label }}</span>
              <span class="config-value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
            </div>
          }
        </at-status-card>
        <at-status-card title="Position Sizing" icon="pie_chart" iconColor="var(--info)">
          @for (field of riskSizingFields; track field.key) {
            <div class="config-row">
              <span class="config-label">{{ field.label }}</span>
              <span class="config-value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
            </div>
          }
        </at-status-card>
        <at-status-card title="Trailing & Timing" icon="timer" iconColor="var(--warning)">
          @for (field of riskTimingFields; track field.key) {
            <div class="config-row">
              <span class="config-label">{{ field.label }}</span>
              <span class="config-value mono">{{ formatValue(riskConfig, field.key, field.format) }}</span>
            </div>
          }
        </at-status-card>
      </div>
    }

    <!-- Symbols -->
    @if (activeTab === 'Symbols') {
      <div class="symbols-section">
        <at-status-card title="Active Symbols" icon="list" iconColor="var(--accent)">
          @if (activeSymbols.length > 0) {
            <div class="symbol-list">
              @for (s of activeSymbols; track s) {
                <div class="symbol-item">
                  <span class="mono">{{ s }}</span>
                </div>
              }
            </div>
          } @else {
            <div class="empty">No active symbols configured</div>
          }
          <div class="config-hint">
            <mat-icon>info</mat-icon>
            Edit <code>.env</code> → <code>ACTIVE_SYMBOLS</code> or strategy YAML files to change
          </div>
        </at-status-card>

        <at-status-card title="Symbol Master" icon="storage" iconColor="var(--info)" style="margin-top: 16px">
          <div class="search-section">
            <input type="text" [(ngModel)]="symbolSearch" (ngModelChange)="searchSymbols()" placeholder="Search symbol master (e.g., SBIN, RELIANCE, NIFTY)…" class="search-input" />
          </div>
          @if (symbolResults.length > 0) {
            <div class="table-wrap">
              <table class="at-table">
                <thead><tr>
                  <th>Ticker</th><th>Exchange</th><th>Segment</th><th>Lot</th><th>Tick</th>
                </tr></thead>
                <tbody>
                  @for (s of symbolResults.slice(0, 20); track s.symbolTicker) {
                    <tr>
                      <td class="mono">{{ s.symbolTicker }}</td>
                      <td>{{ s.exchange }}</td>
                      <td>{{ s.segment }}</td>
                      <td class="r mono">{{ s.minLotSize }}</td>
                      <td class="r mono">{{ s.tickSize }}</td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (symbolResults.length > 20) {
                <div class="text-muted" style="font-size: 11px; padding: 4px 6px;">Showing 20 of {{ symbolResults.length }} results</div>
              }
            </div>
          }
        </at-status-card>
      </div>
    }

    <!-- Dimensions -->
    @if (activeTab === 'Dimensions') {
      <div class="dim-section">
        <div class="dim-selector">
          <label>Table</label>
          <select [(ngModel)]="selectedDim" (ngModelChange)="loadDimension()">
            @for (d of dimTables; track d) {
              <option [value]="d">{{ d }}</option>
            }
          </select>
        </div>
        @if (dimData.length > 0) {
          <div class="table-wrap">
            <table class="at-table">
              <thead><tr>
                @for (col of dimColumns; track col) {
                  <th>{{ col }}</th>
                }
              </tr></thead>
              <tbody>
                @for (row of dimData; track $index) {
                  <tr>
                    @for (col of dimColumns; track col) {
                      <td class="mono">{{ row[col] ?? '—' }}</td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="empty">Select a dimension table to view</div>
        }
      </div>
    }

    <!-- Environment -->
    @if (activeTab === 'Environment') {
      <at-status-card title="Engine Status" icon="settings" iconColor="var(--accent)">
        @if (engineStatus) {
          @for (entry of statusEntries; track entry[0]) {
            <div class="config-row">
              <span class="config-label">{{ entry[0] }}</span>
              <span class="config-value mono">{{ formatStatusValue(entry[1]) }}</span>
            </div>
          }
        } @else {
          <div class="empty">Engine not reachable</div>
        }
        <div class="config-hint" style="margin-top: 12px">
          <mat-icon>info</mat-icon>
          Environment is configured via <code>.env</code> file and strategy YAML files. Changes require restart or hot-reload.
        </div>
      </at-status-card>
    }
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .tabs { display: flex; gap: 2px; margin-bottom: 16px; border-bottom: 1px solid var(--border); }
    .tab { background: transparent; border: none; color: var(--text-secondary); padding: 8px 16px; cursor: pointer; font-size: 13px; border-bottom: 2px solid transparent; }
    .tab:hover { color: var(--text-primary); }
    .tab.active { color: var(--accent); border-bottom-color: var(--accent); }

    .config-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }

    .config-row { display: flex; justify-content: space-between; align-items: center; padding: 5px 0; border-bottom: 1px solid var(--bg-hover); }
    .config-label { font-size: 12px; color: var(--text-secondary); }
    .config-value { font-size: 13px; font-weight: 500; }

    .config-hint { display: flex; align-items: center; gap: 6px; font-size: 11px; color: var(--text-muted); margin-top: 12px; padding: 8px; background: var(--bg-hover); border-radius: 4px; }
    .config-hint mat-icon { font-size: 16px; }
    .config-hint code { background: var(--bg-primary); padding: 1px 4px; border-radius: 2px; font-family: var(--font-mono); }

    .symbol-list { display: flex; flex-wrap: wrap; gap: 6px; }
    .symbol-item { background: var(--bg-hover); padding: 4px 10px; border-radius: 4px; font-size: 12px; }

    .search-section { margin-bottom: 10px; }
    .search-input { width: 100%; padding: 8px 12px; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 4px; color: var(--text-primary); font-size: 12px; font-family: var(--font-mono); box-sizing: border-box; }

    .dim-section { }
    .dim-selector { margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
    .dim-selector label { font-size: 12px; color: var(--text-secondary); }
    .dim-selector select { padding: 6px 10px; background: var(--bg-card); border: 1px solid var(--border); color: var(--text-primary); border-radius: 4px; font-size: 12px; }

    .table-wrap { overflow-x: auto; }
    .at-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .at-table th { text-align: left; font-size: 10px; text-transform: uppercase; color: var(--text-secondary); padding: 5px 6px; border-bottom: 1px solid var(--border); }
    .at-table td { padding: 5px 6px; border-bottom: 1px solid var(--bg-hover); }
    .at-table tr:hover td { background: var(--bg-hover); }
    .r { text-align: right; }
    .empty { color: var(--text-muted); font-size: 13px; padding: 16px; text-align: center; }
  `],
})
export class ConfigComponent implements OnInit {
  activeTab = 'Risk';
  tabs = ['Risk', 'Symbols', 'Dimensions', 'Environment'];

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
    { key: 'noNewTradesAfter', label: 'No New Trades After', format: 'str' },
    { key: 'marketCloseTime', label: 'Market Close Time', format: 'str' },
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

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    // Load risk config
    this.api.getRisk().pipe(catchError(() => of({}))).subscribe(r => this.riskConfig = r);

    // Load active symbols from status
    this.api.getStatus().pipe(catchError(() => of({ symbols: [] }))).subscribe(s => this.activeSymbols = s.symbols || []);

    // Load engine status
    this.api.getStatus().pipe(catchError(() => of(null))).subscribe(s => this.engineStatus = s);

    // Load first dimension
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
