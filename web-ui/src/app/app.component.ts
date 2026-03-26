import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './layout/sidebar.component';
import { StatusBarComponent } from './layout/status-bar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, StatusBarComponent],
  template: `
    <div class="app-shell">
      <at-status-bar />
      <div class="app-body">
        <at-sidebar />
        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: [`
    .app-shell {
      display: flex;
      flex-direction: column;
      height: 100vh;
    }
    .app-body {
      display: flex;
      flex: 1;
      overflow: hidden;
    }
    .content {
      flex: 1;
      padding: 24px;
      overflow-y: auto;
      background: var(--bg-primary);
    }
  `],
})
export class AppComponent {}
