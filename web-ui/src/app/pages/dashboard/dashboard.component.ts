import { Component } from '@angular/core';

@Component({
  selector: 'at-dashboard',
  standalone: true,
  template: `
    <div class="page-header">
      <h1>Dashboard</h1>
      <p class="text-secondary">System overview — alerts, risk, and health</p>
    </div>
    <div class="placeholder">
      <p class="text-muted">Dashboard cards will be built in Milestone 2</p>
    </div>
  `,
  styles: [`
    .page-header {
      margin-bottom: 24px;
    }
    h1 {
      margin: 0;
      font-size: 20px;
      font-weight: 500;
    }
    .placeholder {
      padding: 40px;
      text-align: center;
      border: 1px dashed var(--border);
      border-radius: 8px;
    }
  `],
})
export class DashboardComponent {}
