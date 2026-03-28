import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'at-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule],
  template: `
    <nav class="sidebar">
      <div class="logo">
        <span class="logo-text">AutoTrader</span>
        <span class="logo-sub">Control Center</span>
      </div>
      <mat-nav-list>
        @for (item of navItems; track item.path) {
          <a mat-list-item
             [routerLink]="item.path"
             routerLinkActive="active"
             [routerLinkActiveOptions]="{ exact: item.path === '/' }">
            <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
            <span matListItemTitle>{{ item.label }}</span>
          </a>
        }
      </mat-nav-list>
    </nav>
  `,
  styles: [`
    .sidebar {
      width: 200px;
      height: 100%;
      background: var(--bg-secondary);
      border-right: 1px solid var(--border);
      display: flex;
      flex-direction: column;
    }
    .logo {
      padding: 16px;
      border-bottom: 1px solid var(--border);
      display: flex;
      flex-direction: column;
    }
    .logo-text {
      font-size: 18px;
      font-weight: 600;
      color: var(--accent);
    }
    .logo-sub {
      font-size: 11px;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 1px;
    }
    a.active {
      background: var(--bg-hover) !important;
      border-left: 3px solid var(--accent);
    }
    mat-icon { color: var(--text-secondary); }
    a.active mat-icon { color: var(--accent); }
  `],
})
export class SidebarComponent {
  navItems = [
    { path: '/', icon: 'dashboard', label: 'Dashboard' },
    { path: '/transactions', icon: 'receipt_long', label: 'Transactions' },
    { path: '/paper-trade', icon: 'show_chart', label: 'Paper Trade' },
    { path: '/positions', icon: 'account_balance_wallet', label: 'Positions' },
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
