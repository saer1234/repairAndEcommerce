import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { Api } from './api';
import { AuthService } from './auth.service';
import { Company } from './models';

/** Don't depend on a stale/empty company selector after a browser refresh. */
export const clientShopGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const api = inject(Api);
  if (!auth.signedIn()) return router.createUrlTree(['/login']);
  if (auth.user()?.platformRole !== 'CLIENT') return router.createUrlTree(['/dashboard']);
  return api.get<Company[]>('/companies').pipe(
    map(companies => companies.length === 0 ? true : router.createUrlTree(['/dashboard'])),
    catchError(() => of(router.createUrlTree(['/dashboard'])))
  );
};
