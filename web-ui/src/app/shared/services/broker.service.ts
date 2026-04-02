import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type BrokerStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export interface BrokerState {
  status: BrokerStatus;
  errorMsg: string;
}

@Injectable({ providedIn: 'root' })
export class BrokerService {
  private state$ = new BehaviorSubject<BrokerState>({ status: 'disconnected', errorMsg: '' });

  readonly state = this.state$.asObservable();
  get snapshot(): BrokerState { return this.state$.value; }

  connect(accessToken: string): Promise<void> {
    this.state$.next({ status: 'connecting', errorMsg: '' });
    return new Promise(resolve => {
      setTimeout(() => {
        if (accessToken.trim().length >= 8) {
          this.state$.next({ status: 'connected', errorMsg: '' });
        } else {
          this.state$.next({ status: 'error', errorMsg: 'Invalid token — must be at least 8 characters.' });
        }
        resolve();
      }, 1200);
    });
  }

  disconnect() {
    this.state$.next({ status: 'disconnected', errorMsg: '' });
  }
}
