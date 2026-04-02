import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import {
  DxSelectBoxModule,
  DxCalendarModule,
  DxTagBoxModule,
  DxDataGridModule,
  DxProgressBarModule,
  DxLoadIndicatorModule,
  DxButtonModule,
} from 'devextreme-angular';
import { TickDataService, DownloadJobStatus } from '../../shared/services/tick-data.service';
import notify from 'devextreme/ui/notify';

interface DownloadTask {
  date: string;
  symbol: string;
  status: 'pending' | 'downloading' | 'success' | 'error';
  progress: number;
  candleCount?: number;
  errorMsg?: string;
}

@Component({
  selector: 'candle-download',
  standalone: true,
  imports: [
    CommonModule,
    DxSelectBoxModule,
    DxCalendarModule,
    DxTagBoxModule,
    DxDataGridModule,
    DxProgressBarModule,
    DxLoadIndicatorModule,
    DxButtonModule,
  ],
  template: `
    <h2 class="content-block">Candle Data Download</h2>

    <!-- ── Download Form ── -->
    <div class="content-block">
      <div class="dx-card responsive-paddings">

        <div class="form-layout">

          <!-- Left: Symbol + Calendar -->
          <div class="form-left">
            <!-- Symbol -->
            <div class="form-field">
              <label class="field-label">Symbol</label>
              <dx-select-box
                [dataSource]="symbols"
                [(value)]="selectedSymbol"
                placeholder="Select symbol…"
                [showClearButton]="true"
                [searchEnabled]="true"
                width="280"
                (onValueChanged)="onSymbolChange($event)">
              </dx-select-box>
              <span *ngIf="selectedSymbol && availableDatesStr.length" class="local-badge">
                {{ availableDatesStr.length }} dates already local
              </span>
            </div>

            <!-- Calendar -->
            <div class="form-field" *ngIf="selectedSymbol">
              <label class="field-label">
                Select Dates
                <span class="legend-inline">
                  <span class="dot green"></span> local &nbsp;
                  <span class="dot blue"></span> new
                </span>
              </label>
              <dx-calendar
                selectionMode="multiple"
                [(value)]="selectedDates"
                [cellTemplate]="'cell'">
                <div *dxTemplate="let c of 'cell'"
                     [class.cell-local]="isLocal(c.date)"
                     [class.cell-weekend]="isWeekend(c.date)">
                  {{ c.text }}
                </div>
              </dx-calendar>
            </div>
          </div>

          <!-- Right: Selection summary + Submit -->
          <div class="form-right" *ngIf="selectedSymbol">

            <div class="summary-box">
              <div class="summary-row">
                <span class="sum-label">Symbol</span>
                <strong>{{ selectedSymbol }}</strong>
              </div>
              <div class="summary-row">
                <span class="sum-label">Selected dates</span>
                <span>{{ selectedDates.length }}</span>
              </div>
              <div class="summary-row">
                <span class="sum-label">New to download</span>
                <span [class.highlight]="newDates.length > 0">{{ newDates.length }}</span>
              </div>
              <div class="summary-row" *ngIf="dupDates.length">
                <span class="sum-label">Already local</span>
                <span class="muted">{{ dupDates.length }} (skipped)</span>
              </div>
            </div>

            <!-- New dates tag box -->
            <div *ngIf="newDates.length > 0" class="tag-section">
              <label class="field-label">Dates queued for download:</label>
              <dx-tag-box
                [value]="newDates"
                [items]="newDates"
                [readOnly]="true"
                [showDropDownButton]="false"
                [multiline]="true">
              </dx-tag-box>
            </div>

            <div *ngIf="selectedDates.length > 0 && newDates.length === 0" class="warn-box">
              All selected dates are already in your local database.
            </div>

            <!-- Submit -->
            <div class="submit-row" *ngIf="!isDownloading">
              <dx-button
                [text]="newDates.length ? 'Download ' + newDates.length + ' Date(s) for ' + selectedSymbol : 'Select dates to download'"
                type="success"
                icon="download"
                [disabled]="newDates.length === 0"
                (onClick)="startDownload()">
              </dx-button>
              <dx-button
                *ngIf="selectedDates.length > 0"
                text="Clear Selection"
                type="normal"
                stylingMode="outlined"
                (onClick)="reset()">
              </dx-button>
            </div>

            <div *ngIf="isDownloading" class="downloading-msg">
              <dx-load-indicator [height]="20" [width]="20"></dx-load-indicator>
              Downloading {{ doneCount }}/{{ downloadTasks.length }} dates…
            </div>

          </div>
        </div>
      </div>
    </div>

    <!-- ── Progress Grid ── -->
    <div class="content-block" *ngIf="downloadTasks.length > 0">
      <div class="dx-card responsive-paddings">
        <div class="progress-header">
          <span class="progress-title">Download Progress — {{ selectedSymbol }}</span>
          <span class="progress-count">{{ doneCount }}/{{ downloadTasks.length }} complete</span>
          <dx-button
            *ngIf="downloadDone"
            text="New Download"
            type="normal"
            icon="refresh"
            stylingMode="outlined"
            (onClick)="reset()">
          </dx-button>
        </div>

        <dx-data-grid
          [dataSource]="downloadTasks"
          [showBorders]="true"
          [columnAutoWidth]="false"
          [rowAlternationEnabled]="true"
          keyExpr="date">
          <dxo-paging [enabled]="false"></dxo-paging>

          <dxi-column dataField="date"   caption="Date"   [width]="130"></dxi-column>
          <dxi-column dataField="symbol" caption="Symbol" [width]="100"></dxi-column>
          <dxi-column caption="Progress" [minWidth]="240" cellTemplate="progTpl"></dxi-column>
          <dxi-column caption="Result"   [minWidth]="210" cellTemplate="resTpl"></dxi-column>

          <div *dxTemplate="let cell of 'progTpl'" class="prog-cell">
            <dx-progress-bar
              [min]="0" [max]="100"
              [value]="cell.data.progress"
              [showStatus]="cell.data.status === 'downloading'"
              [ngClass]="{
                'bar-success': cell.data.status === 'success',
                'bar-error':   cell.data.status === 'error'
              }">
            </dx-progress-bar>
          </div>

          <div *dxTemplate="let cell of 'resTpl'" class="status-cell">
            <dx-load-indicator
              *ngIf="cell.data.status === 'downloading'"
              [height]="18" [width]="18">
            </dx-load-indicator>
            <span *ngIf="cell.data.status === 'pending'"  class="s-pending">⏳ Queued</span>
            <span *ngIf="cell.data.status === 'success'"  class="s-ok">✓ {{ cell.data.candleCount | number }} candles</span>
            <span *ngIf="cell.data.status === 'error'"    class="s-err">✗ {{ cell.data.errorMsg }}</span>
          </div>

        </dx-data-grid>
      </div>
    </div>
  `,
  styles: [`
    /* Two-column form layout */
    .form-layout { display: flex; gap: 32px; align-items: flex-start; flex-wrap: wrap; }
    .form-left  { display: flex; flex-direction: column; gap: 20px; flex: 0 0 auto; }
    .form-right { flex: 1; min-width: 260px; display: flex; flex-direction: column; gap: 16px; }

    .form-field { display: flex; flex-direction: column; gap: 6px; }
    .field-label { font-size: 13px; font-weight: 600; color: #444; display: flex; align-items: center; gap: 8px; }

    .legend-inline { font-size: 11px; font-weight: 400; color: #888; display: flex; align-items: center; gap: 4px; }
    .dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; }
    .dot.green { background: #28a745; }
    .dot.blue  { background: #007bff; }

    :host ::ng-deep .cell-local   { color: #28a745 !important; font-weight: 700; }
    :host ::ng-deep .cell-weekend { opacity: 0.4; }

    .local-badge {
      margin-top: 4px;
      display: inline-block;
      background: #e8f4fd; color: #0077cc;
      border-radius: 12px; padding: 3px 12px; font-size: 12px;
    }

    /* Summary box */
    .summary-box {
      border: 1px solid #e8e8e8;
      border-radius: 6px;
      padding: 14px 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      background: #fafafa;
    }
    .summary-row { display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
    .sum-label { color: #888; }
    .highlight { color: #007bff; font-weight: 700; }
    .muted { color: #aaa; }

    /* Tag box */
    .tag-section { display: flex; flex-direction: column; gap: 6px; }

    .warn-box { padding: 8px 14px; background: #fff3cd; border-radius: 4px; color: #856404; font-size: 13px; }

    /* Submit row */
    .submit-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; padding-top: 4px; }

    .downloading-msg {
      display: flex; align-items: center; gap: 10px;
      font-size: 13px; color: #555; padding: 6px 0;
    }

    /* Progress section header */
    .progress-header {
      display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap;
    }
    .progress-title { font-size: 14px; font-weight: 600; flex: 1; }
    .progress-count { font-size: 12px; color: #888; }

    /* Grid cells */
    .prog-cell   { padding: 6px 0; }
    .status-cell { display: flex; align-items: center; gap: 6px; padding: 4px 0; }
    .s-pending { color: #bbb; font-size: 12px; }
    .s-ok  { color: #28a745; font-size: 13px; font-weight: 600; }
    .s-err { color: #dc3545; font-size: 13px; }

    :host ::ng-deep .bar-success .dx-progressbar-range { background-color: #28a745 !important; }
    :host ::ng-deep .bar-error   .dx-progressbar-range { background-color: #dc3545 !important; }
  `]
})
export class CandleDownload implements OnInit, OnDestroy {
  symbols: string[] = [];
  selectedSymbol = '';
  availableDatesStr: string[] = [];
  selectedDates: Date[] = [];
  downloadTasks: DownloadTask[] = [];
  isDownloading = false;
  downloadDone = false;

  private pollInterval: ReturnType<typeof setInterval> | null = null;
  private destroy$ = new Subject<void>();

  constructor(
    private tickDataService: TickDataService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.tickDataService.getSymbols().subscribe(s => this.symbols = s);
  }

  ngOnDestroy() {
    this.stopPoll();
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSymbolChange(e: any) {
    this.selectedSymbol = e.value || '';
    this.selectedDates = [];
    this.availableDatesStr = [];
    this.clearTasks();
    if (this.selectedSymbol) {
      this.tickDataService.getAvailableDates(this.selectedSymbol).subscribe(dates => {
        this.availableDatesStr = dates;
      });
    }
  }

  isLocal(date: Date): boolean {
    return this.availableDatesStr.includes(this.fmtDate(date));
  }

  isWeekend(date: Date): boolean {
    const d = date.getDay();
    return d === 0 || d === 6;
  }

  get newDates(): string[] {
    return this.selectedDates.map(d => this.fmtDate(d)).filter(ds => !this.availableDatesStr.includes(ds));
  }

  get dupDates(): string[] {
    return this.selectedDates.map(d => this.fmtDate(d)).filter(ds => this.availableDatesStr.includes(ds));
  }

  get doneCount(): number {
    return this.downloadTasks.filter(t => t.status === 'success' || t.status === 'error').length;
  }

  startDownload() {
    if (!this.newDates.length) return;

    this.downloadTasks = this.newDates.map(ds => ({
      date: ds, symbol: this.selectedSymbol,
      status: 'downloading' as const, progress: 10,
    }));
    this.isDownloading = true;
    this.downloadDone = false;

    const sorted = [...this.newDates].sort();
    const from = sorted[0];
    const to = sorted[sorted.length - 1];

    this.tickDataService.startDownloadJob(this.selectedSymbol, from, to).subscribe({
      next: (jobId: string) => {
        this.startPoll(jobId);
      },
      error: () => {
        this.downloadTasks.forEach(t => {
          t.status = 'error';
          t.errorMsg = 'Failed to start download job';
          t.progress = 0;
        });
        this.isDownloading = false;
        notify('Failed to start download job', 'error', 4000);
        this.cdr.detectChanges();
      },
    });
  }

  private startPoll(jobId: string) {
    this.pollInterval = setInterval(() => {
      if (this.pollInterval === null) return;
      this.tickDataService.pollJob(jobId).pipe(
        takeUntil(this.destroy$)
      ).subscribe({
        next: (job: DownloadJobStatus) => this.handleJobStatus(job),
        error: () => {
          // transient poll error — keep polling
        },
      });
    }, 1000);
  }

  private handleJobStatus(job: DownloadJobStatus) {
    const parts = job.progress.split('/');
    const done = parseInt(parts[0], 10) || 0;
    const total = parseInt(parts[1], 10) || 1;
    const pct = Math.round((done / total) * 80) + 10;

    this.downloadTasks.forEach(t => {
      if (t.status === 'downloading') {
        t.progress = pct;
      }
    });
    this.cdr.detectChanges();

    if (job.status === 'COMPLETED' || job.status === 'FAILED') {
      this.stopPoll();
      this.finalizeTasksFromJob(job);
    }
  }

  private finalizeTasksFromJob(job: DownloadJobStatus) {
    this.tickDataService.getAvailableDates(this.selectedSymbol).pipe(
      takeUntil(this.destroy$)
    ).subscribe(freshDates => {
      this.availableDatesStr = freshDates;

      if (job.status === 'FAILED') {
        this.downloadTasks.forEach(t => {
          t.status = 'error';
          t.errorMsg = job.error ?? 'Download job failed';
          t.progress = 0;
        });
      } else {
        this.downloadTasks.forEach(t => {
          if (freshDates.includes(t.date)) {
            t.status = 'success';
            t.progress = 100;
            t.candleCount = 0;
          } else {
            t.status = 'error';
            t.progress = 0;
            t.errorMsg = 'Date not downloaded (weekend/holiday or API error)';
          }
        });
      }

      this.isDownloading = false;
      this.downloadDone = true;

      const ok = this.downloadTasks.filter(t => t.status === 'success').length;
      notify(
        `Download complete: ${ok}/${this.downloadTasks.length} succeeded`,
        ok === this.downloadTasks.length ? 'success' : 'warning',
        4000
      );
      this.cdr.detectChanges();
    });
  }

  private stopPoll() {
    if (this.pollInterval !== null) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  reset() {
    this.stopPoll();
    this.clearTasks();
    this.selectedDates = [];
  }

  private clearTasks() {
    this.stopPoll();
    this.downloadTasks = [];
    this.isDownloading = false;
    this.downloadDone = false;
  }

  fmtDate(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
