import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
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
    MatSidenavModule,
    SidebarComponent,
    StatusBarComponent,
    ToastContainerComponent,
    ConnectionBannerComponent
  ],
  template: `
    <div class="app-container">
      <at-status-bar />
      <mat-sidenav-container class="sidenav-container">
        <mat-sidenav mode="side" opened class="app-sidebar">
          <at-sidebar />
        </mat-sidenav>
        <mat-sidenav-content class="app-content">
          <at-connection-banner [disconnected]="state.isStale()" />
          <main class="main-outlet">
            <router-outlet />
          </main>
        </mat-sidenav-content>
      </mat-sidenav-container>
    </div>
    <at-toast-container />
  `,
  styles: [`
    .app-container {
      display: flex;
      flex-direction: column;
      height: 100vh;
    }
    .sidenav-container {
      flex: 1;
    }
    .app-sidebar {
      width: 200px;
      border-right: 1px solid var(--border);
      background: var(--bg-secondary);
    }
    .app-content {
      background: var(--bg-primary);
      display: flex;
      flex-direction: column;
    }
    .main-outlet {
      padding: 24px;
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
