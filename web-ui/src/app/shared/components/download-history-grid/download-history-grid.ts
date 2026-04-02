import { Component, Input } from '@angular/core';
import { DxDataGridModule } from 'devextreme-angular';
import { DownloadHistory } from '../../services/tick-data.service';

@Component({
  selector: 'app-download-history-grid',
  template: `
    <dx-data-grid
      [dataSource]="dataSource"
      [showBorders]="true"
      [columnAutoWidth]="true">
      <dxo-filter-row [visible]="true"></dxo-filter-row>
      <dxo-header-filter [visible]="true"></dxo-header-filter>
      <dxo-paging [pageSize]="10"></dxo-paging>

      <dxi-column dataField="symbol" caption="Symbol"></dxi-column>
      <dxi-column dataField="dataDate" caption="Data Date" dataType="date"></dxi-column>
      <dxi-column dataField="downloadDate" caption="Download Date" dataType="date"></dxi-column>
    </dx-data-grid>
  `,
  standalone: true,
  imports: [DxDataGridModule]
})
export class DownloadHistoryGrid {
  @Input() dataSource: DownloadHistory[] = [];
}
