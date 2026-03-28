import { Injectable, OnDestroy } from '@angular/core';
import { forkJoin, catchError, of, Subscription, interval } from 'rxjs';
import { ApiService } from './api.service';
import { PollingService } from './polling.service';
import { GlobalStateService } from './global-state.service';

/**
 * Orchestrates high-frequency data feeds from the backend.
 * Feeds data into the GlobalStateService at regular intervals.
 */
@Injectable({ providedIn: 'root' })
export class DataFeedService implements OnDestroy {
  private subs = new Subscription();

  constructor(
    private api: ApiService,
    private polling: PollingService,
    private state: GlobalStateService
  ) {}

  /**
   * Starts all background data feeds.
   * Typically called once at application startup.
   */
  startFeeds() {
    this.startSystemStatusFeed();
    this.startTradingStateFeed();
    this.startClockPulse();
  }

  /**
   * Pulses the 'now' signal every second to drive computed staleness.
   */
  private startClockPulse() {
    const clock$ = interval(1000);
    this.subs.add(
      clock$.subscribe(() => this.state.now.set(Date.now()))
    );
  }

  /**
   * Feed for system-level status (Dashboard requirements).
   */
  private startSystemStatusFeed() {
    const systemFeed$ = this.polling.poll(() =>
      forkJoin({
        status: this.api.getStatus().pipe(catchError(() => of(null))),
        risk: this.api.getRisk().pipe(catchError(() => of(null))),
        health: this.api.getHealth().pipe(catchError(() => of(null))),
        anomaly: this.api.getAnomalyStatus().pipe(catchError(() => of(null))),
        cb: this.api.getCircuitBreakerStatus().pipe(catchError(() => of(null))),
        token: this.api.getTokenStatus().pipe(catchError(() => of(null))),
      })
    );

    this.subs.add(
      systemFeed$.subscribe(data => {
        this.state.updateState({
          status: data.status || undefined,
          risk: data.risk || undefined,
          health: data.health || undefined,
          anomaly: data.anomaly || undefined,
          circuitBreaker: data.cb || undefined,
          token: data.token || undefined,
        });
      })
    );
  }

  /**
   * Feed for active trading data (Positions, Orders).
   */
  private startTradingStateFeed() {
    const tradingFeed$ = this.polling.poll(() =>
      forkJoin({
        positions: this.api.getPositions().pipe(catchError(() => of([]))),
        orders: this.api.getOrders().pipe(catchError(() => of({ active: [], completed: [], activeCount: 0 }))),
      })
    );

    this.subs.add(
      tradingFeed$.subscribe(data => {
        this.state.updateState({
          positions: data.positions,
          activeOrders: data.orders?.active || [],
        });
      })
    );
  }

  ngOnDestroy() {
    this.subs.unsubscribe();
  }
}
