import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SessionService } from '../service/session.service';

/**
 * Sends the admin back to the login page when the crawler answers 401 in the middle of work,
 * i.e. the session expired or the crawler restarted.
 *
 * The session check and the login itself are left alone: a 401 there is an answer, not a lost
 * session.
 */
export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const sessionService = inject(SessionService);

  return next(request).pipe(
    catchError((error) => {
      const isSessionCall = request.url === '/crawler/session' || request.url === '/login';
      if (error instanceof HttpErrorResponse && error.status === 401 && !isSessionCall) {
        sessionService.expireSession();
      }
      return throwError(() => error);
    })
  );
};
