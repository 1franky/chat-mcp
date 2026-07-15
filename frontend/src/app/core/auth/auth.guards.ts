import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authenticatedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .ensureBootstrap()
    .pipe(map((status) => (status.authenticated ? true : router.createUrlTree(['/auth']))));
};

export const unauthenticatedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .ensureBootstrap()
    .pipe(map((status) => (status.authenticated ? router.createUrlTree(['/home']) : true)));
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth
    .ensureBootstrap()
    .pipe(
      map((status) => (status.user?.role === 'ADMIN' ? true : router.createUrlTree(['/home']))),
    );
};
