import { Injectable } from '@angular/core';
import { Observable, BehaviorSubject, of } from 'rxjs';

export interface TradingSymbol {
  id: number;
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

@Injectable({ providedIn: 'root' })
export class SymbolService {
  private nextId = 20;

  private readonly EXCHANGES = ['NASDAQ', 'NYSE', 'NSE', 'BSE', 'MCX', 'NCDEX'];
  private readonly SEGMENTS = ['EQ', 'FO', 'CD', 'COMM'];
  private readonly INSTRUMENT_TYPES = ['STOCK', 'INDEX', 'FUTURES', 'OPTIONS', 'ETF', 'CURRENCY'];

  private data: TradingSymbol[] = [
    { id:  1, symbol: 'AAPL',       name: 'Apple Inc.',               exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  2, symbol: 'MSFT',       name: 'Microsoft Corporation',    exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  3, symbol: 'GOOGL',      name: 'Alphabet Inc.',            exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  4, symbol: 'AMZN',       name: 'Amazon.com Inc.',          exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  5, symbol: 'TSLA',       name: 'Tesla Inc.',               exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  6, symbol: 'META',       name: 'Meta Platforms Inc.',      exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  7, symbol: 'NVDA',       name: 'NVIDIA Corporation',       exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  8, symbol: 'NFLX',       name: 'Netflix Inc.',             exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id:  9, symbol: 'ADBE',       name: 'Adobe Inc.',               exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: false },
    { id: 10, symbol: 'PYPL',       name: 'PayPal Holdings Inc.',     exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.01, currency: 'USD', active: false },
    { id: 11, symbol: 'SPY',        name: 'SPDR S&P 500 ETF',         exchange: 'NYSE',   segment: 'EQ',   instrumentType: 'ETF',     lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id: 12, symbol: 'QQQ',        name: 'Invesco QQQ ETF',          exchange: 'NASDAQ', segment: 'EQ',   instrumentType: 'ETF',     lotSize: 1,   tickSize: 0.01, currency: 'USD', active: true  },
    { id: 13, symbol: 'NIFTY50',    name: 'Nifty 50 Index',           exchange: 'NSE',    segment: 'FO',   instrumentType: 'INDEX',   lotSize: 50,  tickSize: 0.05, currency: 'INR', active: true  },
    { id: 14, symbol: 'BANKNIFTY',  name: 'Bank Nifty Index',         exchange: 'NSE',    segment: 'FO',   instrumentType: 'INDEX',   lotSize: 25,  tickSize: 0.05, currency: 'INR', active: true  },
    { id: 15, symbol: 'RELIANCE',   name: 'Reliance Industries Ltd.', exchange: 'NSE',    segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.05, currency: 'INR', active: true  },
    { id: 16, symbol: 'INFY',       name: 'Infosys Ltd.',             exchange: 'NSE',    segment: 'EQ',   instrumentType: 'STOCK',   lotSize: 1,   tickSize: 0.05, currency: 'INR', active: true  },
    { id: 17, symbol: 'CRUDEOIL',   name: 'Crude Oil Futures',        exchange: 'MCX',    segment: 'COMM', instrumentType: 'FUTURES',  lotSize: 100, tickSize: 1.00, currency: 'INR', active: false },
    { id: 18, symbol: 'GOLD',       name: 'Gold Futures',             exchange: 'MCX',    segment: 'COMM', instrumentType: 'FUTURES',  lotSize: 100, tickSize: 1.00, currency: 'INR', active: true  },
    { id: 19, symbol: 'SILVER',     name: 'Silver Futures',           exchange: 'MCX',    segment: 'COMM', instrumentType: 'FUTURES',  lotSize: 30,  tickSize: 1.00, currency: 'INR', active: true  },
  ];

  private symbolsSubject = new BehaviorSubject<TradingSymbol[]>(this.data);

  getSymbols(): Observable<TradingSymbol[]> {
    return this.symbolsSubject.asObservable();
  }

  addSymbol(payload: Omit<TradingSymbol, 'id'>): Observable<TradingSymbol> {
    const entry: TradingSymbol = { ...payload, id: ++this.nextId };
    this.data = [...this.data, entry];
    this.symbolsSubject.next(this.data);
    return of(entry);
  }

  updateSymbol(id: number, payload: Partial<TradingSymbol>): Observable<boolean> {
    this.data = this.data.map(s => s.id === id ? { ...s, ...payload } : s);
    this.symbolsSubject.next(this.data);
    return of(true);
  }

  deleteSymbol(id: number): Observable<boolean> {
    this.data = this.data.filter(s => s.id !== id);
    this.symbolsSubject.next(this.data);
    return of(true);
  }

  getExchanges(): string[] { return this.EXCHANGES; }
  getSegments(): string[] { return this.SEGMENTS; }
  getInstrumentTypes(): string[] { return this.INSTRUMENT_TYPES; }
}
