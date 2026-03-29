import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormControl } from '@angular/forms';
import { Subject, takeUntil, interval, switchMap, catchError, of, startWith, debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { MetricRowComponent } from '../../shared/components/metric-row.component';
import { MatIconModule } from '@angular/material/icon';

// DevExtreme Imports
import { DxTagBoxModule } from 'devextreme-angular/ui/tag-box';
import { DxDateBoxModule } from 'devextreme-angular/ui/date-box';
import { DxDataGridModule } from 'devextreme-angular/ui/data-grid';
import { DxButtonModule } from 'devextreme-angular/ui/button';
import { DxSelectBoxModule } from 'devextreme-angular/ui/select-box';

interface BacktestResult {
  overall: any;
  byStrategy: Record<string, any>;
  bySymbol: Record<string, any>;
  equityCurve: number[];
  suggestions: string[];
  summary: string;
}

interface DownloadJob {
  jobId: string;
  status: string;
  symbols: string[];
  progress?: string;
  startTime?: string;
}

interface DataSummary {
  symbol: string;
  startDate: string;
  endDate: string;
  count: number;
}

@Component({
  selector: 'at-backtest',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    StatusCardComponent, MetricRowComponent, MatIconModule,
    DxTagBoxModule, DxDateBoxModule, DxDataGridModule, DxButtonModule, DxSelectBoxModule
  ],
  template: `
    <div class="page-header">
      <h1>Backtest & Data Manager</h1>
    </div>

    <div class="backtest-layout">
      <!-- Left: Controls -->
      <div class="controls-panel">
        
        <!-- M1 Data Downloader -->
        <div class="at-card section">
          <h3><mat-icon>cloud_download</mat-icon> Download M1 Data</h3>
          
          <div class="field">
            <label>Search Symbols</label>
            <dx-tag-box
              [dataSource]="allSymbolsSource"
              displayExpr="symbolTicker"
              valueExpr="symbolTicker"
              [(value)]="selectedDlSymbols"
              [searchEnabled]="true"
              placeholder="Search symbols..."
              stylingMode="outlined"
              [showSelectionControls]="true"
              [maxDisplayedTags]="3"
              [multiline]="true">
              <div *dxTemplate="let data of 'item'">
                <div class="symbol-item">
                  <span class="mono">{{ data.symbolTicker }}</span>
                  <small class="text-muted">{{ data.symbolDetails }}</small>
                </div>
              </div>
            </dx-tag-box>
          </div>

          <div class="field-row">
            <div class="field">
              <label>From</label>
              <dx-date-box
                type="date"
                [(value)]="dlFrom"
                displayFormat="yyyy-MM-dd"
                stylingMode="outlined">
              </dx-date-box>
            </div>
            <div class="field">
              <label>To</label>
              <dx-date-box
                type="date"
                [(value)]="dlTo"
                displayFormat="yyyy-MM-dd"
                stylingMode="outlined">
              </dx-date-box>
            </div>
          </div>

          <dx-button
            class="action-btn"
            text="{{ dlPending ? 'Downloading…' : 'Start Download' }}"
            type="default"
            stylingMode="contained"
            [disabled]="dlPending || selectedDlSymbols.length === 0 || !dlFrom || !dlTo"
            (onClick)="startDownload()">
          </dx-button>

          <!-- Active downloads -->
          @if (downloads.length > 0) {
            <div class="downloads">
              <label>Active Jobs</label>
              @for (d of downloads; track d.jobId) {
                <div class="dl-row">
                  <span class="dl-status badge" [class]="dlStatusClass(d.status)">{{ d.status }}</span>
                  <span class="mono text-muted">{{ d.symbols.join(', ') }}</span>
                  @if (d.progress) { <span class="text-muted">{{ d.progress }}</span> }
                </div>
              }
            </div>
          }
        </div>

        <!-- Backtest Form -->
        <div class="at-card section">
          <h3><mat-icon>science</mat-icon> Run Backtest</h3>
          <div class="field">
            <label>Symbol</label>
            <dx-select-box
              [items]="availableSymbols"
              [(value)]="btSymbol"
              placeholder="Select symbol..."
              [searchEnabled]="true"
              stylingMode="outlined"
              (onValueChanged)="onSymbolChange()">
            </dx-select-box>
          </div>
          <div class="field-row">
            <div class="field">
              <label>From</label>
              <dx-date-box
                type="date"
                [(value)]="btFrom"
                displayFormat="yyyy-MM-dd"
                stylingMode="outlined">
              </dx-date-box>
            </div>
            <div class="field">
              <label>To</label>
              <dx-date-box
                type="date"
                [(value)]="btTo"
                displayFormat="yyyy-MM-dd"
                stylingMode="outlined">
              </dx-date-box>
            </div>
          </div>

          @if (btSymbol) {
            <div class="available-dates text-muted">
              @if (availableDates.length > 0) {
                Available: {{ availableDates[0] }} to {{ availableDates[availableDates.length - 1] }} ({{ availableDates.length }} days)
              } @else {
                No dates available for this symbol
              }
            </div>
          }

          <dx-button
            class="action-btn"
            text="{{ btRunning ? 'Running…' : 'Run Backtest' }}"
            type="default"
            stylingMode="contained"
            [disabled]="btRunning || !btSymbol || !btFrom || !btTo"
            (onClick)="runBacktest()">
          </dx-button>
          
          @if (btError) {
            <div class="error-msg">{{ btError }}</div>
          }
        </div>

        <!-- Session History -->
        @if (history.length > 0) {
          <div class="at-card section">
            <h3><mat-icon>history</mat-icon> History</h3>
            @for (h of history; track $index) {
              <div class="history-row" [class.active]="h === result" (click)="result = h">
                <span class="mono">{{ shortSymbol(h._symbol) }}</span>
                <span class="mono text-muted">{{ h._dateRange }}</span>
                <span class="mono" [class]="(h.overall?.totalPnl ?? 0) >= 0 ? 'profit' : 'loss'">
                  {{ formatPnl(h.overall?.totalPnl) }}
                </span>
              </div>
            }
          </div>
        }
      </div>

      <!-- Right: Results & Data Summary -->
      <div class="results-panel">
        
        <!-- Data Summary DataGrid -->
        <div class="at-card section summary-card">
          <div class="card-header">
            <h3><mat-icon>storage</mat-icon> Available Data Summary</h3>
            <dx-button
              icon="refresh"
              stylingMode="text"
              (onClick)="refreshSummary()">
            </dx-button>
          </div>
          
          <dx-data-grid
            [dataSource]="dataSummary"
            [showBorders]="true"
            [rowAlternationEnabled]="true"
            [columnAutoWidth]="true">
            
            <dxo-sorting mode="multiple"></dxo-sorting>
            
            <dxi-column dataField="symbol" caption="Symbol" [allowSorting]="true" cellTemplate="symbolTemplate"></dxi-column>
            <dxi-column dataField="startDate" caption="Start Date" dataType="date"></dxi-column>
            <dxi-column dataField="endDate" caption="End Date" dataType="date"></dxi-column>
            <dxi-column dataField="count" caption="Days" alignment="right"></dxi-column>
            <dxi-column caption="Actions" cellTemplate="actionsTemplate" alignment="center" [width]="100"></dxi-column>

            <div *dxTemplate="let data of 'symbolTemplate'">
              <span class="mono font-bold">{{ data.value }}</span>
            </div>

            <div *dxTemplate="let data of 'actionsTemplate'">
              <dx-button
                text="Update"
                stylingMode="outlined"
                type="default"
                (onClick)="prepareSync(data.data)">
              </dx-button>
            </div>
          </dx-data-grid>
        </div>

        @if (!result) {
          <div class="empty-results" style="margin-top: 24px">
            <mat-icon>science</mat-icon>
            <p>Select a symbol and run a backtest to see results here.</p>
          </div>
        } @else {
          <!-- Result tabs -->
          <div class="tabs" style="margin-top: 24px">
            @for (tab of ['Overview', 'By Strategy', 'By Symbol', 'Suggestions']; track tab) {
              <button class="tab" [class.active]="resultTab === tab" (click)="resultTab = tab">{{ tab }}</button>
            }
          </div>

          @if (resultTab === 'Overview') {
            <div class="results-grid">
              <at-status-card title="Performance" icon="trending_up" iconColor="var(--accent)">
                <at-metric label="Total Trades" [value]="result.overall?.total ?? '—'" />
                <at-metric label="Winners" [value]="result.overall?.wins ?? '—'" [valueClass]="'profit'" />
                <at-metric label="Losers" [value]="result.overall?.losses ?? '—'" [valueClass]="'loss'" />
                <at-metric label="Win Rate" [value]="formatPct(result.overall?.winRate)" [valueClass]="(result.overall?.winRate ?? 0) >= 0.5 ? 'profit' : 'loss'" />
              </at-status-card>
              <at-status-card title="Returns" icon="attach_money" [iconColor]="(result.overall?.totalPnl ?? 0) >= 0 ? 'var(--profit)' : 'var(--loss)'">
                <at-metric label="Total PnL" [value]="formatPnl(result.overall?.totalPnl)" [valueClass]="(result.overall?.totalPnl ?? 0) >= 0 ? 'profit' : 'loss'" />
                <at-metric label="Profit Factor" [value]="result.overall?.profitFactor?.toFixed(2) ?? '—'" />
                <at-metric label="Expectancy" [value]="formatPnl(result.overall?.expectancy)" />
                <at-metric label="Avg R" [value]="(result.overall?.avgR?.toFixed(2) ?? '—') + 'R'" />
              </at-status-card>
              <at-status-card title="Risk" icon="shield" iconColor="var(--warning)">
                <at-metric label="Sharpe Ratio" [value]="result.overall?.sharpe?.toFixed(2) ?? '—'" />
                <at-metric label="Max Drawdown" [value]="formatPct(result.overall?.maxDrawdown)" [valueClass]="'loss'" />
                <at-metric label="Avg Hold (min)" [value]="result.overall?.avgHoldMinutes?.toFixed(0) ?? '—'" />
                <at-metric label="Max Consec Losses" [value]="result.overall?.maxConsecLosses ?? '—'" />
              </at-status-card>
            </div>

            <!-- Equity curve (text) -->
            @if (result.equityCurve?.length > 0) {
              <div class="at-card section" style="margin-top: 16px">
                <h3>Equity Curve</h3>
                <div class="equity-curve">
                  @for (val of equityCurveSampled; track $index) {
                    <div class="eq-bar" [style.height.%]="eqBarHeight(val)" [class]="val >= 0 ? 'profit-bar' : 'loss-bar'"
                         [title]="'₹' + val.toFixed(0)"></div>
                  }
                </div>
                <div class="eq-labels">
                  <span class="text-muted">Start</span>
                  <span class="mono" [class]="eqFinal >= 0 ? 'profit' : 'loss'">{{ formatPnl(eqFinal) }}</span>
                  <span class="text-muted">End</span>
                </div>
              </div>
            }
          }

          @if (resultTab === 'By Strategy') {
            <div class="breakdown-table">
              @if (strategyEntries.length > 0) {
                <table class="at-table">
                  <thead><tr>
                    <th>Strategy</th><th class="r">Trades</th><th class="r">Win Rate</th>
                    <th class="r">PnL</th><th class="r">PF</th><th class="r">Sharpe</th><th class="r">Avg R</th>
                  </tr></thead>
                  <tbody>
                    @for (entry of strategyEntries; track entry[0]) {
                      <tr>
                        <td>{{ entry[0] }}</td>
                        <td class="r mono">{{ entry[1].total }}</td>
                        <td class="r mono" [class]="entry[1].winRate >= 0.5 ? 'profit' : 'loss'">{{ formatPct(entry[1].winRate) }}</td>
                        <td class="r mono" [class]="entry[1].totalPnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(entry[1].totalPnl) }}</td>
                        <td class="r mono">{{ entry[1].profitFactor?.toFixed(2) ?? '—' }}</td>
                        <td class="r mono">{{ entry[1].sharpe?.toFixed(2) ?? '—' }}</td>
                        <td class="r mono">{{ entry[1].avgR?.toFixed(2) ?? '—' }}R</td>
                      </tr>
                    }
                  </tbody>
                </table>
              } @else {
                <div class="empty">No strategy breakdown available</div>
              }
            </div>
          }

          @if (resultTab === 'By Symbol') {
            <div class="breakdown-table">
              @if (symbolEntries.length > 0) {
                <table class="at-table">
                  <thead><tr>
                    <th>Symbol</th><th class="r">Trades</th><th class="r">Win Rate</th>
                    <th class="r">PnL</th><th class="r">PF</th><th class="r">Avg R</th>
                  </tr></thead>
                  <tbody>
                    @for (entry of symbolEntries; track entry[0]) {
                      <tr>
                        <td class="mono">{{ shortSymbol(entry[0]) }}</td>
                        <td class="r mono">{{ entry[1].total }}</td>
                        <td class="r mono" [class]="entry[1].winRate >= 0.5 ? 'profit' : 'loss'">{{ formatPct(entry[1].winRate) }}</td>
                        <td class="r mono" [class]="entry[1].totalPnl >= 0 ? 'profit' : 'loss'">{{ formatPnl(entry[1].totalPnl) }}</td>
                        <td class="r mono">{{ entry[1].profitFactor?.toFixed(2) ?? '—' }}</td>
                        <td class="r mono">{{ entry[1].avgR?.toFixed(2) ?? '—' }}R</td>
                      </tr>
                    }
                  </tbody>
                </table>
              } @else {
                <div class="empty">No symbol breakdown available</div>
              }
            </div>
          }

          @if (resultTab === 'Suggestions') {
            <div class="suggestions at-card section">
              @if (result.suggestions?.length > 0) {
                @for (s of result.suggestions; track $index) {
                  <div class="suggestion-row">
                    <mat-icon>lightbulb</mat-icon>
                    <span>{{ s }}</span>
                  </div>
                }
              } @else {
                <div class="empty">No suggestions generated</div>
              }
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .page-header { margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .backtest-layout { display: grid; grid-template-columns: 320px 1fr; gap: 16px; }

    .controls-panel { display: flex; flex-direction: column; gap: 12px; }
    .section { padding: 14px; }
    .section h3 { margin: 0 0 10px; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; color: var(--accent); }
    .section h3 mat-icon { font-size: 18px; }

    .field { margin-bottom: 10px; }
    .field label { display: block; font-size: 10px; text-transform: uppercase; color: var(--text-muted); margin-bottom: 3px; letter-spacing: 0.5px; }
    .field-row {display:flex;gap:8px;}
    .field-row .field {flex:1;}

    .symbol-item { display: flex; flex-direction: column; }
    .symbol-item small { font-size: 10px; }

    .action-btn { width: 100%; margin-top: 8px; }
    
    .downloads {margin-top:10px;}
    .downloads label {font-size:10px;text-transform:uppercase;color:var(--text-muted);display:block;margin-bottom:4px;}
    .dl-row {display:flex;gap:8px;align-items:center;font-size:11px;padding:3px 0;}

    .available-dates {font-size:11px;margin:4px 0 4px;}
    .error-msg {color:var(--loss);font-size:12px;margin-top:6px;}

    .history-row {display:flex;justify-content:space-between;padding:5px 0;cursor:pointer;font-size:12px;border-bottom:1px solid var(--bg-hover);}
    .history-row:hover {background:var(--bg-hover);}
    .history-row.active {border-left:2px solid var(--accent);padding-left:6px;}

    .results-panel {min-height:400px; display: flex; flex-direction: column; gap: 16px; }
    .empty-results {display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:300px;color:var(--text-muted);gap:12px;}
    .empty-results mat-icon {font-size:48px;opacity:0.3;}
    .empty-results p {font-size:13px;}

    .tabs {display:flex;gap:2px;border-bottom:1px solid var(--border);}
    .tab {background:transparent;border:none;color:var(--text-secondary);padding:8px 16px;cursor:pointer;font-size:13px;border-bottom:2px solid transparent;}
    .tab:hover {color:var(--text-primary);}
    .tab.active {color:var(--accent);border-bottom-color:var(--accent);}

    .results-grid {display:grid;grid-template-columns:repeat(3,1fr);gap:12px;}

    .equity-curve {display:flex;align-items:flex-end;height:80px;gap:1px;padding:8px 0;}
    .eq-bar {flex:1;min-width:2px;border-radius:1px 1px 0 0;transition:height 0.2s;}
    .profit-bar {background:var(--profit);}
    .loss-bar {background:var(--loss);}
    .eq-labels {display:flex;justify-content:space-between;font-size:10px;}

    .at-table {width:100%;border-collapse:collapse;font-size:12px;}
    .at-table th {text-align:left;font-size:10px;text-transform:uppercase;color:var(--text-secondary);padding:5px 6px;border-bottom:1px solid var(--border);}
    .at-table td {padding:5px 6px;border-bottom:1px solid var(--bg-hover);}
    .at-table tr:hover td {background:var(--bg-hover);}
    .r {text-align:right;}

    .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .card-header h3 { margin: 0; }

    .font-bold { font-weight: 600; }

    .suggestion-row {display:flex;gap:8px;align-items:flex-start;padding:6px 0;font-size:12px;border-bottom:1px solid var(--bg-hover);}
    .suggestion-row mat-icon {font-size:16px;color:var(--warning);flex-shrink:0;margin-top:1px;}

    .badge {padding:2px 6px;border-radius:3px;font-size:10px;font-weight:600;}
    .badge.RUNNING,.badge.running {background:rgba(0,188,212,0.15);color:var(--accent);}
    .badge.COMPLETED,.badge.completed {background:rgba(63,185,80,0.15);color:var(--profit);}
    .badge.FAILED,.badge.failed {background:rgba(248,81,73,0.15);color:var(--loss);}
    .empty {color:var(--text-muted);font-size:13px;padding:24px;text-align:center;}

    ::ng-deep .dx-datagrid { background-color: transparent !important; }
    ::ng-deep .dx-datagrid-headers { color: var(--text-secondary); font-size: 11px; text-transform: uppercase; }
  `],
})
export class BacktestComponent implements OnInit, OnDestroy {
  // Data manager
  availableSymbols: string[] = [];
  availableDates: string[] = [];
  dataSummary: DataSummary[] = [];
  downloads: DownloadJob[] = [];
  
  // DevExtreme Data Sources
  allSymbolsSource: any[] = [];

  // Download Form
  symbolSearchCtrl = new FormControl('');
  selectedDlSymbols: string[] = [];
  dlFrom: any = null;
  dlTo: any = null;
  dlPending = false;

  // Backtest form
  btSymbol = '';
  btFrom: any = null;
  btTo: any = null;
  btRunning = false;
  btError = '';

  // Results
  result: any = null;
  resultTab = 'Overview';
  history: any[] = [];

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    // Initial data load
    this.refreshAvailable();
    this.refreshSummary();

    // Load all symbols for search (simplified for this migration turn)
    this.api.searchSymbols('').subscribe(list => this.allSymbolsSource = list);

    // Poll downloads
    interval(5000).pipe(
      startWith(0),
      switchMap(() => this.api.getDownloads().pipe(catchError(() => of([])))),
      takeUntil(this.destroy$),
    ).subscribe(dl => this.downloads = dl);
  }

  refreshAvailable(): void {
    this.api.getCandleDbSymbols().pipe(catchError(() => of([]))).subscribe(s => this.availableSymbols = s);
  }

  refreshSummary(): void {
    this.api.getCandleDbSummary().pipe(catchError(() => of([]))).subscribe(s => this.dataSummary = s);
  }

  prepareSync(item: DataSummary): void {
    if (!this.selectedDlSymbols.includes(item.symbol)) {
      this.selectedDlSymbols = [...this.selectedDlSymbols, item.symbol];
    }
    this.dlFrom = item.startDate;
    this.dlTo = new Date();
  }

  // ── Symbol selection → load dates ────────────────────────
  onSymbolChange(): void {
    if (this.btSymbol) {
      this.api.getCandleDbDates(this.btSymbol).pipe(catchError(() => of([]))).subscribe(d => {
        this.availableDates = d;
        if (d.length > 0) {
          this.btFrom = d[0];
          this.btTo = d[d.length - 1];
        }
      });
    } else {
      this.availableDates = [];
    }
  }

  // ── Download ─────────────────────────────────────────────
  startDownload(): void {
    if (this.selectedDlSymbols.length === 0) return;
    this.dlPending = true;
    
    const fromStr = this.formatDate(this.dlFrom);
    const toStr = this.formatDate(this.dlTo);

    this.api.startDownload(this.selectedDlSymbols, fromStr, toStr).subscribe({
      next: () => {
        this.dlPending = false;
        this.selectedDlSymbols = [];
        // Refresh UI state after a delay
        setTimeout(() => {
          this.refreshAvailable();
          this.refreshSummary();
        }, 3000);
      },
      error: (e) => { this.dlPending = false; this.btError = e.error?.message || 'Download failed'; },
    });
  }

  // ── Backtest ─────────────────────────────────────────────
  runBacktest(): void {
    this.btRunning = true;
    this.btError = '';
    
    const fromStr = this.formatDate(this.btFrom);
    const toStr = this.formatDate(this.btTo);

    this.api.runBacktest(this.btSymbol, fromStr, toStr).subscribe({
      next: (r) => {
        this.btRunning = false;
        r._symbol = this.btSymbol;
        r._dateRange = `${fromStr} → ${toStr}`;
        this.result = r;
        this.resultTab = 'Overview';
        this.history.unshift(r);
        if (this.history.length > 5) this.history.pop();
      },
      error: (e) => {
        this.btRunning = false;
        this.btError = e.error?.message || e.message || 'Backtest failed';
      },
    });
  }

  private formatDate(val: any): string {
    if (!val) return '';
    if (typeof val === 'string') return val;
    if (val instanceof Date) return val.toISOString().split('T')[0];
    return '';
  }

  // ── Result accessors ─────────────────────────────────────
  get strategyEntries(): [string, any][] {
    return this.result?.byStrategy ? Object.entries(this.result.byStrategy) : [];
  }

  get symbolEntries(): [string, any][] {
    return this.result?.bySymbol ? Object.entries(this.result.bySymbol) : [];
  }

  get equityCurveSampled(): number[] {
    const curve = this.result?.equityCurve;
    if (!curve || curve.length === 0) return [];
    if (curve.length <= 60) return curve;
    const step = Math.ceil(curve.length / 60);
    return curve.filter((_: any, i: number) => i % step === 0);
  }

  get eqFinal(): number {
    const curve = this.result?.equityCurve;
    return curve?.length > 0 ? curve[curve.length - 1] : 0;
  }

  eqBarHeight(val: number): number {
    const curve = this.equityCurveSampled;
    const max = Math.max(...curve.map(Math.abs), 1);
    return Math.max(5, (Math.abs(val) / max) * 100);
  }

  // ── Helpers ──────────────────────────────────────────────
  dlStatusClass(status: string): string { return status; }
  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
  formatPnl(v: number | null | undefined): string {
    if (v == null) return '—';
    return `${v >= 0 ? '+' : ''}₹${v.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  }
  formatPct(v: number | null | undefined): string {
    if (v == null) return '—';
    return `${(v * 100).toFixed(1)}%`;
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
