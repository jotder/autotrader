import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  DxDataGridModule,
  DxButtonModule,
  DxPopupModule,
  DxSelectBoxModule,
  DxCheckBoxModule,
  DxTextBoxModule,
  DxNumberBoxModule,
  DxValidatorModule,
  DxValidationGroupModule,
  DxValidationGroupComponent,
  DxDropDownButtonModule,
  DxToolbarModule,
  DxRadioGroupModule,
} from 'devextreme-angular';
import { Subscription } from 'rxjs';
import { SymbolService, TradingSymbol } from '../../shared/services/symbol.service';
import notify from 'devextreme/ui/notify';

interface SymbolForm {
  id?: number;
  symbol: string;
  name: string;
  exchange: string;
  segment: string;
  instrumentType: string;
  lotSize: number;
  tickSize: number;
  currency: string;
  active: boolean;
}

const BLANK: SymbolForm = {
  symbol: '', name: '', exchange: 'NASDAQ', segment: 'EQ',
  instrumentType: 'STOCK', lotSize: 1, tickSize: 0.01, currency: 'USD', active: true,
};

@Component({
  selector: 'symbol-master',
  standalone: true,
  imports: [
    CommonModule,
    DxDataGridModule,
    DxButtonModule,
    DxPopupModule,
    DxSelectBoxModule,
    DxCheckBoxModule,
    DxTextBoxModule,
    DxNumberBoxModule,
    DxValidatorModule,
    DxValidationGroupModule,
    DxDropDownButtonModule,
    DxToolbarModule,
    DxRadioGroupModule,
  ],
  template: `
    <h2 class="content-block">Symbol Master</h2>
    <div class="content-block">
      <div class="dx-card responsive-paddings">

        <!-- ── Toolbar ── -->
        <dx-toolbar class="grid-toolbar">
          <dxi-item location="before" template="titleTpl"></dxi-item>
          <dxi-item location="after"  template="addBtnTpl"></dxi-item>

          <div *dxTemplate="let d of 'titleTpl'" class="grid-title">
            Symbol Master
            <span class="count-chip">{{ symbols.length }} symbols</span>
          </div>

          <div *dxTemplate="let d of 'addBtnTpl'">
            <dx-button text="Add Symbol" type="success" icon="plus" (onClick)="openAdd()"></dx-button>
          </div>
        </dx-toolbar>

        <!-- ── Data Grid ── -->
        <dx-data-grid
          [dataSource]="symbols"
          [showBorders]="true"
          [rowAlternationEnabled]="true"
          [hoverStateEnabled]="true"
          [columnAutoWidth]="false"
          keyExpr="id">
          <dxo-search-panel [visible]="true" placeholder="Search…" [width]="220"></dxo-search-panel>
          <dxo-filter-row [visible]="true"></dxo-filter-row>
          <dxo-header-filter [visible]="true"></dxo-header-filter>
          <dxo-sorting mode="multiple"></dxo-sorting>
          <dxo-column-chooser [enabled]="true" mode="select"></dxo-column-chooser>
          <dxo-paging [pageSize]="15"></dxo-paging>
          <dxo-pager [showInfo]="true" [showPageSizeSelector]="true" [allowedPageSizes]="[10,15,25]"></dxo-pager>
          <dxo-export [enabled]="true" fileName="symbol-master"></dxo-export>

          <dxi-column dataField="symbol"         caption="Symbol"          [width]="110" sortOrder="asc" cellTemplate="symTpl"></dxi-column>
          <dxi-column dataField="name"           caption="Name"            [minWidth]="160"></dxi-column>
          <dxi-column dataField="exchange"       caption="Exchange"        [width]="100"  cellTemplate="exTpl"></dxi-column>
          <dxi-column dataField="segment"        caption="Segment"         [width]="90"   cellTemplate="segTpl"></dxi-column>
          <dxi-column dataField="instrumentType" caption="Instr. Type"     [width]="130"  cellTemplate="instTpl"></dxi-column>
          <dxi-column dataField="lotSize"        caption="Lot"             [width]="70"   dataType="number"></dxi-column>
          <dxi-column dataField="tickSize"       caption="Tick"            [width]="70"   dataType="number" format="#,##0.##"></dxi-column>
          <dxi-column dataField="currency"       caption="CCY"             [width]="60"></dxi-column>
          <dxi-column dataField="active"         caption="Status"          [width]="90"   cellTemplate="activeTpl"></dxi-column>
          <dxi-column caption="Actions"          [width]="90"              cellTemplate="actionTpl" [allowFiltering]="false" [allowSorting]="false"></dxi-column>

          <!-- Symbol cell -->
          <div *dxTemplate="let c of 'symTpl'">
            <strong class="sym-link">{{ c.value }}</strong>
          </div>

          <!-- Exchange cell -->
          <div *dxTemplate="let c of 'exTpl'">
            <span class="badge ex">{{ c.value }}</span>
          </div>

          <!-- Segment cell -->
          <div *dxTemplate="let c of 'segTpl'">
            <span class="badge" [ngClass]="'seg-' + c.value">{{ c.value }}</span>
          </div>

          <!-- Instrument type cell -->
          <div *dxTemplate="let c of 'instTpl'">
            <span class="badge" [ngClass]="'inst-' + c.value">{{ c.value }}</span>
          </div>

          <!-- Active cell -->
          <div *dxTemplate="let c of 'activeTpl'">
            <span class="status-pill" [class.active]="c.value" [class.inactive]="!c.value">
              {{ c.value ? 'Active' : 'Inactive' }}
            </span>
          </div>

          <!-- Actions cell — DxDropDownButton -->
          <div *dxTemplate="let c of 'actionTpl'">
            <dx-drop-down-button
              [items]="rowActions"
              displayExpr="text"
              keyExpr="id"
              icon="overflow"
              stylingMode="text"
              [showArrowIcon]="false"
              [dropDownOptions]="{ width: 150 }"
              (onItemClick)="onRowAction($event, c.data)">
            </dx-drop-down-button>
          </div>

          <!-- Grid summary row -->
          <dxo-summary>
            <dxi-total-item column="symbol"   summaryType="count" displayFormat="{0} symbols"></dxi-total-item>
            <dxi-total-item column="active"   summaryType="count" [customizeText]="activeCountText"></dxi-total-item>
          </dxo-summary>

        </dx-data-grid>
      </div>
    </div>

    <!-- ─── Add / Edit Popup ─── -->
    <dx-popup
      [visible]="popupVisible"
      [title]="popupTitle"
      [width]="700"
      [height]="'auto'"
      [showCloseButton]="true"
      (onHidden)="popupVisible = false">
      <div *dxTemplate="let data of 'content'">

        <dx-validation-group #valGroup>
          <div class="form-grid">

            <div class="form-field">
              <label>Symbol <span class="req">*</span></label>
              <dx-text-box [(value)]="editForm.symbol" [inputAttr]="{ autocomplete: 'off' }">
                <dx-validator>
                  <dxi-validation-rule type="required" message="Symbol is required"></dxi-validation-rule>
                  <dxi-validation-rule type="stringLength" [min]="1" [max]="20" message="1–20 characters"></dxi-validation-rule>
                </dx-validator>
              </dx-text-box>
            </div>

            <div class="form-field">
              <label>Name <span class="req">*</span></label>
              <dx-text-box [(value)]="editForm.name">
                <dx-validator>
                  <dxi-validation-rule type="required" message="Name is required"></dxi-validation-rule>
                </dx-validator>
              </dx-text-box>
            </div>

            <div class="form-field">
              <label>Exchange <span class="req">*</span></label>
              <dx-select-box [dataSource]="exchanges" [(value)]="editForm.exchange">
                <dx-validator>
                  <dxi-validation-rule type="required" message="Exchange is required"></dxi-validation-rule>
                </dx-validator>
              </dx-select-box>
            </div>

            <div class="form-field">
              <label>Segment <span class="req">*</span></label>
              <dx-select-box [dataSource]="segments" [(value)]="editForm.segment">
                <dx-validator>
                  <dxi-validation-rule type="required" message="Segment is required"></dxi-validation-rule>
                </dx-validator>
              </dx-select-box>
            </div>

            <div class="form-field">
              <label>Instrument Type <span class="req">*</span></label>
              <dx-select-box [dataSource]="instrumentTypes" [(value)]="editForm.instrumentType">
                <dx-validator>
                  <dxi-validation-rule type="required" message="Instrument type is required"></dxi-validation-rule>
                </dx-validator>
              </dx-select-box>
            </div>

            <div class="form-field">
              <label>Currency</label>
              <dx-select-box [dataSource]="currencies" [(value)]="editForm.currency"></dx-select-box>
            </div>

            <div class="form-field">
              <label>Lot Size</label>
              <dx-number-box [(value)]="editForm.lotSize" [min]="1" [showSpinButtons]="true"></dx-number-box>
            </div>

            <div class="form-field">
              <label>Tick Size</label>
              <dx-number-box [(value)]="editForm.tickSize" [min]="0.001" [step]="0.01" format="#,##0.###"></dx-number-box>
            </div>

            <div class="form-field full-row">
              <label>Status</label>
              <dx-radio-group
                [dataSource]="statusOptions"
                [(value)]="editForm.active"
                layout="horizontal"
                displayExpr="label"
                valueExpr="value">
              </dx-radio-group>
            </div>

          </div>

          <div class="popup-footer">
            <dx-button text="Cancel" type="normal"  (onClick)="popupVisible = false"></dx-button>
            <dx-button
              [text]="isEditMode ? 'Save Changes' : 'Add Symbol'"
              type="success"
              (onClick)="saveForm(valGroup)">
            </dx-button>
          </div>
        </dx-validation-group>

      </div>
    </dx-popup>

    <!-- ─── Delete Confirm Popup ─── -->
    <dx-popup
      [visible]="deleteVisible"
      title="Confirm Delete"
      [width]="420"
      [height]="190"
      [showCloseButton]="true"
      (onHidden)="deleteVisible = false">
      <div *dxTemplate="let data of 'content'">
        <p class="del-msg">
          Remove <strong>{{ deleteTarget?.symbol }}</strong> — {{ deleteTarget?.name }} from symbol master?
        </p>
        <div class="popup-footer">
          <dx-button text="Cancel" type="normal"  (onClick)="deleteVisible = false"></dx-button>
          <dx-button text="Delete" type="danger"  (onClick)="executeDelete()"></dx-button>
        </div>
      </div>
    </dx-popup>
  `,
  styles: [`
    .grid-title {
      font-size: 15px; font-weight: 600;
      display: flex; align-items: center; gap: 8px;
    }
    .count-chip {
      background: #e9ecef; color: #555;
      border-radius: 10px; padding: 2px 10px; font-size: 12px; font-weight: 400;
    }
    :host ::ng-deep .grid-toolbar { margin-bottom: 12px; }

    /* Badges */
    .badge { border-radius: 4px; padding: 2px 8px; font-size: 11px; font-weight: 600; }
    .ex           { background: #e8f4fd; color: #0366d6; }
    .seg-EQ       { background: #d4edda; color: #155724; }
    .seg-FO       { background: #fff3cd; color: #856404; }
    .seg-CD       { background: #cce5ff; color: #004085; }
    .seg-COMM     { background: #f8d7da; color: #721c24; }
    .inst-STOCK   { background: #e3f2fd; color: #1565c0; }
    .inst-INDEX   { background: #f3e5f5; color: #6a1b9a; }
    .inst-FUTURES { background: #fff8e1; color: #e65100; }
    .inst-OPTIONS { background: #fce4ec; color: #880e4f; }
    .inst-ETF     { background: #e8f5e9; color: #2e7d32; }
    .inst-CURRENCY{ background: #e0f7fa; color: #006064; }

    .status-pill { border-radius: 12px; padding: 2px 10px; font-size: 11px; font-weight: 600; }
    .status-pill.active   { background: #d4edda; color: #155724; }
    .status-pill.inactive { background: #f8f9fa; color: #6c757d; }

    .sym-link { color: #007bff; }

    /* Popup form */
    .form-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 14px 22px; padding: 4px 0 16px;
    }
    .form-field { display: flex; flex-direction: column; gap: 5px; }
    .form-field label { font-size: 12px; font-weight: 600; color: #555; }
    .full-row { grid-column: 1 / -1; }
    .req { color: #dc3545; }

    .popup-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 12px; border-top: 1px solid #eee; }

    .del-msg { font-size: 14px; margin: 0 0 20px; line-height: 1.6; }
  `]
})
export class SymbolMaster implements OnInit, OnDestroy {
  @ViewChild('valGroup') valGroup!: DxValidationGroupComponent;

  symbols: TradingSymbol[] = [];
  exchanges: string[] = [];
  segments: string[] = [];
  instrumentTypes: string[] = [];
  currencies = ['USD', 'INR', 'EUR', 'GBP', 'JPY', 'AUD'];
  statusOptions = [
    { value: true,  label: 'Active' },
    { value: false, label: 'Inactive' },
  ];
  rowActions = [
    { id: 'edit',   text: 'Edit',   icon: 'edit'  },
    { id: 'delete', text: 'Delete', icon: 'trash' },
  ];

  popupVisible = false;
  popupTitle = 'Add Symbol';
  isEditMode = false;
  editForm: SymbolForm = { ...BLANK };

  deleteVisible = false;
  deleteTarget: TradingSymbol | null = null;

  private sub!: Subscription;

  constructor(private symbolService: SymbolService) {}

  ngOnInit() {
    this.exchanges       = this.symbolService.getExchanges();
    this.segments        = this.symbolService.getSegments();
    this.instrumentTypes = this.symbolService.getInstrumentTypes();
    this.sub = this.symbolService.getSymbols().subscribe(s => this.symbols = s);
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
  }

  openAdd() {
    this.isEditMode = false;
    this.popupTitle = 'Add Symbol';
    this.editForm = { ...BLANK };
    this.popupVisible = true;
  }

  onRowAction(e: any, row: TradingSymbol) {
    if (e.itemData?.id === 'edit')   this.openEdit(row);
    if (e.itemData?.id === 'delete') this.confirmDelete(row);
  }

  openEdit(sym: TradingSymbol) {
    this.isEditMode = true;
    this.popupTitle = `Edit — ${sym.symbol}`;
    this.editForm = { ...BLANK, ...sym };
    this.popupVisible = true;
  }

  saveForm(group: DxValidationGroupComponent) {
    const result = group.instance.validate();
    if (!result.isValid) return;

    if (this.isEditMode && this.editForm.id != null) {
      this.symbolService.updateSymbol(this.editForm.id, this.editForm).subscribe(() => {
        notify(`${this.editForm.symbol} updated`, 'success', 2500);
        this.popupVisible = false;
      });
    } else {
      this.symbolService.addSymbol(this.editForm).subscribe(s => {
        notify(`${s.symbol} added to symbol master`, 'success', 2500);
        this.popupVisible = false;
      });
    }
  }

  confirmDelete(sym: TradingSymbol) {
    this.deleteTarget = sym;
    this.deleteVisible = true;
  }

  executeDelete() {
    if (!this.deleteTarget) return;
    this.symbolService.deleteSymbol(this.deleteTarget.id).subscribe(() => {
      notify(`${this.deleteTarget!.symbol} removed`, 'warning', 2500);
      this.deleteTarget = null;
      this.deleteVisible = false;
    });
  }

  activeCountText = (e: any) => {
    const active = this.symbols.filter(s => s.active).length;
    return `${active} active / ${this.symbols.length - active} inactive`;
  };
}
