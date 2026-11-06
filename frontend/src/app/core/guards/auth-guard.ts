import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
   const auth = inject(AuthService);
  if (auth.signedIn()) return true;
  auth.clearSession();
  return inject(Router).createUrlTree(['/login']);
};
