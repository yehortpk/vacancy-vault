import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../service/auth.service';

/** Lets any signed-in account through; guests are sent to log in and brought back after. */
export const authGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);

  return inject(AuthService).findAccount().pipe(
    map((account) => account
      ? true
      : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }))
  );
};

/** Lets only admins through; a signed-in user without the role lands on the dashboard. */
export const adminGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);

  return inject(AuthService).findAccount().pipe(
    map((account) => {
      if (!account) {
        return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
      }
      return account.role === 'ADMIN' ? true : router.createUrlTree(['/dashboard']);
    })
  );
};
