import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { TransactionsComponent } from './pages/transactions/transactions.component';
import { PositionsComponent } from './pages/positions/positions.component';
import { PaperTradeComponent } from './pages/paper-trade/paper-trade.component';
import { ControlsComponent } from './pages/controls/controls.component';
import { StrategiesComponent } from './pages/strategies/strategies.component';
import { SymbolsComponent } from './pages/symbols/symbols.component';
import { MarketComponent } from './pages/market/market.component';
import { BacktestComponent } from './pages/backtest/backtest.component';
import { ConfigComponent } from './pages/config/config.component';
import { KnowledgeComponent } from './pages/knowledge/knowledge.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'transactions', component: TransactionsComponent },
  { path: 'paper-trade', component: PaperTradeComponent },
  { path: 'positions', component: PositionsComponent },
  { path: 'strategies', component: StrategiesComponent },
  { path: 'symbols', component: SymbolsComponent },
  { path: 'symbols/:symbol', component: SymbolsComponent },
  { path: 'market', component: MarketComponent },
  { path: 'backtest', component: BacktestComponent },
  { path: 'config', component: ConfigComponent },
  { path: 'knowledge', component: KnowledgeComponent },
  { path: 'controls', component: ControlsComponent },
  { path: '**', redirectTo: '' },
];
