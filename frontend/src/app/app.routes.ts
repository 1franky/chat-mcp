import { Routes } from '@angular/router';
import { adminGuard, authenticatedGuard, unauthenticatedGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    path: 'auth',
    canActivate: [unauthenticatedGuard],
    loadComponent: () => import('./features/auth/auth-page').then((module) => module.AuthPage),
  },
  {
    path: 'home',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/home/home-page').then((module) => module.HomePage),
  },
  {
    path: 'admin/users',
    canActivate: [authenticatedGuard, adminGuard],
    loadComponent: () =>
      import('./features/admin-users/admin-users-page').then((module) => module.AdminUsersPage),
  },
  { path: '**', redirectTo: 'home' },
];
