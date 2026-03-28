import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ConnectionService } from '../../core/services/connection.service';

@Component({
  selector: 'at-connection-banner',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    @if (disconnected && !connection.isMock()) {
      <div class="connection-banner">
        <mat-icon>cloud_off</mat-icon>
        <span class="msg">Backend unreachable — check your connection or engine status.</span>
        <button mat-stroked-button color="accent" class="btn-sm" (click)="switchToMock()">
          Switch to Demo Mode
        </button>
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
      animation: pulse-warn 4s infinite;
    }
    .msg { flex: 1; }
    .btn-sm { height: 28px; line-height: 28px; padding: 0 12px; font-size: 11px; }
    mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `],
})
export class ConnectionBannerComponent {
  @Input() disconnected = false;
  connection = inject(ConnectionService);

  switchToMock() {
    this.connection.setMode('mock');
  }
}
