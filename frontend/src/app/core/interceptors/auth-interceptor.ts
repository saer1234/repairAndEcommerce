import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const isOurApi = request.url.startsWith(environment.apiUrl + '/');
  const isLogin = request.url === environment.apiUrl + '/auth/login';
  if (isOurApi && !isLogin && auth.signedIn()) {
    request = request.clone({ setHeaders: { Authorization: `Bearer ${auth.token()}` } });
  }
  return next(request).pipe(catchError(error => {
    if (isOurApi && !isLogin && error instanceof HttpErrorResponse &&
        error.status === 401 && auth.token()) {
      auth.logout(); // Expired/revoked/invalid token; don't keep stale credentials.
    }
    return throwError(() => error);
  }));
};
