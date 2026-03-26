import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, takeUntil, forkJoin, catchError, of } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { TradeRecord } from '../../core/models/api.models';
import { MatIconModule } from '@angular/material/icon';

interface MarketNode {
  name: string;
  tradeCount: number;
  totalPnl: number;
  symbolCount: number;
  children?: MarketNode[];
  symbols?: string[];
}

@Component({
  selector: 'at-market',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="page-header">
      <h1>Market Explorer</h1>
    </div>

    <!-- Breadcrumb -->
    <div class="breadcrumb">
      <a class="crumb" (click)="navigateTo(0)">Market</a>
      @for (level of breadcrumb; track $index) {
        <span class="sep">/</span>
        <a class="crumb" [class.active]="$index === breadcrumb.length - 1" (click)="navigateTo($index + 1)">{{ level }}</a>
      }
    </div>

    <!-- Current level nodes -->
    @if (currentNodes.length > 0) {
      <div class="node-grid">
        @for (node of currentNodes; track node.name) {
          <div class="node-card at-card" (click)="drillDown(node)">
            <div class="node-header">
              <mat-icon>{{ node.children || node.symbols ? 'folder' : 'insert_chart' }}</mat-icon>
              <span class="node-name">{{ node.name }}</span>
            </div>
            <div class="node-stats">
              <div class="node-stat">
                <span class="stat-value mono">{{ node.symbolCount }}</span>
                <span class="stat-label">symbols</span>
              </div>
              <div class="node-stat">
                <span class="stat-value mono">{{ node.tradeCount }}</span>
                <span class="stat-label">trades</span>
              </div>
              <div class="node-stat">
                <span class="stat-value mono" [class]="node.totalPnl >= 0 ? 'profit' : 'loss'">
                  {{ node.totalPnl >= 0 ? '+' : '' }}₹{{ node.totalPnl.toLocaleString('en-IN', {maximumFractionDigits: 0}) }}
                </span>
                <span class="stat-label">PnL</span>
              </div>
            </div>
          </div>
        }
      </div>
    }

    <!-- Symbol list (leaf level) -->
    @if (currentSymbols.length > 0) {
      <div class="symbol-list">
        <table class="at-table">
          <thead><tr>
            <th>Symbol</th>
            <th class="r">Trades</th>
            <th class="r">PnL</th>
            <th class="r">Win Rate</th>
          </tr></thead>
          <tbody>
            @for (sym of currentSymbols; track sym.symbol) {
              <tr class="clickable" (click)="openSymbol(sym.symbol)">
                <td class="mono link">{{ shortSymbol(sym.symbol) }}</td>
                <td class="r mono">{{ sym.tradeCount }}</td>
                <td class="r mono" [class]="sym.pnl >= 0 ? 'profit' : 'loss'">
                  {{ sym.pnl >= 0 ? '+' : '' }}₹{{ sym.pnl.toLocaleString('en-IN', {maximumFractionDigits: 0}) }}
                </td>
                <td class="r mono" [class]="sym.winRate >= 50 ? 'profit' : 'loss'">{{ sym.winRate.toFixed(0) }}%</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (currentNodes.length === 0 && currentSymbols.length === 0) {
      <div class="empty">No market data. Run trades or backtests to populate this view.</div>
    }
  `,
  styles: [`
    .page-header { margin-bottom: 12px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }

    .breadcrumb { display: flex; align-items: center; gap: 4px; margin-bottom: 16px; font-size: 13px; }
    .crumb { color: var(--accent); cursor: pointer; }
    .crumb:hover { text-decoration: underline; }
    .crumb.active { color: var(--text-primary); cursor: default; text-decoration: none; }
    .sep { color: var(--text-muted); }

    .node-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
    .node-card { padding: 14px; cursor: pointer; transition: border-color 0.2s; }
    .node-card:hover { border-color: var(--accent); }
    .node-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
    .node-header mat-icon { color: var(--accent); font-size: 20px; }
    .node-name { font-size: 14px; font-weight: 600; }
    .node-stats { display: flex; gap: 16px; }
    .node-stat { display: flex; flex-direction: column; gap: 1px; }
    .stat-value { font-size: 14px; font-weight: 600; }
    .stat-label { font-size: 10px; color: var(--text-muted); text-transform: uppercase; }

    .symbol-list { margin-top: 8px; }
    .at-table { width: 100%; border-collapse: collapse; font-size: 12px; }
    .at-table th { text-align: left; font-size: 10px; text-transform: uppercase; color: var(--text-secondary); padding: 6px 8px; border-bottom: 1px solid var(--border); }
    .at-table td { padding: 6px 8px; border-bottom: 1px solid var(--bg-hover); }
    .at-table tr.clickable { cursor: pointer; }
    .at-table tr:hover td { background: var(--bg-hover); }
    .r { text-align: right; }
    .link { color: var(--accent); }
    .empty { color: var(--text-muted); font-size: 13px; padding: 40px 16px; text-align: center; }
  `],
})
export class MarketComponent implements OnInit, OnDestroy {
  private allTrades: TradeRecord[] = [];
  private tree: MarketNode[] = [];
  breadcrumb: string[] = [];
  currentNodes: MarketNode[] = [];
  currentSymbols: { symbol: string; tradeCount: number; pnl: number; winRate: number }[] = [];

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.api.getTrades().pipe(
      catchError(() => of([])),
      takeUntil(this.destroy$),
    ).subscribe(trades => {
      this.allTrades = trades;
      this.buildTree();
      this.currentNodes = this.tree;
    });
  }

  private buildTree(): void {
    // Group: exchange → segment → symbol type
    const exchangeMap = new Map<string, Map<string, Map<string, TradeRecord[]>>>();

    for (const t of this.allTrades) {
      const parts = t.symbol.split(':');
      const exchange = parts[0] || 'UNKNOWN';
      // Infer segment from symbol suffix
      const ticker = parts[1] || t.symbol;
      let segment = 'CM';
      if (ticker.includes('-FUT')) segment = 'FO';
      else if (ticker.includes('-OPT') || ticker.match(/\d{2}(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\d{2}/)) segment = 'FO';
      else if (ticker.endsWith('-EQ') || ticker.endsWith('-BE')) segment = 'CM';
      else if (ticker.includes('-IX')) segment = 'IX';

      const type = segment === 'FO' ? (ticker.includes('FUT') ? 'Futures' : 'Options') : 'Equity';

      if (!exchangeMap.has(exchange)) exchangeMap.set(exchange, new Map());
      const segMap = exchangeMap.get(exchange)!;
      if (!segMap.has(segment)) segMap.set(segment, new Map());
      const typeMap = segMap.get(segment)!;
      if (!typeMap.has(type)) typeMap.set(type, []);
      typeMap.get(type)!.push(t);
    }

    this.tree = Array.from(exchangeMap.entries()).map(([exchange, segMap]) => {
      const segChildren = Array.from(segMap.entries()).map(([segment, typeMap]) => {
        const typeChildren = Array.from(typeMap.entries()).map(([type, trades]) => {
          const symbols = [...new Set(trades.map(t => t.symbol))];
          return {
            name: type,
            ...this.computeStats(trades),
            symbolCount: symbols.length,
            symbols,
          } as MarketNode;
        });
        const allTrades = Array.from(typeMap.values()).flat();
        return {
          name: segment,
          ...this.computeStats(allTrades),
          symbolCount: new Set(allTrades.map(t => t.symbol)).size,
          children: typeChildren,
        } as MarketNode;
      });
      const allExTrades = Array.from(segMap.values()).flatMap(m => Array.from(m.values()).flat());
      return {
        name: exchange,
        ...this.computeStats(allExTrades),
        symbolCount: new Set(allExTrades.map(t => t.symbol)).size,
        children: segChildren,
      } as MarketNode;
    }).sort((a, b) => b.tradeCount - a.tradeCount);
  }

  private computeStats(trades: TradeRecord[]): { tradeCount: number; totalPnl: number } {
    return {
      tradeCount: trades.length,
      totalPnl: trades.reduce((s, t) => s + (t.pnl ?? 0), 0),
    };
  }

  drillDown(node: MarketNode): void {
    this.breadcrumb.push(node.name);
    if (node.children && node.children.length > 0) {
      this.currentNodes = node.children;
      this.currentSymbols = [];
    } else if (node.symbols) {
      this.currentNodes = [];
      this.currentSymbols = this.symbolStats(node.symbols);
    }
  }

  navigateTo(level: number): void {
    if (level === 0) {
      this.breadcrumb = [];
      this.currentNodes = this.tree;
      this.currentSymbols = [];
      return;
    }
    this.breadcrumb = this.breadcrumb.slice(0, level);
    let nodes = this.tree;
    for (const name of this.breadcrumb) {
      const found = nodes.find(n => n.name === name);
      if (found?.children) { nodes = found.children; }
      else if (found?.symbols) { this.currentNodes = []; this.currentSymbols = this.symbolStats(found.symbols); return; }
      else break;
    }
    this.currentNodes = nodes;
    this.currentSymbols = [];
  }

  private symbolStats(symbols: string[]): { symbol: string; tradeCount: number; pnl: number; winRate: number }[] {
    return symbols.map(symbol => {
      const trades = this.allTrades.filter(t => t.symbol === symbol);
      const closed = trades.filter(t => t.pnl != null);
      const wins = closed.filter(t => t.pnl! > 0).length;
      return {
        symbol,
        tradeCount: trades.length,
        pnl: closed.reduce((s, t) => s + t.pnl!, 0),
        winRate: closed.length > 0 ? (wins / closed.length) * 100 : 0,
      };
    }).sort((a, b) => b.pnl - a.pnl);
  }

  openSymbol(symbol: string): void { this.router.navigate(['/symbols', symbol]); }
  shortSymbol(s: string): string { return s.replace(/^(NSE|BSE|MCX):/, ''); }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
