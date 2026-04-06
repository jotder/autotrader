import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  DxChartModule,
  DxDataGridModule,
  DxToolbarModule,
  DxSelectBoxModule,
  DxDateBoxModule,
  DxButtonModule,
  DxTabsModule,
} from 'devextreme-angular';
import { Subscription } from 'rxjs';
import { TickDataService, LocalCandleData } from '../../shared/services/tick-data.service';

interface SymbolSummary {
  symbol: string;
  dateCount: number;
  totalCandles: number;
  minDate: string;
  maxDate: string;
  lastDownloaded: string;
}

@Component({
  selector: 'local-data-explorer',
  standalone: true,
  imports: [
    CommonModule,
    DxChartModule,
    DxDataGridModule,
    DxToolbarModule,
    DxSelectBoxModule,
    DxDateBoxModule,
    DxButtonModule,
    DxTabsModule,
  ],
  template: `
    <h2 class="content-block">Local Data Explorer</h2>

    <!-- ── Filter Toolbar ── -->
    <div class="content-block">
      <div class="dx-card responsive-paddings">
        <dx-toolbar>
          <dxi-item location="before" template="symbolTpl"></dxi-item>
          <dxi-item location="before" template="fromTpl"></dxi-item>
          <dxi-item location="before" template="toTpl"></dxi-item>
          <dxi-item location="before" template="clearTpl"></dxi-item>
          <dxi-item location="after"  template="countTpl"></dxi-item>

          <div *dxTemplate="let d of 'symbolTpl'" class="toolbar-field">
            <label class="tb-label">Symbol</label>
            <dx-select-box
              [dataSource]="symbolOptions"
              [(value)]="filterSymbol"
              [showClearButton]="true"
              placeholder="All"
              width="150"
              (onValueChanged)="applyFilter()">
            </dx-select-box>
          </div>

          <div *dxTemplate="let d of 'fromTpl'" class="toolbar-field">
            <label class="tb-label">From</label>
            <dx-date-box
              type="date"
              [(value)]="filterDateFrom"
              [showClearButton]="true"
              width="140"
              (onValueChanged)="applyFilter()">
            </dx-date-box>
          </div>

          <div *dxTemplate="let d of 'toTpl'" class="toolbar-field">
            <label class="tb-label">To</label>
            <dx-date-box
              type="date"
              [(value)]="filterDateTo"
              [showClearButton]="true"
              width="140"
              (onValueChanged)="applyFilter()">
            </dx-date-box>
          </div>

          <div *dxTemplate="let d of 'clearTpl'">
            <dx-button text="Clear" type="normal" icon="revert" (onClick)="clearFilter()"></dx-button>
          </div>

          <div *dxTemplate="let d of 'countTpl'" class="total-chip">
            {{ filteredData.length }} records across {{ summaryRows.length }} symbols
          </div>
        </dx-toolbar>
      </div>
    </div>

    <!-- ── Chart ── -->
    <div class="content-block">
      <div class="dx-card responsive-paddings">
        <div class="panel-title">Candle Count by Date</div>
        <dx-chart
          [dataSource]="dateChartData"
          [size]="{ height: 240 }">

          <dxi-series
            argumentField="date"
            valueField="totalCandles"
            type="bar"
            name="Candles"
            color="#4a90d9">
          </dxi-series>

          <dxo-argument-axis>
            <dxo-label [overlappingBehavior]="'rotate'" [rotationAngle]="-40"></dxo-label>
          </dxo-argument-axis>

          <dxi-value-axis position="left">
            <dxo-title text="Candles"></dxo-title>
            <dxo-label format="#,##0"></dxo-label>
          </dxi-value-axis>

          <dxo-tooltip [enabled]="true" [shared]="false"></dxo-tooltip>
          <dxo-legend [visible]="false"></dxo-legend>
          <dxo-zoom-and-pan argumentAxis="both"></dxo-zoom-and-pan>
        </dx-chart>
      </div>
    </div>

    <!-- ── Summary Grid with master-detail ── -->
    <div class="content-block">
      <div class="dx-card responsive-paddings">
        <div class="panel-title">Symbol Summary <span class="hint-small">(for selected date range — expand a row to see dates)</span></div>
        <dx-data-grid
          [dataSource]="summaryRows"
          [showBorders]="true"
          [columnAutoWidth]="true"
          [rowAlternationEnabled]="true"
          [hoverStateEnabled]="true"
          keyExpr="symbol"
          [focusedRowEnabled]="true"
          [(focusedRowKey)]="focusedSymbol">
          <dxo-filter-row [visible]="true"></dxo-filter-row>
          <dxo-header-filter [visible]="true"></dxo-header-filter>
          <dxo-sorting mode="multiple"></dxo-sorting>
          <dxo-paging [pageSize]="20"></dxo-paging>
          <dxo-pager [showInfo]="true"></dxo-pager>

          <dxi-column dataField="symbol"        caption="Symbol"          [width]="110" sortOrder="asc"  cellTemplate="symTpl"></dxi-column>
          <dxi-column dataField="dateCount"     caption="Days"            [width]="70"  dataType="number"></dxi-column>
          <dxi-column dataField="totalCandles"  caption="Total Candles"   [width]="130" dataType="number" format="#,##0"></dxi-column>
          <dxi-column dataField="minDate"       caption="Earliest Date"   [width]="120"></dxi-column>
          <dxi-column dataField="maxDate"       caption="Latest Date"     [width]="110"></dxi-column>
          <dxi-column dataField="lastDownloaded" caption="Last Downloaded"></dxi-column>

          <div *dxTemplate="let cell of 'symTpl'">
            <strong [style.color]="cell.data.symbol === focusedSymbol ? '#e65100' : '#007bff'">
              {{ cell.value }}
            </strong>
          </div>

          <!-- Grid totals -->
          <dxo-summary>
            <dxi-total-item column="symbol"       summaryType="count"  displayFormat="{0} symbols"></dxi-total-item>
            <dxi-total-item column="dateCount"    summaryType="sum"    displayFormat="{0} total days" valueFormat="#,##0"></dxi-total-item>
            <dxi-total-item column="totalCandles" summaryType="sum"    displayFormat="{0} candles"    valueFormat="#,##0"></dxi-total-item>
          </dxo-summary>

          <!-- Master-detail: date breakdown -->
          <dxo-master-detail [enabled]="true" template="dateDetail"></dxo-master-detail>
          <div *dxTemplate="let detail of 'dateDetail'">
            <div class="detail-header">
              <strong>{{ detail.data.symbol }}</strong> — {{ detail.data.dateCount }} dates
            </div>
            <dx-data-grid
              [dataSource]="getDatesForSymbol(detail.data.symbol)"
              [showBorders]="true"
              [rowAlternationEnabled]="true"
              [columnAutoWidth]="true">
              <dxo-paging [pageSize]="10"></dxo-paging>
              <dxo-pager [showInfo]="true"></dxo-pager>
              <dxo-sorting mode="single"></dxo-sorting>

              <dxi-column dataField="date"        caption="Date"       [width]="120" sortOrder="desc"></dxi-column>
              <dxi-column dataField="candleCount" caption="Candles"    [width]="100" cellTemplate="candleTpl"></dxi-column>
              <dxi-column dataField="timeframe"   caption="Timeframe"  [width]="100" cellTemplate="tfTpl"></dxi-column>
              <dxi-column dataField="fileSize"    caption="File Size"  [width]="100"></dxi-column>
              <dxi-column dataField="downloadedAt" caption="Downloaded At"></dxi-column>

              <div *dxTemplate="let c of 'candleTpl'">
                <span *ngIf="c.value > 0">{{ c.value | number }}</span>
                <span *ngIf="c.value === 0" style="color:#aaa">—</span>
              </div>

              <div *dxTemplate="let c of 'tfTpl'">
                <span class="tf-badge">{{ c.value }}</span>
              </div>

              <dxo-summary>
                <dxi-total-item column="candleCount" summaryType="sum" displayFormat="Total: {0}" valueFormat="#,##0"></dxi-total-item>
              </dxo-summary>
            </dx-data-grid>
          </div>

        </dx-data-grid>
      </div>
    </div>
  `,
  styles: [`
    /* Toolbar */
    .toolbar-field { display: flex; align-items: center; gap: 6px; }
    .tb-label { font-size: 12px; font-weight: 600; white-space: nowrap; color: #555; }
    .total-chip {
      background: #f0f4f8; color: #444;
      border-radius: 12px; padding: 4px 14px; font-size: 12px; white-space: nowrap;
    }

    /* Chart */
    .panel-title { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
    .chart-hint  { font-size: 11px; color: #aaa; margin: 4px 0 0; text-align: center; }
    .hint-small  { font-size: 11px; font-weight: 400; color: #aaa; }

    /* Sym cell */
    strong { cursor: default; }

    /* Timeframe badge */
    .tf-badge {
      background: #e3f0ff; color: #004085;
      border-radius: 4px; padding: 1px 7px; font-size: 11px; font-weight: 600;
    }

    /* Master-detail */
    .detail-header {
      padding: 6px 8px 10px;
      font-size: 13px;
      border-bottom: 1px solid #eee;
      margin-bottom: 8px;
    }
  `]
})
export class LocalDataExplorer implements OnInit, OnDestroy {
  allData: LocalCandleData[] = [];
  filteredData: LocalCandleData[] = [];
  summaryRows: SymbolSummary[] = [];
  symbolOptions: string[] = [];

  filterSymbol: string | null = null;
  filterDateFrom: Date | null = null;
  filterDateTo: Date | null = null;
  focusedSymbol = '';

  private sub!: Subscription;

  constructor(private tickDataService: TickDataService) {}

  ngOnInit() {
    this.sub = this.tickDataService.getLocalData().subscribe(data => {
      this.allData = data;
      this.symbolOptions = [...new Set(data.map(d => d.symbol))].sort();
      this.applyFilter();
    });
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  applyFilter() {
    let result = this.allData;
    if (this.filterSymbol) result = result.filter(d => d.symbol === this.filterSymbol);
    if (this.filterDateFrom) {
      const from = this.toStr(this.filterDateFrom);
      result = result.filter(d => d.date >= from);
    }
    if (this.filterDateTo) {
      const to = this.toStr(this.filterDateTo);
      result = result.filter(d => d.date <= to);
    }
    this.filteredData = result;
    this.buildSummary(result);
  }

  clearFilter() {
    this.filterSymbol = null;
    this.filterDateFrom = null;
    this.filterDateTo = null;
    this.applyFilter();
  }

  getDatesForSymbol(symbol: string): LocalCandleData[] {
    return this.filteredData.filter(d => d.symbol === symbol);
  }

  get dateChartData(): { date: string; totalCandles: number }[] {
    const map = new Map<string, number>();
    this.filteredData.forEach(d => {
      map.set(d.date, (map.get(d.date) ?? 0) + d.candleCount);
    });
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, totalCandles]) => ({ date, totalCandles }));
  }

  private buildSummary(data: LocalCandleData[]) {
    const map = new Map<string, SymbolSummary>();
    data.forEach(d => {
      if (!map.has(d.symbol)) {
        map.set(d.symbol, { symbol: d.symbol, dateCount: 0, totalCandles: 0, minDate: d.date, maxDate: d.date, lastDownloaded: d.downloadedAt });
      }
      const row = map.get(d.symbol)!;
      row.dateCount++;
      row.totalCandles += d.candleCount;
      if (d.date < row.minDate) row.minDate = d.date;
      if (d.date > row.maxDate) row.maxDate = d.date;
      if (d.downloadedAt > row.lastDownloaded) row.lastDownloaded = d.downloadedAt;
    });
    this.summaryRows = Array.from(map.values()).sort((a, b) => a.symbol.localeCompare(b.symbol));
  }

  private toStr(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
