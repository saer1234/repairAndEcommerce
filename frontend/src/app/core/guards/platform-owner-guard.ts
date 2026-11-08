import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const platformOwnerGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.signedIn() && auth.user()?.platformRole === 'SUPER_ADMIN') return true;
  return inject(Router).createUrlTree(['/dashboard']);
};
