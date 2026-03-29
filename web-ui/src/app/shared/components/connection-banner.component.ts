import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { ConnectionService } from '../../core/services/connection.service';
import { DxButtonModule } from 'devextreme-angular/ui/button';

@Component({
  selector: 'at-connection-banner',
  standalone: true,
  imports: [CommonModule, MatIconModule, DxButtonModule],
  template: `
    @if (disconnected && !connection.isMock()) {
      <div class="connection-banner">
        <mat-icon>cloud_off</mat-icon>
        <span class="msg">Backend unreachable — check your connection or engine status.</span>
        <dx-button
          text="Switch to Demo Mode"
          type="default"
          stylingMode="outlined"
          (onClick)="switchToMock()">
        </dx-button>
      </div>
    }
  `,
  styles: [`
    .connection-banner {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 16px;
      background: rgba(210, 153, 34, 0.12);
      border: 1px solid var(--warning);
      border-radius: 6px;
      color: var(--warning);
      font-size: 13px;
      margin-bottom: 12px;
    }
    .msg { flex: 1; }
    mat-icon { font-size: 18px; width: 18px; height: 18px; }
    ::ng-deep .connection-banner .dx-button { height: 28px; font-size: 11px; }
  `],
})
export class ConnectionBannerComponent {
  @Input() disconnected = false;
  connection = inject(ConnectionService);

  switchToMock() {
    this.connection.setMode('mock');
  }
}
