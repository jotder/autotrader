import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../../shared/components/toast.component';
import { ConnectionService } from '../services/connection.service';

/**
 * Functional interceptor to handle global API errors and show notifications.
 */
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const connection = inject(ConnectionService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // If we are in mock mode, we shouldn't even be making HTTP calls to backend,
      // but if we do (e.g. initial setup), we skip the error toasts.
      if (connection.isMock()) {
        return throwError(() => error);
      }

      let message = 'An unexpected error occurred';

      if (error.status === 0) {
        message = 'Backend server unreachable — check connection';
      } else if (error.error?.message) {
        message = error.error.message;
      } else if (typeof error.error === 'string') {
        message = error.error;
      } else {
        message = `API Error ${error.status}: ${error.statusText}`;
      }

      // Avoid spamming toasts for repeated polling failures
      // Only show error for non-GET requests or specific critical failures
      if (req.method !== 'GET' || error.status !== 0) {
        toast.error(message);
      }

      return throwError(() => error);
    })
  );
};
