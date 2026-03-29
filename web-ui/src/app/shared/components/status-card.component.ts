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
      <div class="card-content">
        <ng-content />
      </div>
    </div>
  `,
  styles: [`
    .at-card {
      background: var(--bg-card);
      border: 1px solid var(--border);
      border-radius: 6px;
      padding: 16px;
      height: 100%;
      box-sizing: border-box;
    }
    .card-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;
    }
    .card-title {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-secondary);
      text-transform: uppercase;
      letter-spacing: 1px;
    }
    mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
    .card-content {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  `],
})
export class StatusCardComponent {
  @Input() title = '';
  @Input() icon = 'info';
  @Input() iconColor = 'var(--text-secondary)';
}
