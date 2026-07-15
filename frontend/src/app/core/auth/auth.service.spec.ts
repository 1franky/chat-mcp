import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthBootstrap, UserAccount } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the first-administrator bootstrap state', () => {
    auth.ensureBootstrap().subscribe();
    http.expectOne('/api/auth/bootstrap').flush(bootstrap(null, true));

    expect(auth.bootstrap()?.bootstrapRequired).toBe(true);
    expect(auth.authenticated()).toBe(false);
  });

  it('registers and refreshes the server-side session state', () => {
    const created = user('ADMIN');
    auth
      .register({
        email: created.email,
        displayName: created.displayName,
        password: 'correct-horse-battery-staple',
      })
      .subscribe();

    http.expectOne('/api/auth/register').flush(created);
    http.expectOne('/api/auth/bootstrap').flush(bootstrap(created, false));

    expect(auth.currentUser()?.role).toBe('ADMIN');
  });

  it('logs in without storing a token in browser storage', () => {
    const loggedIn = user('USER');
    auth.login({ email: loggedIn.email, password: 'correct-horse-battery-staple' }).subscribe();

    http.expectOne('/api/auth/login').flush(loggedIn);
    http.expectOne('/api/auth/bootstrap').flush(bootstrap(loggedIn, false));

    expect(auth.authenticated()).toBe(true);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});

function bootstrap(userAccount: UserAccount | null, bootstrapRequired: boolean): AuthBootstrap {
  return {
    bootstrapRequired,
    registrationOpen: true,
    authenticated: userAccount !== null,
    user: userAccount,
  };
}

function user(role: 'ADMIN' | 'USER'): UserAccount {
  return {
    id: '6dc26004-382d-4f0a-a838-8614e32da62b',
    email: 'user@example.test',
    displayName: 'Test User',
    role,
    active: true,
    createdAt: '2026-07-15T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  };
}
