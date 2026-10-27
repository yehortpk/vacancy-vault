import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService, REDIRECT_ON_EXPIRED_SESSION } from '../service/auth.service';
import { environment } from '../../environment';

/**
 * Attaches the access token to router calls, and on a 401 refreshes once and retries.
 * If the refresh fails too, the session is over and the user is sent to log in again.
 *
 * The /auth endpoints are left alone: they take no bearer token, and a 401 from login is
 * a wrong password, not an expired session.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);

  if (!request.url.startsWith(environment.router_service_url) || request.url.includes('/auth/')) {
    return next(request);
  }

  const withToken = (req: HttpRequest<unknown>, token: string | null) =>
    token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(withToken(request, authService.getAccessToken())).pipe(
    catchError((error) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !authService.getRefreshToken()) {
        return throwError(() => error);
      }

      return authService.refresh().pipe(
        switchMap((tokens) => next(withToken(request, tokens.accessToken))),
        catchError((refreshError) => {
          if (request.context.get(REDIRECT_ON_EXPIRED_SESSION)) {
            authService.expireSession();
          }
          return throwError(() => refreshError);
        })
      );
    })
  );
};
