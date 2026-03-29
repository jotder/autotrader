import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'at-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule],
  template: `
    <nav class="sidebar">
      <div class="logo">
        <span class="logo-text">AutoTrader</span>
        <span class="logo-sub">Control Center</span>
      </div>
      <div class="nav-list">
        @for (item of navItems; track item.path) {
          <a class="nav-item"
             [routerLink]="item.path"
             routerLinkActive="active"
             [routerLinkActiveOptions]="{ exact: item.path === '/' }">
            <mat-icon>{{ item.icon }}</mat-icon>
            <span class="nav-label">{{ item.label }}</span>
          </a>
        }
      </div>
    </nav>
  `,
  styles: [`
    .sidebar {
      width: 200px;
      height: 100%;
      display: flex;
      flex-direction: column;
    }
    .logo {
      padding: 16px;
      display: flex;
      flex-direction: column;
      margin-bottom: 8px;
    }
    .logo-text {
      font-size: 18px;
      font-weight: 600;
    }
    .logo-sub {
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 1px;
      opacity: 0.6;
    }
    .nav-list {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 16px;
      text-decoration: none;
      color: inherit;
      font-size: 13px;
      transition: all 0.2s;
      border-left: 3px solid transparent;
    }
    .nav-item:hover {
      background: rgba(255,255,255,0.05);
    }
    .nav-item.active {
      background: rgba(255,255,255,0.1);
      border-left-color: #00bcd4;
    }
    mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      opacity: 0.7;
    }
  `],
})
export class SidebarComponent {
  navItems = [
    { path: '/', icon: 'dashboard', label: 'Dashboard' },
    { path: '/transactions', icon: 'receipt_long', label: 'Transactions' },
    { path: '/paper-trade', icon: 'show_chart', label: 'Paper Trade' },
    { path: '/positions', icon: 'account_balance_wallet', label: 'Positions' },
    { path: '/signals', icon: 'notifications', label: 'Alpha Signals' },
    { path: '/strategies', icon: 'tune', label: 'Strategies' },
    { path: '/symbols', icon: 'token', label: 'Symbols' },
    { path: '/market', icon: 'store', label: 'Market' },
    { path: '/backtest', icon: 'science', label: 'Backtest' },
    { path: '/go-live', icon: 'rocket_launch', label: 'Go-Live' },
    { path: '/config', icon: 'settings', label: 'Config' },

    { path: '/knowledge', icon: 'menu_book', label: 'Knowledge' },
    { path: '/controls', icon: 'security', label: 'Controls' },
  ];
}
