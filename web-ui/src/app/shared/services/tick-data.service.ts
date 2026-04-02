import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject, of } from 'rxjs';

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

@Injectable({ providedIn: 'root' })
export class TickDataService {
  private readonly symbols = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA',
    'META', 'NVDA', 'PYPL', 'ADBE', 'NFLX',
    'NIFTY50', 'BANKNIFTY', 'RELIANCE', 'GOLD', 'SPY'
  ];

  private localDataSubject = new BehaviorSubject<LocalCandleData[]>([
    { id: 'AAPL-2026-03-25', symbol: 'AAPL', date: '2026-03-25', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-26 09:00', fileSize: '124 KB' },
    { id: 'AAPL-2026-03-24', symbol: 'AAPL', date: '2026-03-24', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-26 09:01', fileSize: '122 KB' },
    { id: 'AAPL-2026-03-21', symbol: 'AAPL', date: '2026-03-21', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-22 10:15', fileSize: '119 KB' },
    { id: 'AAPL-2026-03-20', symbol: 'AAPL', date: '2026-03-20', candleCount: 385, timeframe: '1min', downloadedAt: '2026-03-22 10:16', fileSize: '118 KB' },
    { id: 'MSFT-2026-03-25', symbol: 'MSFT', date: '2026-03-25', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-27 08:45', fileSize: '118 KB' },
    { id: 'MSFT-2026-03-24', symbol: 'MSFT', date: '2026-03-24', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-27 08:46', fileSize: '115 KB' },
    { id: 'MSFT-2026-03-21', symbol: 'MSFT', date: '2026-03-21', candleCount: 388, timeframe: '1min', downloadedAt: '2026-03-27 08:47', fileSize: '113 KB' },
    { id: 'TSLA-2026-03-25', symbol: 'TSLA', date: '2026-03-25', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-26 11:30', fileSize: '132 KB' },
    { id: 'TSLA-2026-03-20', symbol: 'TSLA', date: '2026-03-20', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-21 10:00', fileSize: '128 KB' },
    { id: 'NVDA-2026-03-25', symbol: 'NVDA', date: '2026-03-25', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-26 14:00', fileSize: '130 KB' },
    { id: 'NVDA-2026-03-24', symbol: 'NVDA', date: '2026-03-24', candleCount: 388, timeframe: '1min', downloadedAt: '2026-03-26 14:01', fileSize: '127 KB' },
    { id: 'GOOGL-2026-03-25', symbol: 'GOOGL', date: '2026-03-25', candleCount: 390, timeframe: '1min', downloadedAt: '2026-03-26 16:00', fileSize: '121 KB' },
    { id: 'GOOGL-2026-03-24', symbol: 'GOOGL', date: '2026-03-24', candleCount: 387, timeframe: '1min', downloadedAt: '2026-03-26 16:01', fileSize: '120 KB' },
    { id: 'NIFTY50-2026-03-25', symbol: 'NIFTY50', date: '2026-03-25', candleCount: 375, timeframe: '1min', downloadedAt: '2026-03-26 06:15', fileSize: '98 KB' },
    { id: 'NIFTY50-2026-03-24', symbol: 'NIFTY50', date: '2026-03-24', candleCount: 375, timeframe: '1min', downloadedAt: '2026-03-25 06:10', fileSize: '97 KB' },
    { id: 'GOLD-2026-03-25', symbol: 'GOLD', date: '2026-03-25', candleCount: 1440, timeframe: '1min', downloadedAt: '2026-03-26 10:00', fileSize: '312 KB' },
  ]);

  getSymbols(): Observable<string[]> {
    return of(this.symbols);
  }

  getLocalData(): Observable<LocalCandleData[]> {
    return this.localDataSubject.asObservable();
  }

  getAvailableDates(symbol: string): Observable<string[]> {
    const dates = this.localDataSubject.value
      .filter(d => d.symbol === symbol)
      .map(d => d.date);
    return of(dates);
  }

  hasData(symbol: string, date: string): boolean {
    return !!this.localDataSubject.value.find(d => d.symbol === symbol && d.date === date);
  }

  addLocalData(symbol: string, date: string, candleCount: number): void {
    const current = this.localDataSubject.value;
    const id = `${symbol}-${date}`;
    if (!current.find(d => d.id === id)) {
      const entry: LocalCandleData = {
        id,
        symbol,
        date,
        candleCount,
        timeframe: '1min',
        downloadedAt: new Date().toISOString().replace('T', ' ').substring(0, 16),
        fileSize: `${Math.floor(Math.random() * 60) + 90} KB`,
      };
      this.localDataSubject.next([...current, entry]);
    }
  }

  getDownloadHistory(): Observable<DownloadHistory[]> {
    return of(this.localDataSubject.value.map(d => ({
      symbol: d.symbol,
      dataDate: d.date,
      downloadDate: d.downloadedAt.split(' ')[0],
      candleCount: d.candleCount,
      timeframe: d.timeframe,
      fileSize: d.fileSize,
    })));
  }

  downloadData(symbol: string, dates: Date[]): Observable<boolean> {
    dates.forEach(date => {
      const y = date.getFullYear();
      const m = String(date.getMonth() + 1).padStart(2, '0');
      const d = String(date.getDate()).padStart(2, '0');
      this.addLocalData(symbol, `${y}-${m}-${d}`, Math.floor(Math.random() * 50) + 370);
    });
    return of(true);
  }
}
