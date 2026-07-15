import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { finalize, map, Observable, of, shareReplay, switchMap, tap } from 'rxjs';
import { AuthBootstrap, LoginRequest, RegisterRequest, UserAccount } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly bootstrapState = signal<AuthBootstrap | null>(null);
  private bootstrapRequest?: Observable<AuthBootstrap>;

  readonly bootstrap = this.bootstrapState.asReadonly();
  readonly currentUser = computed(() => this.bootstrapState()?.user ?? null);
  readonly authenticated = computed(() => Boolean(this.currentUser()));
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  ensureBootstrap(): Observable<AuthBootstrap> {
    const current = this.bootstrapState();
    if (current) {
      return of(current);
    }
    if (!this.bootstrapRequest) {
      this.bootstrapRequest = this.http.get<AuthBootstrap>('/api/auth/bootstrap').pipe(
        tap((status) => this.bootstrapState.set(status)),
        finalize(() => (this.bootstrapRequest = undefined)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.bootstrapRequest;
  }

  refreshBootstrap(): Observable<AuthBootstrap> {
    this.bootstrapState.set(null);
    return this.ensureBootstrap();
  }

  register(request: RegisterRequest): Observable<UserAccount> {
    return this.http
      .post<UserAccount>('/api/auth/register', request)
      .pipe(switchMap((user) => this.refreshBootstrap().pipe(map(() => user))));
  }

  login(request: LoginRequest): Observable<UserAccount> {
    return this.http
      .post<UserAccount>('/api/auth/login', request)
      .pipe(switchMap((user) => this.refreshBootstrap().pipe(map(() => user))));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', {})
      .pipe(tap(() => this.bootstrapState.set(null)));
  }

  clearLocalSession(): void {
    this.bootstrapState.set(null);
  }
}
