import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map, of, switchMap, catchError, throwError } from 'rxjs';

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
  progress: string;
  results: Record<string, number>;
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
        for (const key of ['cm', 'fo', 'com']) {
          const arr = res[key];
          if (Array.isArray(arr)) flat.push(...arr as string[]);
        }
        return flat.sort();
      }),
      catchError(() => of([]))
    );
  }

  /** ISO date strings already downloaded for a symbol. */
  getAvailableDates(symbol: string): Observable<string[]> {
    const encoded = encodeURIComponent(symbol);
    return this.http.get<string[]>(`/api/candle-db/${encoded}/dates`).pipe(
      catchError(() => of([]))
    );
  }

  /**
   * Builds LocalCandleData[] from summary + per-symbol date lists.
   * candleCount, downloadedAt, fileSize are placeholders (not in summary API).
   */
  getLocalData(): Observable<LocalCandleData[]> {
    return this.http.get<Array<{symbol: string; startDate: string; endDate: string; count: number}>>('/api/candle-db/summary').pipe(
      switchMap(summaries => {
        if (summaries.length === 0) return of([] as LocalCandleData[]);
        const calls = summaries.map(s =>
          this.getAvailableDates(s.symbol).pipe(
            catchError(() => of([] as string[])),
            map(dates => dates.map(date => ({
              id: `${s.symbol}-${date}`,
              symbol: s.symbol,
              date,
              candleCount: 0,
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

  startDownloadJob(symbol: string, from: string, to: string): Observable<string> {
    return this.http.post<{jobId: string}>('/api/candle-db/download', {
      symbols: [symbol], from, to
    }).pipe(
      map(r => r.jobId),
      catchError(err => throwError(() => err))
    );
  }

  pollJob(jobId: string): Observable<DownloadJobStatus> {
    return this.http.get<DownloadJobStatus>(`/api/candle-db/download/${jobId}`).pipe(
      catchError(err => throwError(() => err))
    );
  }

  addLocalData(_symbol: string, _date: string, _candleCount: number): void {}
}
