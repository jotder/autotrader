import { Component, Injectable } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';

export interface Toast {
  message: string;
  type: 'success' | 'error' | 'info';
  id: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private counter = 0;
  toasts$ = new Subject<Toast>();
  remove$ = new Subject<number>();

  show(message: string, type: 'success' | 'error' | 'info' = 'info'): void {
    const id = ++this.counter;
    this.toasts$.next({ message, type, id });
    setTimeout(() => this.remove$.next(id), 4000);
  }

  success(message: string): void { this.show(message, 'success'); }
  error(message: string): void { this.show(message, 'error'); }
}

@Component({
  selector: 'at-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container">
      @for (t of toasts; track t.id) {
        <div class="toast" [class]="t.type" (click)="dismiss(t.id)">
          {{ t.message }}
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      bottom: 16px;
      right: 16px;
      z-index: 2000;
      display: flex;
      flex-direction: column;
      gap: 6px;
      max-width: 360px;
    }
    .toast {
      padding: 10px 16px;
      border-radius: 6px;
      font-size: 13px;
      cursor: pointer;
      animation: slide-in 0.2s ease;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
    }
    .toast.success { background: rgba(63, 185, 80, 0.9); color: #000; }
    .toast.error { background: rgba(248, 81, 73, 0.9); color: #fff; }
    .toast.info { background: rgba(0, 188, 212, 0.9); color: #000; }
    @keyframes slide-in {
      from { transform: translateX(100%); opacity: 0; }
      to { transform: translateX(0); opacity: 1; }
    }
  `],
})
export class ToastContainerComponent {
  toasts: Toast[] = [];

  constructor(private svc: ToastService) {
    svc.toasts$.subscribe(t => this.toasts.push(t));
    svc.remove$.subscribe(id => this.toasts = this.toasts.filter(t => t.id !== id));
  }

  dismiss(id: number): void {
    this.toasts = this.toasts.filter(t => t.id !== id);
  }
}
