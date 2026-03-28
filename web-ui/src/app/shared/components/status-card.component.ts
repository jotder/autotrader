import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'at-status-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  template: `
    <mat-card class="status-card">
      <mat-card-header>
        <mat-icon mat-card-avatar [style.color]="iconColor">{{ icon }}</mat-icon>
        <mat-card-title>{{ title }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <div class="card-body">
          <ng-content />
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .status-card {
      height: 100%;
    }
    ::ng-deep .mat-mdc-card-header {
      padding: 12px 16px 8px !important;
    }
    ::ng-deep .mat-mdc-card-header-text {
      margin: 0 !important;
    }
    mat-card-title {
      font-size: 11px !important;
      text-transform: uppercase;
      letter-spacing: 1px;
      color: var(--text-secondary);
      font-weight: 600;
      margin-top: 4px;
    }
    .card-body {
      padding-top: 8px;
    }
    mat-icon[mat-card-avatar] {
      margin-right: 8px !important;
      font-size: 20px;
      width: 20px;
      height: 20px;
    }
  `],
})
export class StatusCardComponent {
  @Input() title = '';
  @Input() icon = 'info';
  @Input() iconColor = 'var(--text-secondary)';
}
