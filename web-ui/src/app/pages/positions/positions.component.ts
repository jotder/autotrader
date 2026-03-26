import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil, interval, switchMap, startWith, catchError, of, forkJoin } from 'rxjs';
import { ApiService } from '../../core/services/api.service';
import { Position, OrdersResponse, ManagedOrder } from '../../core/models/api.models';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'at-positions',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusCardComponent],
  template: `
    <div class="page-header">
      <h1>Positions</h1>
      <button class="btn-danger" (click)="flatten()" [disabled]="flattenPending">
        Emergency Flatten
      </button>
    </div>

    <!-- Open Positions -->
    <at-status-card title="Open Positions" icon="trending_up"
                    [iconColor]="positions.length > 0 ? 'var(--accent)' : 'var(--text-secondary)'">
      @if (positions.length === 0) {
        <div class="empty">No open positions</div>
      } @else {
        <div class="table-wrap">
          <table class="at-table">
            <thead>
              <tr>
                <th>Symbol</th>
                <th>Dir</th>
                <th class="r">Qty</th>
                <th class="r">Entry</th>
                <th class="r">SL</th>
                <th class="r">TP</th>
                <th class="r">PnL</th>
                <th>Trail</th>
                <th>Since</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (p of positions; track p.correlationId) {
                <tr>
                  <td class="mono">{{ p.symbol }}</td>
                  <td><span class="badge" [class]="p.direction === 'BUY' ? 'buy' : 'sell'">{{ p.direction }}</span></td>
                  <td class="r mono">{{ p.quantity }}</td>
                  <td class="r mono">{{ p.entryPrice | number:'1.2-2' }}</td>
                  <td class="r mono">{{ p.currentStopLoss | number:'1.2-2' }}</td>
                  <td class="r mono">{{ p.takeProfit | number:'1.2-2' }}</td>
                  <td class="r mono" [class]="pnlClass(p.unrealizedPnl)">{{ formatPnl(p.unrealizedPnl) }}</td>
                  <td>{{ p.trailingActivated ? 'Active' : '—' }}</td>
                  <td class="mono text-muted">{{ formatTime(p.entryTime) }}</td>
                  <td>
                    <button class="btn-sm btn-exit" (click)="exitPosition(p)" [disabled]="exitPending[p.correlationId]">
                      Exit
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </at-status-card>

    <!-- Active Orders -->
    <at-status-card title="Active Orders" icon="receipt_long" iconColor="var(--info)"
                    style="margin-top: 16px">
      @if (activeOrders.length === 0) {
        <div class="empty">No active orders</div>
      } @else {
        <div class="table-wrap">
          <table class="at-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Symbol</th>
                <th>Side</th>
                <th>State</th>
                <th class="r">Qty</th>
                <th>Submitted</th>
              </tr>
            </thead>
            <tbody>
              @for (o of activeOrders; track o.clientOrderId) {
                <tr>
                  <td class="mono text-muted">{{ o.clientOrderId | slice:0:16 }}…</td>
                  <td class="mono">{{ o.symbol }}</td>
                  <td>{{ o.side }}</td>
                  <td><span class="badge state">{{ o.state }}</span></td>
                  <td class="r mono">{{ o.quantity }}</td>
                  <td class="mono text-muted">{{ formatTime(o.submittedAt) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </at-status-card>

    <!-- Confirmation dialog -->
    @if (confirmAction) {
      <div class="overlay" (click)="confirmAction = null">
        <div class="confirm-dialog" (click)="$event.stopPropagation()">
          <h3>{{ confirmAction.title }}</h3>
          <p>{{ confirmAction.message }}</p>
          @if (confirmAction.requireType) {
            <input class="confirm-input" [(ngModel)]="confirmTyped"
                   [placeholder]="'Type ' + confirmAction.requireType + ' to confirm'" />
          }
          <div class="confirm-actions">
            <button class="btn-secondary" (click)="confirmAction = null">Cancel</button>
            <button class="btn-danger" (click)="executeConfirmed()"
                    [disabled]="confirmAction.requireType && confirmTyped !== confirmAction.requireType">
              {{ confirmAction.confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    .table-wrap { overflow-x: auto; }
    .at-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .at-table th { text-align: left; font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px;
      color: var(--text-secondary); padding: 6px 8px; border-bottom: 1px solid var(--border); }
    .at-table td { padding: 6px 8px; border-bottom: 1px solid var(--bg-hover); }
    .at-table tr:hover td { background: var(--bg-hover); }
    .r { text-align: right; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 11px; font-weight: 600; }
    .badge.buy { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .badge.sell { background: rgba(248, 81, 73, 0.15); color: var(--loss); }
    .badge.state { background: rgba(0, 188, 212, 0.15); color: var(--accent); }
    .btn-danger { background: var(--loss); color: #fff; border: none; padding: 6px 16px; border-radius: 4px; cursor: pointer; font-size: 13px; }
    .btn-danger:hover { opacity: 0.85; }
    .btn-danger:disabled { opacity: 0.4; cursor: not-allowed; }
    .btn-sm { padding: 3px 10px; font-size: 12px; border-radius: 3px; border: 1px solid var(--border); background: transparent; color: var(--text-primary); cursor: pointer; }
    .btn-sm:hover { background: var(--bg-hover); }
    .btn-exit { border-color: var(--loss); color: var(--loss); }
    .empty { color: var(--text-muted); font-size: 13px; padding: 12px 0; }
    .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 24px; max-width: 420px; width: 90%; }
    .confirm-dialog h3 { margin: 0 0 8px; font-size: 16px; }
    .confirm-dialog p { color: var(--text-secondary); font-size: 13px; margin: 0 0 16px; }
    .confirm-input { width: 100%; padding: 8px; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 4px; color: var(--text-primary); font-family: var(--font-mono); margin-bottom: 16px; }
    .confirm-actions { display: flex; gap: 8px; justify-content: flex-end; }
    .btn-secondary { background: transparent; border: 1px solid var(--border); color: var(--text-primary); padding: 6px 16px; border-radius: 4px; cursor: pointer; font-size: 13px; }
  `],
})
export class PositionsComponent implements OnInit, OnDestroy {
  positions: Position[] = [];
  activeOrders: ManagedOrder[] = [];
  exitPending: Record<string, boolean> = {};
  flattenPending = false;
  confirmAction: { title: string; message: string; requireType?: string; confirmLabel: string; onConfirm: () => void } | null = null;
  confirmTyped = '';

  private destroy$ = new Subject<void>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    interval(environment.pollingIntervalMs).pipe(
      startWith(0),
      switchMap(() => forkJoin({
        positions: this.api.getPositions().pipe(catchError(() => of([]))),
        orders: this.api.getOrders().pipe(catchError(() => of({ activeCount: 0, active: [], completed: [] }))),
      })),
      takeUntil(this.destroy$),
    ).subscribe(data => {
      this.positions = data.positions as Position[];
      this.activeOrders = (data.orders as OrdersResponse).active || [];
    });
  }

  exitPosition(p: Position): void {
    this.confirmAction = {
      title: 'Exit Position',
      message: `Close ${p.direction} ${p.quantity} × ${p.symbol} (${p.correlationId.slice(0, 8)}…)?`,
      confirmLabel: 'Exit',
      onConfirm: () => {
        this.exitPending[p.correlationId] = true;
        this.api.exitPosition(p.correlationId).subscribe({
          next: () => { this.exitPending[p.correlationId] = false; },
          error: () => { this.exitPending[p.correlationId] = false; },
        });
      },
    };
  }

  flatten(): void {
    this.confirmAction = {
      title: 'Emergency Flatten',
      message: 'Close ALL positions immediately. This cannot be undone.',
      requireType: environment.confirmFlattenWord,
      confirmLabel: 'Flatten All',
      onConfirm: () => {
        this.flattenPending = true;
        this.api.emergencyFlatten().subscribe({
          next: () => { this.flattenPending = false; },
          error: () => { this.flattenPending = false; },
        });
      },
    };
  }

  executeConfirmed(): void {
    if (this.confirmAction) {
      this.confirmAction.onConfirm();
      this.confirmAction = null;
      this.confirmTyped = '';
    }
  }

  pnlClass(v: number | undefined): string { return v == null ? '' : v >= 0 ? 'profit' : 'loss'; }
  formatPnl(v: number | undefined): string {
    if (v == null) return '—';
    return `${v >= 0 ? '+' : ''}₹${v.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }
  formatTime(iso: string | null): string {
    if (!iso) return '—';
    try { return new Date(iso).toLocaleTimeString('en-IN', { hour12: false }); } catch { return iso; }
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
}
