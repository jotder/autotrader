import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  DxButtonModule,
  DxPopupModule,
  DxTextAreaModule,
  DxLoadIndicatorModule,
} from 'devextreme-angular';
import { Subscription } from 'rxjs';
import { BrokerService, BrokerState } from '../../services/broker.service';

@Component({
  selector: 'broker-connector',
  standalone: true,
  imports: [CommonModule, DxButtonModule, DxPopupModule, DxTextAreaModule, DxLoadIndicatorModule],
  template: `
    <!-- Header pill button -->
    <dx-button
      stylingMode="outlined"
      [ngClass]="pillClass"
      [icon]="pillIcon"
      [text]="pillText"
      (onClick)="openPopup()">
    </dx-button>

    <!-- Connection popup -->
    <dx-popup
      [(visible)]="popupVisible"
      [width]="420"
      [height]="'auto'"
      [showTitle]="true"
      title="Broker Connection"
      [dragEnabled]="true"
      [hideOnOutsideClick]="true"
      [showCloseButton]="true">

      <div *dxTemplate="let d of 'content'" class="popup-body">

        <!-- Status banner -->
        <div class="status-banner" [ngClass]="bannerClass" *ngIf="state.status !== 'disconnected'">
          <dx-load-indicator
            *ngIf="state.status === 'connecting'"
            [height]="16" [width]="16">
          </dx-load-indicator>
          <span class="status-icon" *ngIf="state.status !== 'connecting'">
            {{ state.status === 'connected' ? '✓' : '✗' }}
          </span>
          <span class="status-text">{{ statusLabel }}</span>
        </div>

        <ng-container *ngIf="state.status !== 'connected'">
          <label class="field-label">Access Token</label>
          <dx-text-area
            [(value)]="accessToken"
            placeholder="Paste your broker access token here…"
            [height]="90"
            [maxLength]="512"
            stylingMode="outlined">
          </dx-text-area>

          <div class="error-msg" *ngIf="state.status === 'error'">
            {{ state.errorMsg }}
          </div>

          <div class="popup-actions">
            <dx-button
              text="Connect"
              type="success"
              icon="check"
              [disabled]="!accessToken.trim() || state.status === 'connecting'"
              (onClick)="connect()">
            </dx-button>
            <dx-button
              text="Cancel"
              type="normal"
              stylingMode="outlined"
              (onClick)="popupVisible = false">
            </dx-button>
          </div>
        </ng-container>

        <div *ngIf="state.status === 'connected'" class="popup-actions">
          <dx-button
            text="Disconnect"
            type="danger"
            icon="close"
            (onClick)="disconnect()">
          </dx-button>
          <dx-button
            text="Close"
            type="normal"
            stylingMode="outlined"
            (onClick)="popupVisible = false">
          </dx-button>
        </div>

      </div>
    </dx-popup>
  `,
  styles: [`
    :host { display: flex; align-items: center; }

    /* Pill button states */
    ::ng-deep .pill-disconnected.dx-button { border-color: #ccc; color: #888; }
    ::ng-deep .pill-connecting.dx-button   { border-color: #f5a623; color: #f5a623; }
    ::ng-deep .pill-connected.dx-button    { border-color: #28a745; color: #28a745; }
    ::ng-deep .pill-error.dx-button        { border-color: #dc3545; color: #dc3545; }

    /* Popup body */
    .popup-body { display: flex; flex-direction: column; gap: 14px; padding: 4px 0; }
    .field-label { font-size: 12px; font-weight: 600; color: #555; }

    /* Status banner */
    .status-banner {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 12px; border-radius: 6px; font-size: 13px;
    }
    .banner-connecting { background: #fff8e1; color: #b45309; }
    .banner-connected  { background: #d4edda; color: #155724; }
    .banner-error      { background: #f8d7da; color: #721c24; }

    .status-icon { font-weight: 700; font-size: 15px; }
    .status-text { flex: 1; }

    .error-msg { font-size: 12px; color: #dc3545; margin-top: -6px; }

    .popup-actions { display: flex; gap: 10px; justify-content: flex-end; padding-top: 4px; }
  `]
})
export class BrokerConnector implements OnInit, OnDestroy {
  popupVisible = false;
  accessToken = '';
  state: BrokerState = { status: 'disconnected', errorMsg: '' };

  private sub!: Subscription;

  constructor(private brokerService: BrokerService) {}

  ngOnInit() {
    this.sub = this.brokerService.state.subscribe(s => this.state = s);
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  get pillClass(): string {
    return `pill-${this.state.status}`;
  }

  get pillIcon(): string {
    const icons: Record<string, string> = {
      disconnected: 'minus',
      connecting:   'clock',
      connected:    'check',
      error:        'warning',
    };
    return icons[this.state.status];
  }

  get pillText(): string {
    const labels: Record<string, string> = {
      disconnected: 'Broker: Off',
      connecting:   'Connecting…',
      connected:    'Broker: Live',
      error:        'Broker: Error',
    };
    return labels[this.state.status];
  }

  get bannerClass(): string {
    return `banner-${this.state.status}`;
  }

  get statusLabel(): string {
    const labels: Record<string, string> = {
      connecting: 'Connecting to broker…',
      connected:  'Connected successfully',
      error:      'Connection failed',
    };
    return labels[this.state.status] ?? '';
  }

  openPopup() { this.popupVisible = true; }

  async connect() {
    await this.brokerService.connect(this.accessToken);
    if (this.brokerService.snapshot.status === 'connected') {
      this.accessToken = '';
    }
  }

  disconnect() {
    this.brokerService.disconnect();
    this.accessToken = '';
  }
}
