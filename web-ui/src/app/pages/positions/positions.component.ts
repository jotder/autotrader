import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { GlobalStateService } from '../../core/services/global-state.service';
import { Position } from '../../core/models/api.models';
import { StatusCardComponent } from '../../shared/components/status-card.component';
import { environment } from '../../../environments/environment';
import { MatIconModule } from '@angular/material/icon';

// DevExtreme
import { DxDataGridModule } from 'devextreme-angular/ui/data-grid';
import { DxButtonModule } from 'devextreme-angular/ui/button';
import { DxPopupModule } from 'devextreme-angular/ui/popup';
import { DxTextBoxModule } from 'devextreme-angular/ui/text-box';

@Component({
  selector: 'at-positions',
  standalone: true,
  imports: [
    CommonModule, FormsModule, StatusCardComponent, MatIconModule,
    DxDataGridModule, DxButtonModule, DxPopupModule, DxTextBoxModule
  ],
  template: `
    <div class="page-header">
      <h1>Positions & Orders</h1>
      <dx-button
        text="Emergency Flatten"
        type="danger"
        stylingMode="contained"
        [disabled]="flattenPending"
        (onClick)="flatten()">
      </dx-button>
    </div>

    <!-- Open Positions -->
    <at-status-card title="Open Positions" icon="trending_up"
                    [iconColor]="state.positions().length > 0 ? 'var(--accent)' : 'var(--text-secondary)'">
      
      <dx-data-grid
        [dataSource]="state.positions()"
        [showBorders]="false"
        [rowAlternationEnabled]="true"
        [columnAutoWidth]="true"
        keyExpr="correlationId">

        <dxi-column dataField="symbol" caption="Symbol" cellTemplate="symbolTemplate"></dxi-column>
        <dxi-column dataField="direction" caption="Dir" cellTemplate="dirTemplate" [width]="70"></dxi-column>
        <dxi-column dataField="quantity" caption="Qty" alignment="right"></dxi-column>
        <dxi-column dataField="entryPrice" caption="Entry" format="#,##0.00"></dxi-column>
        <dxi-column dataField="unrealizedPnl" caption="PnL" cellTemplate="pnlTemplate" alignment="right"></dxi-column>
        <dxi-column dataField="trailingActivated" caption="Trail" cellTemplate="trailTemplate" [width]="80"></dxi-column>
        <dxi-column caption="Exit" cellTemplate="actionsTemplate" [width]="100" alignment="center"></dxi-column>

        <div *dxTemplate="let data of 'symbolTemplate'">
          <span class="mono font-bold">{{ data.value }}</span>
        </div>

        <div *dxTemplate="let data of 'dirTemplate'">
          <span class="badge" [class.buy]="data.value === 'BUY'" [class.sell]="data.value === 'SELL'">{{ data.value }}</span>
        </div>

        <div *dxTemplate="let data of 'pnlTemplate'">
          <span class="mono font-bold" [class.profit]="data.value >= 0" [class.loss]="data.value < 0">
            {{ formatPnl(data.value) }}
          </span>
        </div>

        <div *dxTemplate="let data of 'trailTemplate'">
          <span class="text-muted">{{ data.value ? 'Active' : '—' }}</span>
        </div>

        <div *dxTemplate="let data of 'actionsTemplate'">
          <dx-button
            text="Exit"
            type="danger"
            stylingMode="outlined"
            [disabled]="exitPending[data.data.correlationId]"
            (onClick)="exitPosition(data.data)">
          </dx-button>
        </div>
      </dx-data-grid>
    </at-status-card>

    <!-- Active Orders -->
    <at-status-card title="Active Orders" icon="receipt_long" iconColor="var(--info)"
                    style="margin-top: 16px">
      
      <dx-data-grid
        [dataSource]="state.activeOrders()"
        [showBorders]="false"
        [rowAlternationEnabled]="true"
        [columnAutoWidth]="true">
        
        <dxi-column dataField="clientOrderId" caption="Order ID" cellTemplate="orderIdTemplate" [width]="120"></dxi-column>
        <dxi-column dataField="symbol" caption="Symbol" [width]="120"></dxi-column>
        <dxi-column dataField="side" caption="Side" [width]="70"></dxi-column>
        <dxi-column dataField="state" caption="State" cellTemplate="stateTemplate"></dxi-column>
        <dxi-column dataField="quantity" caption="Qty" alignment="right"></dxi-column>
        <dxi-column dataField="submittedAt" caption="Submitted" dataType="datetime" format="HH:mm:ss" alignment="right"></dxi-column>

        <div *dxTemplate="let data of 'orderIdTemplate'">
          <span class="mono text-muted">{{ data.value | slice:0:12 }}…</span>
        </div>

        <div *dxTemplate="let data of 'stateTemplate'">
          <span class="badge state-badge">{{ data.value }}</span>
        </div>
      </dx-data-grid>
    </at-status-card>

    <!-- Confirmation Popup -->
    <dx-popup
      [width]="400"
      [height]="'auto'"
      [showTitle]="true"
      [title]="confirmAction?.title || 'Confirm Action'"
      [dragEnabled]="false"
      [hideOnOutsideClick]="true"
      [(visible)]="showConfirmPopup">
      <div *dxTemplate="let data of 'content'">
        <p class="confirm-msg">{{ confirmAction?.message }}</p>
        
        @if (confirmAction?.requireType) {
          <div class="dx-field">
            <div class="dx-field-label">Type <b>{{ confirmAction?.requireType }}</b> to confirm</div>
            <div class="dx-field-value">
              <dx-text-box [(value)]="confirmTyped"></dx-text-box>
            </div>
          </div>
        }

        <div class="popup-actions">
          <dx-button text="Cancel" stylingMode="text" (onClick)="showConfirmPopup = false"></dx-button>
          <dx-button
            [text]="confirmAction?.confirmLabel"
            type="danger"
            stylingMode="contained"
            [disabled]="confirmAction?.requireType && confirmTyped !== confirmAction?.requireType"
            (onClick)="executeConfirmed()">
          </dx-button>
        </div>
      </div>
    </dx-popup>
  `,
  styles: [`
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    h1 { margin: 0; font-size: 20px; font-weight: 500; }
    
    .font-bold { font-weight: 600; }
    .badge { padding: 2px 6px; border-radius: 3px; font-size: 10px; font-weight: 600; background: rgba(255,255,255,0.1); }
    .badge.buy { background: rgba(63, 185, 80, 0.15); color: var(--profit); }
    .badge.sell { background: rgba(248, 81, 73, 0.15); color: var(--loss); }
    .state-badge { background: rgba(0, 188, 212, 0.15); color: var(--accent); }

    .confirm-msg { margin-bottom: 16px; color: var(--text-secondary); }
    .popup-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }

    ::ng-deep .dx-datagrid { background-color: transparent !important; }
  `],
})
export class PositionsComponent {
  exitPending: Record<string, boolean> = {};
  flattenPending = false;
  confirmAction: any = null;
  showConfirmPopup = false;
  confirmTyped = '';

  constructor(
    private api: ApiService,
    public state: GlobalStateService
  ) {}

  exitPosition(p: Position): void {
    this.confirmAction = {
      title: 'Exit Position',
      message: `Close ${p.direction} ${p.quantity} × ${p.symbol} at market?`,
      confirmLabel: 'Exit',
      onConfirm: () => {
        this.exitPending[p.correlationId] = true;
        this.api.exitPosition(p.correlationId).subscribe({
          next: () => { this.exitPending[p.correlationId] = false; },
          error: () => { this.exitPending[p.correlationId] = false; },
        });
      },
    };
    this.showConfirmPopup = true;
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
    this.showConfirmPopup = true;
  }

  executeConfirmed(): void {
    if (this.confirmAction) {
      this.confirmAction.onConfirm();
      this.confirmAction = null;
      this.showConfirmPopup = false;
      this.confirmTyped = '';
    }
  }

  formatPnl(v: number | undefined): string {
    if (v == null) return '—';
    return `${v >= 0 ? '+' : ''}₹${v.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
  }
}
