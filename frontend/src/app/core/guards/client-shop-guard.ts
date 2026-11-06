import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, catchError, of } from 'rxjs';
import { Api } from '../../api';
import { Company } from '../../core/models/model';
import { AuthService } from '../services/auth.service';

export const clientShopGuard: CanActivateFn = (route, state) => {
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
