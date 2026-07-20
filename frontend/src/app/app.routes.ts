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
    path: 'settings/providers',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/providers/providers-page').then((module) => module.ProvidersPage),
  },
  {
    path: 'settings/mcp',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/mcp/mcp-page').then((module) => module.McpPage),
  },
  {
    path: 'settings/documents',
    canActivate: [authenticatedGuard],
    loadComponent: () =>
      import('./features/documents/documents-page').then((module) => module.DocumentsPage),
  },
  {
    path: 'chat',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/chat/chat-page').then((module) => module.ChatPage),
  },
  {
    path: 'chat/:conversationId',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/chat/chat-page').then((module) => module.ChatPage),
  },
  {
    path: 'admin/users',
    canActivate: [authenticatedGuard, adminGuard],
    loadComponent: () =>
      import('./features/admin-users/admin-users-page').then((module) => module.AdminUsersPage),
  },
  { path: '**', redirectTo: 'home' },
];
