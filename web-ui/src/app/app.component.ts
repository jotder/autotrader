import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './layout/sidebar.component';
import { StatusBarComponent } from './layout/status-bar.component';
import { ToastContainerComponent } from './shared/components/toast.component';
import { ConnectionBannerComponent } from './shared/components/connection-banner.component';
import { DataFeedService } from './core/services/data-feed.service';
import { GlobalStateService } from './core/services/global-state.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    SidebarComponent,
    StatusBarComponent,
    ToastContainerComponent,
    ConnectionBannerComponent
  ],
  template: `
    <div class="app-container">
      <at-status-bar />
      <div class="sidenav-container">
        <aside class="app-sidebar">
          <at-sidebar />
        </aside>
        <div class="app-content">
          <at-connection-banner [disconnected]="state.isStale()" />
          <main class="main-outlet">
            <router-outlet />
          </main>
        </div>
      </div>
    </div>
    <at-toast-container />
  `,
  styles: [`
    .app-container {
      display: flex;
      flex-direction: column;
      height: 100vh;
      overflow: hidden;
    }
    .sidenav-container {
      flex: 1;
      display: flex;
      flex-direction: row;
      overflow: hidden;
    }
    .app-sidebar {
      width: 200px;
      flex-shrink: 0;
      z-index: 10;
      border-right: 1px solid rgba(255,255,255,0.1);
    }
    .app-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .main-outlet {
      padding: 20px;
      flex: 1;
      overflow-y: auto;
    }
  `],
})
export class AppComponent implements OnInit {
  constructor(
    private dataFeed: DataFeedService,
    public state: GlobalStateService
  ) {}

  ngOnInit() {
    this.dataFeed.startFeeds();
  }
}
