import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { TransactionsComponent } from './pages/transactions/transactions.component';
import { PositionsComponent } from './pages/positions/positions.component';
import { PaperTradeComponent } from './pages/paper-trade/paper-trade.component';
import { ControlsComponent } from './pages/controls/controls.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'transactions', component: TransactionsComponent },
  { path: 'paper-trade', component: PaperTradeComponent },
  { path: 'positions', component: PositionsComponent },
  { path: 'controls', component: ControlsComponent },
  { path: '**', redirectTo: '' },
];
