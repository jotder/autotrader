import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'at-metric',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="metric-row">
      <span class="metric-label">{{ label }}</span>
      <span class="metric-value" [class]="valueClass" [class.mono]="mono">{{ value }}</span>
    </div>
  `,
  styles: [`
    .metric-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 3px 0;
    }
    .metric-label {
      color: var(--text-secondary);
      font-size: 12px;
    }
    .metric-value {
      color: var(--text-primary);
      font-size: 13px;
      font-weight: 500;
    }
  `],
})
export class MetricRowComponent {
  @Input() label = '';
  @Input() value: string | number = '—';
  @Input() valueClass = '';
  @Input() mono = true;
}
