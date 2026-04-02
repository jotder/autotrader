# Candle Download UI → Backend API Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the already-built Angular candle-download UI to the real Spring Boot backend REST endpoints (all exist in `EngineController`), replacing all mock/simulated data.

**Architecture:** Three Angular service methods need real HTTP calls; the download flow changes from a fake timer to a real job-poll loop against `/api/candle-db/download/{jobId}`. No backend changes needed — all endpoints already exist.

**Tech Stack:** Angular 18 + DevExtreme, Spring Boot 3.4.4 on port 7777, CORS pre-configured in `WebConfig.java`.

---

## Existing Backend Endpoints (no changes needed)

| Purpose | Endpoint | Returns |
|---|---|---|
| All tradeable symbols | `GET /api/symbols` | `{CM:[...], FO:[...], COM:[...], total:N}` |
| Symbols with local data | `GET /api/candle-db/symbols` | `string[]` e.g. `["NSE:SBIN-EQ"]` |
| Available dates for symbol | `GET /api/candle-db/{symbol}/dates` | `string[]` e.g. `["2026-03-25","2026-03-24"]` |
| Local data summary | `GET /api/candle-db/summary` | `[{symbol, startDate, endDate, count}]` |
| Start download job | `POST /api/candle-db/download` | `{jobId, status, message, checkUrl}` |
| Poll job status | `GET /api/candle-db/download/{jobId}` | `{jobId, status, progress, results, endTime}` |

**Download job lifecycle:**
- POST body: `{"symbols": ["NSE:SBIN-EQ"], "from": "2026-03-20", "to": "2026-03-25"}`
- `status` values: `"RUNNING"` → `"COMPLETED"` or `"FAILED"`
- `progress` is `"symbolsCompleted/total"` e.g. `"0/1"` → `"1/1"`
- `results` is `{symbol: daysDownloaded}` e.g. `{"NSE:SBIN-EQ": 3}` (or `-1` on error)

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `web-ui/proxy.conf.json` | **Create** | Proxy `/api/**` → `http://localhost:7777` for dev server |
| `web-ui/angular.json` | **Modify** | Add `proxyConfig` to serve development config |
| `web-ui/src/app/app.config.ts` | **Modify** | Add `provideHttpClient()` |
| `web-ui/src/app/shared/services/tick-data.service.ts` | **Rewrite** | Replace BehaviorSubject mock with real HttpClient calls |
| `web-ui/src/app/pages/data-mgmt/candle-download.ts` | **Modify** | Replace timer simulation with real job-poll loop |

---

## Task 1: Dev proxy + HttpClient bootstrap

**Files:**
- Create: `web-ui/proxy.conf.json`
- Modify: `web-ui/angular.json` lines 46–55 (serve development block)
- Modify: `web-ui/src/app/app.config.ts`

- [ ] **Step 1.1: Create proxy config**

Create `web-ui/proxy.conf.json`:
```json
{
  "/api": {
    "target": "http://localhost:7777",
    "secure": false,
    "changeOrigin": true,
    "logLevel": "info"
  }
}
```

- [ ] **Step 1.2: Wire proxy into angular.json**

In `web-ui/angular.json`, find the `"development"` serve configuration block:
```json
"development": {
  "buildTarget": "DevExtreme-app:build:development"
}
```
Replace with:
```json
"development": {
  "buildTarget": "DevExtreme-app:build:development",
  "proxyConfig": "proxy.conf.json"
}
```

- [ ] **Step 1.3: Add provideHttpClient to app.config.ts**

Replace the entire file with:
```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { routes } from './app.routes';
import {
    AppInfoService,
    AuthGuardService,
    AuthService,
    ScreenService,
} from './shared/services';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withHashLocation()),
    provideHttpClient(),
    AuthGuardService,
    AuthService,
    ScreenService,
    AppInfoService,
  ]
};
```

- [ ] **Step 1.4: Verify**

Start both services and confirm no console errors:
```bash
# Terminal 1 — backend
cd /path/to/project && mvn spring-boot:run

# Terminal 2 — frontend
cd web-ui && npm start
```
Expected: browser opens at `http://localhost:4200`, no `NullInjectorError` for `HttpClient`.

- [ ] **Step 1.5: Commit**
```bash
git add web-ui/proxy.conf.json web-ui/angular.json web-ui/src/app/app.config.ts
git commit -m "feat(web-ui): add HttpClient and dev proxy to backend port 7777"
```

---

## Task 2: Rewrite TickDataService with real API calls

**Files:**
- Rewrite: `web-ui/src/app/shared/services/tick-data.service.ts`

The new service keeps the same public method signatures so no component changes are needed in this task.

Key design decisions:
- `getSymbols()`: uses `/api/symbols` (all tradeable) and flattens CM/FO/COM arrays
- `getAvailableDates(symbol)`: uses `/api/candle-db/{symbol}/dates`; URL-encodes the symbol (e.g. `NSE:SBIN-EQ` → `NSE%3ASBIN-EQ`)
- `getLocalData()`: calls `/api/candle-db/summary` and `/api/candle-db/{symbol}/dates` to build `LocalCandleData[]`
- `addLocalData()`: no-op (data is now server-side; after download completes, the component refreshes via `getAvailableDates`)
- `getDownloadHistory()`: maps summary to `DownloadHistory[]`

- [ ] **Step 2.1: Rewrite tick-data.service.ts**

Replace the entire file with:
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

export interface LocalCandleData {
  id: string;
  symbol: string;
  date: string;
  candleCount: number;
  timeframe: string;
  downloadedAt: string;
  fileSize: string;
}

export interface DownloadHistory {
  symbol: string;
  dataDate: string;
  downloadDate: string;
  candleCount?: number;
  timeframe?: string;
  fileSize?: string;
}

export interface DownloadJobStatus {
  jobId: string;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  progress: string;           // "0/1", "1/1"
  results: Record<string, number>;  // {symbol: daysDownloaded} or -1 on error
  endTime: string | null;
  error?: string;
}

@Injectable({ providedIn: 'root' })
export class TickDataService {

  constructor(private http: HttpClient) {}

  /** All tradeable symbols flattened from symbol registry. */
  getSymbols(): Observable<string[]> {
    return this.http.get<Record<string, unknown>>('/api/symbols').pipe(
      map(res => {
        const flat: string[] = [];
        for (const key of ['CM', 'FO', 'COM']) {
          const arr = res[key.toLowerCase()] ?? res[key];
          if (Array.isArray(arr)) flat.push(...arr as string[]);
        }
        return flat.sort();
      })
    );
  }

  /** ISO date strings already downloaded for a symbol. */
  getAvailableDates(symbol: string): Observable<string[]> {
    const encoded = encodeURIComponent(symbol);
    return this.http.get<string[]>(`/api/candle-db/${encoded}/dates`);
  }

  /**
   * Builds LocalCandleData[] from /api/candle-db/summary.
   * Per-date detail (fileSize, downloadedAt) is not available from the summary
   * endpoint so placeholder values are used.
   */
  getLocalData(): Observable<LocalCandleData[]> {
    return this.http.get<Array<{symbol: string; startDate: string; endDate: string; count: number}>>('/api/candle-db/summary').pipe(
      switchMap(summaries => {
        if (summaries.length === 0) return of([] as LocalCandleData[]);
        const calls = summaries.map(s =>
          this.getAvailableDates(s.symbol).pipe(
            map(dates => dates.map(date => ({
              id: `${s.symbol}-${date}`,
              symbol: s.symbol,
              date,
              candleCount: 0,           // not available from summary
              timeframe: '1min',
              downloadedAt: '—',
              fileSize: '—',
            } as LocalCandleData)))
          )
        );
        return forkJoin(calls).pipe(map(arrays => arrays.flat()));
      })
    );
  }

  getDownloadHistory(): Observable<DownloadHistory[]> {
    return this.getLocalData().pipe(
      map(data => data.map(d => ({
        symbol: d.symbol,
        dataDate: d.date,
        downloadDate: d.downloadedAt,
      })))
    );
  }

  /**
   * Start an async download job for one symbol over a date range.
   * @param symbol Fyers symbol e.g. "NSE:SBIN-EQ"
   * @param from   earliest date string "YYYY-MM-DD"
   * @param to     latest date string "YYYY-MM-DD"
   * @returns jobId string
   */
  startDownloadJob(symbol: string, from: string, to: string): Observable<string> {
    return this.http.post<{jobId: string}>('/api/candle-db/download', {
      symbols: [symbol], from, to
    }).pipe(map(r => r.jobId));
  }

  /** Poll job status. */
  pollJob(jobId: string): Observable<DownloadJobStatus> {
    return this.http.get<DownloadJobStatus>(`/api/candle-db/download/${jobId}`);
  }

  /** No-op — data is server-side. Call getAvailableDates() after job completes. */
  addLocalData(_symbol: string, _date: string, _candleCount: number): void {}

  hasData(symbol: string, date: string): boolean { return false; }
  downloadData(_symbol: string, _dates: Date[]): Observable<boolean> { return of(true); }
}
```

- [ ] **Step 2.2: Verify compile**
```bash
cd web-ui && npm run build -- --configuration development 2>&1 | tail -20
```
Expected: no TypeScript errors related to `TickDataService`.

- [ ] **Step 2.3: Smoke test in browser**

Open Candle Download page. In browser DevTools Network tab: confirm `GET /api/symbols` fires and returns data.

- [ ] **Step 2.4: Commit**
```bash
git add web-ui/src/app/shared/services/tick-data.service.ts
git commit -m "feat(web-ui): wire TickDataService to real backend API"
```

---

## Task 3: Wire download flow to real job API

**Files:**
- Modify: `web-ui/src/app/pages/data-mgmt/candle-download.ts`

**Design:**
- Replace fake `setInterval` timer with a real job poll loop
- `startDownload()` calls `tickDataService.startDownloadJob(symbol, from, to)` → gets `jobId`
- A `setInterval` polling loop fires every 1000 ms calling `tickDataService.pollJob(jobId)`
- While job is `RUNNING`: all `DownloadTask` rows show `downloading` status, progress reflects `symbolsCompleted/total`
- When job becomes `COMPLETED`: call `getAvailableDates(symbol)` to determine which dates now exist, mark each `DownloadTask` as `success` (if date now in available) or `error` (if still missing)
- When job becomes `FAILED`: mark all pending rows as `error` with `job.error` message

- [ ] **Step 3.1: Modify CandleDownload component**

In `web-ui/src/app/pages/data-mgmt/candle-download.ts`, replace the `startDownload()` method, `runTask()` private method, and the class fields/constructor section. The template and styles remain unchanged.

Replace the entire class body (everything from line 278 to end of file) with:
```typescript
export class CandleDownload implements OnInit, OnDestroy {
  symbols: string[] = [];
  selectedSymbol = '';
  availableDatesStr: string[] = [];
  selectedDates: Date[] = [];
  downloadTasks: DownloadTask[] = [];
  isDownloading = false;
  downloadDone = false;

  private pollInterval: ReturnType<typeof setInterval> | null = null;

  constructor(
    private tickDataService: TickDataService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.tickDataService.getSymbols().subscribe(s => this.symbols = s);
  }

  ngOnDestroy() {
    this.stopPoll();
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

    // Build per-date task rows (UI state only)
    this.downloadTasks = this.newDates.map(ds => ({
      date: ds, symbol: this.selectedSymbol,
      status: 'pending' as const, progress: 0,
    }));
    this.isDownloading = true;
    this.downloadDone = false;

    // Compute date range from selected new dates
    const sorted = [...this.newDates].sort();
    const from = sorted[0];
    const to = sorted[sorted.length - 1];

    // Mark all as "downloading" immediately
    this.downloadTasks.forEach(t => { t.status = 'downloading'; t.progress = 10; });
    this.cdr.detectChanges();

    this.tickDataService.startDownloadJob(this.selectedSymbol, from, to).subscribe({
      next: (jobId) => this.startPoll(jobId),
      error: (err) => {
        this.downloadTasks.forEach(t => {
          t.status = 'error';
          t.errorMsg = err?.message ?? 'Failed to start download job';
          t.progress = 0;
        });
        this.isDownloading = false;
        this.downloadDone = true;
        this.cdr.detectChanges();
        notify('Failed to start download: ' + (err?.message ?? 'unknown error'), 'error', 5000);
      }
    });
  }

  private startPoll(jobId: string) {
    this.pollInterval = setInterval(() => {
      this.tickDataService.pollJob(jobId).subscribe({
        next: (job) => this.handleJobStatus(job),
        error: () => {} // transient network error — just retry on next tick
      });
    }, 1000);
  }

  private handleJobStatus(job: any) {
    // Update progress on all downloading rows based on job progress string "N/M"
    const [done, total] = (job.progress as string).split('/').map(Number);
    const pct = total > 0 ? Math.round((done / total) * 80) + 10 : 10; // 10%→90% while running
    this.downloadTasks.forEach(t => {
      if (t.status === 'downloading') t.progress = pct;
    });

    if (job.status === 'COMPLETED' || job.status === 'FAILED') {
      this.stopPoll();
      this.finalizeTasksFromJob(job);
    }
    this.cdr.detectChanges();
  }

  private finalizeTasksFromJob(job: any) {
    // Re-fetch available dates to determine per-date success/failure
    this.tickDataService.getAvailableDates(this.selectedSymbol).subscribe(freshDates => {
      this.availableDatesStr = freshDates;

      if (job.status === 'FAILED') {
        this.downloadTasks.forEach(t => {
          t.status = 'error';
          t.progress = 0;
          t.errorMsg = job.error ?? 'Download job failed';
        });
      } else {
        // COMPLETED: dates now in freshDates = success, still missing = error
        this.downloadTasks.forEach(t => {
          if (freshDates.includes(t.date)) {
            t.status = 'success';
            t.progress = 100;
            // candleCount not provided by summary — show 0 until next enhancement
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
```

Also add the `DownloadJobStatus` import at the top of the file. The existing imports stay the same — `TickDataService` is already imported.

- [ ] **Step 3.2: Verify compile**
```bash
cd web-ui && npm run build -- --configuration development 2>&1 | tail -20
```
Expected: zero TypeScript errors.

- [ ] **Step 3.3: Manual test with backend running**

1. Start Spring Boot (`mvn spring-boot:run`)
2. Navigate to Candle Download page
3. Open DevTools Network tab
4. Select a symbol, pick 2-3 dates
5. Click Download
6. Confirm: `POST /api/candle-db/download` fires, followed by `GET /api/candle-db/download/{jobId}` polling every ~1s
7. Confirm rows show `downloading` status, then resolve to `success` or `error`

- [ ] **Step 3.4: Commit**
```bash
git add web-ui/src/app/pages/data-mgmt/candle-download.ts
git commit -m "feat(candle-download): wire download to real backend job API with polling"
```

---

## Task 4: Wire LocalDataExplorer to real summary

**Files:**
- Verify: `web-ui/src/app/pages/data-mgmt/local-data-explorer.ts` (no changes needed if `getLocalData()` contract is unchanged)

The `LocalDataExplorer` calls `tickDataService.getLocalData()` which now returns real data from `/api/candle-db/summary` + per-symbol dates. The `candleCount`, `downloadedAt`, and `fileSize` fields will show placeholder `0` / `—` values since those aren't available from the summary API. This is acceptable for now.

- [ ] **Step 4.1: Verify LocalDataExplorer works with real data**

1. Navigate to Local Data Explorer page
2. Confirm `GET /api/candle-db/summary` fires in DevTools Network tab
3. Confirm symbols and dates appear in the summary grid
4. Confirm the chart shows data grouped by date

Expected: Grid shows real symbols from the candle database. `Total Candles` column shows `0` (known limitation, to be addressed when the backend summary adds candle counts per date).

- [ ] **Step 4.2: Commit**
```bash
git commit -m "chore(web-ui): verify LocalDataExplorer works with real candle DB data"
```

---

## Task 5: Handle the candleCount limitation (optional enhancement)

**Context:** The `/api/candle-db/summary` endpoint does not return individual candle counts per date. It only returns `{symbol, startDate, endDate, count}` where `count` is number of dates, not total candles.

To get real candle counts without new backend endpoints, load individual day data via `GET /api/candle-db/{symbol}?date={date}` and read the `count` field. This is expensive (N+1) so it's deferred.

**Pragmatic fix for now:** Update `LocalCandleData` display to show "—" in candle count column when `candleCount === 0`, making it obvious data isn't loaded yet vs. genuinely zero.

- [ ] **Step 5.1: Show "—" placeholder in LocalDataExplorer for missing candle counts**

In `web-ui/src/app/pages/data-mgmt/local-data-explorer.ts`, find the `candleCount` column:
```html
<dxi-column dataField="candleCount" caption="Candles" [width]="100" dataType="number"></dxi-column>
```
Replace with a cell template:
```html
<dxi-column dataField="candleCount" caption="Candles" [width]="100" cellTemplate="candleTpl"></dxi-column>

<div *dxTemplate="let c of 'candleTpl'">
  <span *ngIf="c.value > 0">{{ c.value | number }}</span>
  <span *ngIf="c.value === 0" class="muted">—</span>
</div>
```

- [ ] **Step 5.2: Commit**
```bash
git add web-ui/src/app/pages/data-mgmt/local-data-explorer.ts
git commit -m "chore(web-ui): show placeholder for missing candle counts in explorer"
```

---

## Known Limitations & Future Work

| Limitation | Root cause | Future fix |
|---|---|---|
| Individual candle counts not shown | Summary API returns count-of-dates, not candles | Add `GET /api/candle-db/{symbol}/dates?withCounts=true` backend endpoint |
| Download covers full date range (not just selected gaps) | `DownloadTracker.startJob()` takes `from/to` range | Backend: accept `List<LocalDate> dates` instead of range |
| `downloadedAt` and `fileSize` show "—" | Not tracked in current CandleDatabase API | Add metadata file per symbol dir |
| Symbols list from `/api/symbols` may use different format than CandleDB | Registry uses `NSE:SBIN-EQ`; CandleDB dirs use `NSE_SBIN-EQ` | Already handled: `CandleDatabase.unsafeSymbol()` converts on read |
