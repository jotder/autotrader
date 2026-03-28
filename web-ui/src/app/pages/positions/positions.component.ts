import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../../core/services/api.service';
import { GlobalStateService } from '../../core/services/global-state.service';
import { Position, ManagedOrder } from '../../core/models/api.models';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'at-positions',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, StatusCardComponent],
  template: `
    <div class="page-header">
      <h1>Positions & Orders</h1>
      <button mat-flat-button color="warn" (click)="flatten()" [disabled]="flattenPending">
        Emergency Flatten
      </button>
    </div>

    <!-- Open Positions -->
    <at-status-card title="Open Positions" icon="trending_up"
                    [iconColor]="state.positions().length > 0 ? 'var(--accent)' : 'var(--text-secondary)'">
      <table mat-table [dataSource]="state.positions()" class="mat-elevation-z0">
        <ng-container matColumnDef="symbol">
          <th mat-header-cell *matHeaderCellDef> Symbol </th>
          <td mat-cell *matCellDef="let p" class="mono"> {{ p.symbol }} </td>
        </ng-container>

        <ng-container matColumnDef="direction">
          <th mat-header-cell *matHeaderCellDef> Dir </th>
          <td mat-cell *matCellDef="let p">
            <span class="badge" [class]="p.direction === 'BUY' ? 'buy' : 'sell'">{{ p.direction }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="quantity">
          <th mat-header-cell *matHeaderCellDef class="r"> Qty </th>
          <td mat-cell *matCellDef="let p" class="r mono"> {{ p.quantity }} </td>
        </ng-container>

        <ng-container matColumnDef="entryPrice">
          <th mat-header-cell *matHeaderCellDef class="r"> Entry </th>
          <td mat-cell *matCellDef="let p" class="r mono"> {{ p.entryPrice | number:'1.2-2' }} </td>
        </ng-container>

        <ng-container matColumnDef="pnl">
          <th mat-header-cell *matHeaderCellDef class="r"> PnL </th>
          <td mat-cell *matCellDef="let p" class="r mono" [class]="pnlClass(p.unrealizedPnl)">
            {{ formatPnl(p.unrealizedPnl) }}
          </td>
        </ng-container>

        <ng-container matColumnDef="trail">
          <th mat-header-cell *matHeaderCellDef> Trail </th>
          <td mat-cell *matCellDef="let p"> {{ p.trailingActivated ? 'Active' : '—' }} </td>
        </ng-container>

        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef> </th>
          <td mat-cell *matCellDef="let p" class="r">
            <button mat-stroked-button color="warn" class="btn-xs"
                    (click)="exitPosition(p)" [disabled]="exitPending[p.correlationId]">
              Exit
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="positionColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: positionColumns;"></tr>
      </table>
      @if (state.positions().length === 0) {
        <div class="empty-row">No open positions</div>
      }
    </at-status-card>

    <!-- Active Orders -->
    <at-status-card title="Active Orders" icon="receipt_long" iconColor="var(--info)"
                    style="margin-top: 16px">
      <table mat-table [dataSource]="state.activeOrders()" class="mat-elevation-z0">
        <ng-container matColumnDef="orderId">
          <th mat-header-cell *matHeaderCellDef> Order ID </th>
          <td mat-cell *matCellDef="let o" class="mono text-muted"> {{ o.clientOrderId | slice:0:12 }}… </td>
        </ng-container>

        <ng-container matColumnDef="symbol">
          <th mat-header-cell *matHeaderCellDef> Symbol </th>
          <td mat-cell *matCellDef="let o" class="mono"> {{ o.symbol }} </td>
        </ng-container>

        <ng-container matColumnDef="side">
          <th mat-header-cell *matHeaderCellDef> Side </th>
          <td mat-cell *matCellDef="let o"> {{ o.side }} </td>
        </ng-container>

        <ng-container matColumnDef="state">
          <th mat-header-cell *matHeaderCellDef> State </th>
          <td mat-cell *matCellDef="let o">
            <span class="badge state">{{ o.state }}</span>
          </td>
        </ng-container>

        <ng-container matColumnDef="quantity">
          <th mat-header-cell *matHeaderCellDef class="r"> Qty </th>
          <td mat-cell *matCellDef="let o" class="r mono"> {{ o.quantity }} </td>
        </ng-container>

        <ng-container matColumnDef="submitted">
          <th mat-header-cell *matHeaderCellDef class="r"> Submitted </th>
          <td mat-cell *matCellDef="let o" class="r mono text-muted"> {{ formatTime(o.submittedAt) }} </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="orderColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: orderColumns;"></tr>
      </table>
      @if (state.activeOrders().length === 0) {
        <div class="empty-row">No active orders</div>
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
            <button mat-button (click)="confirmAction = null">Cancel</button>
            <button mat-flat-button color="warn" (click)="executeConfirmed()"
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
    .r { text-align: right !important; justify-content: flex-end; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 11px; font-weight: 600; }
    .badge.buy { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .badge.sell { background: rgba(248, 81, 73, 0.15); color: var(--loss); }
    .badge.state { background: rgba(0, 188, 212, 0.15); color: var(--accent); }
    .btn-xs { height: 24px; line-height: 24px; padding: 0 8px; font-size: 11px; }
    .empty-row { text-align: center; padding: 24px !important; color: var(--text-muted); font-size: 13px; }
    .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .confirm-dialog { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 24px; max-width: 420px; width: 90%; }
    .confirm-dialog h3 { margin: 0 0 8px; font-size: 16px; }
    .confirm-dialog p { color: var(--text-secondary); font-size: 13px; margin: 0 0 16px; }
    .confirm-input { width: 100%; padding: 8px; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 4px; color: var(--text-primary); font-family: var(--font-mono); margin-bottom: 16px; box-sizing: border-box; }
    .confirm-actions { display: flex; gap: 8px; justify-content: flex-end; }
  `],
})
export class PositionsComponent {
  positionColumns = ['symbol', 'direction', 'quantity', 'entryPrice', 'pnl', 'trail', 'actions'];
  orderColumns = ['orderId', 'symbol', 'side', 'state', 'quantity', 'submitted'];

  exitPending: Record<string, boolean> = {};
  flattenPending = false;
  confirmAction: { title: string; message: string; requireType?: string; confirmLabel: string; onConfirm: () => void } | null = null;
  confirmTyped = '';

  constructor(
    private api: ApiService,
    public state: GlobalStateService
  ) {}

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
}
