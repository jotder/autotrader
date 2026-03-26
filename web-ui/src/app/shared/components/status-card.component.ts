import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'at-status-card',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  template: `
    <div class="at-card status-card">
      <div class="card-header">
        <mat-icon [style.color]="iconColor">{{ icon }}</mat-icon>
        <span class="card-title">{{ title }}</span>
      </div>
      <div class="card-body">
        <ng-content />
      </div>
    </div>
  `,
  styles: [`
    .status-card {
      min-height: 120px;
    }
    .card-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--border);
    }
    .card-title {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-secondary);
      font-weight: 500;
    }
    .card-body {
      font-size: 13px;
    }
  `],
})
export class StatusCardComponent {
  @Input() title = '';
  @Input() icon = 'info';
  @Input() iconColor = 'var(--text-secondary)';
}
