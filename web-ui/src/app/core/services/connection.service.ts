import { Injectable, signal, computed } from '@angular/core';
import { environment } from '../../../environments/environment';

export type ConnectionMode = 'live' | 'mock';

/**
 * Manages the dynamic connection state between the UI and Backend.
 * Allows switching between real API and Mock data at runtime.
 */
@Injectable({ providedIn: 'root' })
export class ConnectionService {
  // ── State ───────────────────────────────────────────────────
  private readonly MODE_KEY = 'at_connection_mode';
  private readonly URL_KEY = 'at_backend_url';

  readonly mode = signal<ConnectionMode>(this.getInitialMode());
  readonly backendUrl = signal<string>(this.getInitialUrl());

  readonly isMock = computed(() => this.mode() === 'mock');
  readonly isLive = computed(() => this.mode() === 'live');

  // ── Actions ─────────────────────────────────────────────────

  setMode(newMode: ConnectionMode) {
    this.mode.set(newMode);
    localStorage.setItem(this.MODE_KEY, newMode);
    // Reload may be required if we want to reset all state,
    // but for now we just change the signal.
  }

  setBackendUrl(url: string) {
    this.backendUrl.set(url);
    localStorage.setItem(this.URL_KEY, url);
  }

  // ── Helpers ─────────────────────────────────────────────────

  private getInitialMode(): ConnectionMode {
    const saved = localStorage.getItem(this.MODE_KEY) as ConnectionMode;
    if (saved === 'live' || saved === 'mock') return saved;
    return environment.useMocks ? 'mock' : 'live';
  }

  private getInitialUrl(): string {
    return localStorage.getItem(this.URL_KEY) || environment.apiBaseUrl;
  }
}
