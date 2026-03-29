import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../core/services/api.service';
import { BacktestReport, BacktestJobRequest } from '../../../core/models/api.models';

@Component({
  selector: 'app-iterative-backtest',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <header class="page-header">
        <h1>Iterative Backtest & Tuning</h1>
      </header>

      <div class="grid-2">
        <div class="card">
          <div class="card-header"><span class="card-title">Parameter Sweep Config</span></div>
          <div class="card-body">
            <div class="form-group">
              <label>Symbol</label>
              <input type="text" [(ngModel)]="request.symbol" class="form-control">
            </div>
            <div class="grid-2">
              <div class="form-group">
                <label>From</label>
                <input type="date" [(ngModel)]="request.from" class="form-control">
              </div>
              <div class="form-group">
                <label>To</label>
                <input type="date" [(ngModel)]="request.to" class="form-control">
              </div>
            </div>
            
            <div class="divider"></div>
            
            <div class="form-group">
              <label>SL ATR Multipliers (comma separated)</label>
              <input type="text" [(ngModel)]="slSweeps" class="form-control" placeholder="1.5, 2.0, 2.5">
            </div>
            <div class="form-group">
              <label>TP R-Multiples (comma separated)</label>
              <input type="text" [(ngModel)]="tpSweeps" class="form-control" placeholder="2.0, 3.0, 4.0">
            </div>

            <button class="btn-primary w-100 mt-16" 
                    [disabled]="loading()" (click)="runJob()">
              {{ loading() ? 'Running Sweep...' : 'Start Iterative Run' }}
            </button>
          </div>
        </div>

        <div class="card" *ngIf="results().length > 0">
          <div class="card-header"><span class="card-title">Comparative Analysis</span></div>
          <div class="table-container">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Strategy / Params</th>
                  <th>Trades</th>
                  <th>Win%</th>
                  <th>PF</th>
                  <th>Net PnL</th>
                  <th>Max DD</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let r of results(); let i = index" 
                    [class.best-performer]="isBest(r)">
                  <td>
                    <div class="bold">{{ getTunedParams(r) }}</div>
                  </td>
                  <td>{{ r.totalTrades }}</td>
                  <td>{{ r.overall.winRate | percent }}</td>
                  <td [class.text-success]="r.overall.profitFactor > 1.5">
                    {{ r.overall.profitFactor | number:'1.2-2' }}
                  </td>
                  <td [class.text-success]="r.overall.totalPnl > 0" 
                      [class.text-danger]="r.overall.totalPnl < 0">
                    ₹{{ r.overall.totalPnl | number:'1.0-0' }}
                  </td>
                  <td class="text-danger">{{ r.overall.maxDrawdown | percent }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .best-performer {
      background: rgba(0, 188, 212, 0.1);
      border-left: 4px solid var(--accent-blue);
    }
    .divider { margin: 16px 0; border-top: 1px solid var(--border-color); }
    .mt-16 { margin-top: 16px; }
    .w-100 { width: 100%; }
  `]
})
export class IterativeBacktestComponent {
  private api = inject(ApiService);

  loading = signal(false);
  results = signal<BacktestReport[]>([]);
  
  request: BacktestJobRequest = {
    symbol: 'NSE:SBIN-EQ',
    from: '2026-03-01',
    to: '2026-03-25',
    sweeps: {}
  };

  slSweeps = '1.5, 2.0, 2.5';
  tpSweeps = '2.0, 3.0, 4.0';

  runJob() {
    this.loading.set(true);
    this.results.set([]);

    const payload: BacktestJobRequest = {
      ...this.request,
      sweeps: {
        slAtrMultiplier: this.slSweeps.split(',').map(s => parseFloat(s.trim())),
        tpRMultiple: this.tpSweeps.split(',').map(s => parseFloat(s.trim()))
      }
    };

    this.api.createBacktestJob(payload).subscribe(resp => {
      this.pollResults(resp.jobId);
    });
  }

  pollResults(jobId: string) {
    this.api.getBacktestJobResults(jobId).subscribe(data => {
      if (data && data.length > 0) {
        this.results.set(data);
        this.loading.set(false);
      } else {
        setTimeout(() => this.pollResults(jobId), 2000);
      }
    });
  }

  getTunedParams(report: BacktestReport): string {
    // Strategy names in tuned runs look like "Tuned [SL=2.0, TP=3.0]"
    const stratId = Object.keys(report.byStrategy)[0] || 'Unknown';
    return stratId.replace('tuned_tf_', ''); 
  }

  isBest(report: BacktestReport): boolean {
    const maxPnl = Math.max(...this.results().map(r => r.overall.totalPnl));
    return report.overall.totalPnl === maxPnl && maxPnl > 0;
  }
}
