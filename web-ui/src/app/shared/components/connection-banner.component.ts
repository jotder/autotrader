import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'at-connection-banner',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    @if (disconnected) {
      <div class="connection-banner">
        <mat-icon>cloud_off</mat-icon>
        <span>Backend unreachable — retrying…</span>
      </div>
    }
  `,
  styles: [`
    .connection-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      background: rgba(210, 153, 34, 0.12);
      border: 1px solid var(--warning);
      border-radius: 6px;
      color: var(--warning);
      font-size: 13px;
      margin-bottom: 12px;
      animation: pulse-warn 2s infinite;
    }
    @keyframes pulse-warn {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.7; }
    }
    mat-icon { font-size: 18px; }
  `],
})
export class ConnectionBannerComponent {
  @Input() disconnected = false;
}
