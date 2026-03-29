import { Routes } from '@angular/router';
export const routes: Routes = [
  { 
    path: '', 
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent) 
  },
  {
    path: 'transactions',
    loadComponent: () => import('./pages/transactions/transactions.component').then(m => m.TransactionsComponent)
  },
  {
    path: 'paper-trade',
    loadComponent: () => import('./pages/paper-trade/paper-trade.component').then(m => m.PaperTradeComponent)
  },
  { path: 'positions', loadComponent: () => import('./pages/positions/positions.component').then(m => m.PositionsComponent) },
  { path: 'signals', loadComponent: () => import('./pages/signals/signals.component').then(m => m.SignalsComponent) },
  { path: 'strategies', loadComponent: () => import('./pages/strategies/strategies.component').then(m => m.StrategiesComponent) },

  {
    path: 'symbols',
    loadComponent: () => import('./pages/symbols/symbols.component').then(m => m.SymbolsComponent)
  },
  {
    path: 'symbols/:symbol',
    loadComponent: () => import('./pages/symbols/symbols.component').then(m => m.SymbolsComponent)
  },
  {
    path: 'market',
    loadComponent: () => import('./pages/market/market.component').then(m => m.MarketComponent)
  },
  {
    path: 'backtest',
    loadComponent: () => import('./pages/backtest/backtest.component').then(m => m.BacktestComponent)
  },
  {
    path: 'backtest/iterative',
    loadComponent: () => import('./pages/backtest/iterative/iterative-backtest.component').then(m => m.IterativeBacktestComponent)
  },
  {
    path: 'go-live',
    loadComponent: () => import('./pages/go-live/go-live.component').then(m => m.GoLiveGateComponent)
  },
  {
    path: 'config',
    loadComponent: () => import('./pages/config/config.component').then(m => m.ConfigComponent)
  },
  {
    path: 'knowledge',
    loadComponent: () => import('./pages/knowledge/knowledge.component').then(m => m.KnowledgeComponent)
  },
  {
    path: 'controls',
    loadComponent: () => import('./pages/controls/controls.component').then(m => m.ControlsComponent)
  },
  { path: '**', redirectTo: '' },
];
