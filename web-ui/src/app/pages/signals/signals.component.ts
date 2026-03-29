import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { RecommendationSignal } from '../../core/models/api.models';
import { PollingService } from '../../core/services/polling.service';

@Component({
  selector: 'app-signals',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page-container">
      <header class="page-header">
        <h1>Alpha Signals</h1>
        <div class="header-actions">
          <button class="btn-secondary" (click)="loadSignals()">Refresh</button>
        </div>
      </header>

      <div class="card">
        <div class="card-header">
          <span class="card-title">Real-time Recommendation Stream</span>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Symbol</th>
                <th>Strategy</th>
                <th>Direction</th>
                <th>Confidence</th>
                <th>Entry</th>
                <th>SL</th>
                <th>TP</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let s of signals()">
                <td>{{ s.timestamp | date:'shortTime' }}</td>
                <td class="bold">{{ s.symbol }}</td>
                <td>{{ s.strategyId }}</td>
                <td>
                  <span [class]="'pill ' + s.direction.toLowerCase()">
                    {{ s.direction }}
                  </span>
                </td>
                <td>
                  <span [class]="'confidence-' + s.confidenceLevel.toLowerCase()">
                    {{ s.confidenceLevel }} ({{ s.confidence | percent }})
                  </span>
                </td>
                <td>{{ s.suggestedEntry | number:'1.2-2' }}</td>
                <td class="text-danger">{{ s.suggestedStopLoss | number:'1.2-2' }}</td>
                <td class="text-success">{{ s.suggestedTarget | number:'1.2-2' }}</td>
                <td class="reason-cell" [title]="s.reason">{{ s.reason }}</td>
              </tr>
              <tr *ngIf="signals().length === 0">
                <td colspan="9" class="text-center">No signals generated yet this session.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .reason-cell {
      max-width: 200px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .confidence-normal { color: var(--text-muted); }
    .confidence-high { color: var(--accent-blue); font-weight: bold; }
    .confidence-very_high { color: var(--accent-purple); font-weight: bold; }
    
    .pill.buy { background: rgba(0, 200, 81, 0.1); color: #00c851; border: 1px solid #00c851; }
    .pill.sell { background: rgba(255, 68, 68, 0.1); color: #ff4444; border: 1px solid #ff4444; }
  `]
})
export class SignalsComponent implements OnInit {
  private api = inject(ApiService);
  private polling = inject(PollingService);
  
  signals = signal<RecommendationSignal[]>([]);

  ngOnInit() {
    this.polling.poll(() => this.api.getSignals(), 10000)
      .subscribe(data => {
        this.signals.set(data);
      });
  }

  loadSignals() {
    this.api.getSignals().subscribe(data => {
      this.signals.set(data);
    });
  }
}
