import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { SessionService } from '../service/session.service';

/** Lets a logged-in admin through; anyone else is sent to the login page. */
export const sessionGuard: CanActivateFn = () => {
  const router = inject(Router);

  return inject(SessionService).findSession().pipe(
    map((admin) => admin ? true : router.createUrlTree(['/sign-in']))
  );
};
