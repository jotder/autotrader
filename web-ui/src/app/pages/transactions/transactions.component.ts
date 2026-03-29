import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, takeUntil, interval } from 'rxjs';
import { GlobalStateService } from '../../core/services/global-state.service';
import { TradeRecord } from '../../core/models/api.models';

// DevExtreme
import { DxDataGridModule } from 'devextreme-angular/ui/data-grid';
import { DxButtonModule } from 'devextreme-angular/ui/button';
import { DxToolbarModule } from 'devextreme-angular/ui/toolbar';
import { DxSelectBoxModule } from 'devextreme-angular/ui/select-box';
import { DxDateBoxModule } from 'devextreme-angular/ui/date-box';

@Component({
  selector: 'at-transactions',
  standalone: true,
  imports: [
    CommonModule, FormsModule, 
    DxDataGridModule, DxButtonModule, DxToolbarModule, DxSelectBoxModule, DxDateBoxModule
  ],
  template: `
    <div class="page-header">
      <h1>Transaction Center</h1>
      <div class="actions">
        <dx-button
          icon="exportxlsx"
          text="Export CSV"
          (onClick)="exportCsv()">
        </dx-button>
      </div>
    </div>

    <!-- DevExtreme Data Grid -->
    <div class="at-card content-card">
      <dx-data-grid
        #grid
        [dataSource]="state.recentTrades()"
        [showBorders]="true"
        [rowAlternationEnabled]="true"
        [columnAutoWidth]="true"
        [hoverStateEnabled]="true"
        [wordWrapEnabled]="true"
        keyExpr="correlationId">

        <dxo-search-panel [visible]="true" [width]="240" placeholder="Search..."></dxo-search-panel>
        <dxo-header-filter [visible]="true"></dxo-header-filter>
        <dxo-group-panel [visible]="true"></dxo-group-panel>
        <dxo-grouping [autoExpandAll]="true"></dxo-grouping>
        
        <dxo-paging [pageSize]="20"></dxo-paging>
        <dxo-pager [showPageSizeSelector]="true" [allowedPageSizes]="[10, 20, 50, 100]"></dxo-pager>

        <dxi-column dataField="entryTime" caption="Time" dataType="datetime" format="HH:mm:ss" sortOrder="desc" [width]="90"></dxi-column>
        <dxi-column dataField="symbol" caption="Symbol" cellTemplate="symbolTemplate" [allowGrouping]="true"></dxi-column>
        <dxi-column dataField="strategyId" caption="Strategy" [allowGrouping]="true"></dxi-column>
        <dxi-column dataField="mode" caption="Mode" cellTemplate="modeTemplate" [width]="80" [allowGrouping]="true"></dxi-column>
        <dxi-column dataField="direction" caption="Dir" cellTemplate="dirTemplate" [width]="70" [allowGrouping]="true"></dxi-column>
        <dxi-column dataField="entryPrice" caption="Entry" format="#,##0.00"></dxi-column>
        <dxi-column dataField="exitPrice" caption="Exit" format="#,##0.00"></dxi-column>
        <dxi-column dataField="pnl" caption="PnL ₹" cellTemplate="pnlTemplate" alignment="right"></dxi-column>
        <dxi-column dataField="rMultipleAchieved" caption="R" format="0.0R" alignment="right" [width]="70"></dxi-column>
        <dxi-column dataField="exitReason" caption="Reason" cellTemplate="reasonTemplate" [allowGrouping]="true"></dxi-column>

        <!-- Summaries -->
        <dxo-summary>
          <dxi-total-item column="pnl" summaryType="sum" displayFormat="Total: {0}" valueFormat="#,##0"></dxi-total-item>
          <dxi-total-item column="symbol" summaryType="count" displayFormat="Count: {0}"></dxi-total-item>
          <dxi-group-item column="pnl" summaryType="sum" displayFormat="PnL: {0}" valueFormat="#,##0" [showInGroupFooter]="false" [alignByColumn]="true"></dxi-group-item>
          <dxi-group-item summaryType="count" displayFormat="{0} trades"></dxi-group-item>
        </dxo-summary>

        <!-- Templates -->
        <div *dxTemplate="let data of 'symbolTemplate'">
          <a class="link mono font-bold" (click)="navSymbol(data.value)">{{ shortSymbol(data.value) }}</a>
        </div>

        <div *dxTemplate="let data of 'modeTemplate'">
          <span class="badge mode">{{ modeLabel(data.value) }}</span>
        </div>

        <div *dxTemplate="let data of 'dirTemplate'">
          <span class="badge" [class.buy]="data.value === 'BUY'" [class.sell]="data.value === 'SELL'">
            {{ data.value }}
          </span>
        </div>

        <div *dxTemplate="let data of 'pnlTemplate'">
          <span class="mono font-bold" [class.profit]="data.value > 0" [class.loss]="data.value < 0">
            {{ formatPnl(data.value) }}
          </span>
        </div>

        <div *dxTemplate="let data of 'reasonTemplate'">
          <span class="badge exit">{{ data.value || '—' }}</span>
        </div>

      </dx-data-grid>
    </div>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .content-card { padding: 0; overflow: hidden; }
    
    .font-bold { font-weight: 600; }
    
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; }
    .badge.buy { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .badge.sell { background: rgba(248, 81, 73, 0.15); color: var(--loss); }
    .badge.mode { background: rgba(0, 188, 212, 0.15); color: var(--accent); }
    .badge.exit { background: var(--bg-hover); color: var(--text-secondary); }

    .link { color: var(--accent); cursor: pointer; text-decoration: none; }
    .link:hover { text-decoration: underline; }

    ::ng-deep .dx-datagrid { background-color: var(--bg-card) !important; color: var(--text-primary) !important; border: none !important; }
    ::ng-deep .dx-datagrid-headers { color: var(--text-secondary); font-size: 11px; text-transform: uppercase; }
    ::ng-deep .dx-datagrid-rowsview .dx-row-focused.dx-data-row > td { background-color: var(--bg-hover); }
    ::ng-deep .dx-datagrid-summary-item { font-family: var(--font-mono); font-weight: 600; color: var(--text-primary); }
  `],
})
export class TransactionsComponent implements OnDestroy {
  private destroy$ = new Subject<void>();

  constructor(
    public state: GlobalStateService,
    private router: Router
  ) {}

  navSymbol(symbol: string): void { this.router.navigate(['/symbols', symbol]); }
  navStrategy(id: string): void { this.router.navigate(['/strategies', id]); }

  exportCsv(): void {
    const headers = ['Time', 'Symbol', 'Strategy', 'Mode', 'Direction', 'Entry', 'Exit', 'Qty', 'PnL', 'R', 'Reason'];
    const rows = this.state.recentTrades().map(t => [
      t.entryTime, t.symbol, t.strategyId, t.mode, t.direction,
      t.entryPrice, t.exitPrice ?? '', t.quantity, t.pnl ?? '',
      t.rMultipleAchieved ?? '', t.exitReason ?? '',
    ].join(','));
    const csv = [headers.join(','), ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `trades-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click(); URL.revokeObjectURL(url);
  }

  modeLabel(m: string): string { return m === 'BACKTEST' ? 'BT' : m === 'PAPER' ? 'PT' : 'LIVE'; }
  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }
  
  formatPnl(v: number | null): string {
    if (v == null) return '—';
    const sign = v >= 0 ? '+' : '';
    return `${sign}₹${v.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
