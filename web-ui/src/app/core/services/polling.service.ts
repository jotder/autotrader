import { Injectable, OnDestroy } from '@angular/core';
import { Observable, timer, switchMap, shareReplay, Subject, takeUntil, EMPTY, BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Manages polling intervals with auto-pause when browser tab is not visible.
 * Uses Page Visibility API to avoid wasting requests when user switches tabs.
 */
@Injectable({ providedIn: 'root' })
export class PollingService implements OnDestroy {
  private destroy$ = new Subject<void>();
  private visible$ = new BehaviorSubject<boolean>(true);

  constructor() {
    // Listen for tab visibility changes
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', () => {
        this.visible$.next(!document.hidden);
      });
    }
  }

  /**
   * Create a polling observable that emits at the configured interval.
   * Automatically pauses when the tab is not visible.
   * Starts immediately on subscription.
   *
   * @param fetcher Function that returns an Observable of data
   * @param intervalMs Override polling interval (default from environment)
   */
  poll<T>(fetcher: () => Observable<T>, intervalMs?: number): Observable<T> {
    const ms = intervalMs ?? environment.pollingIntervalMs;

    return this.visible$.pipe(
      switchMap(visible => {
        if (!visible) return EMPTY;
        // Use timer(0, ms) to emit the first value immediately
        return timer(0, ms).pipe(
          switchMap(() => fetcher()),
        );
      }),
      shareReplay(1),
      takeUntil(this.destroy$),
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
