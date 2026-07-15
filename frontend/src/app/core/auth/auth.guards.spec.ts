import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable } from 'rxjs';
import { adminGuard, authenticatedGuard } from './auth.guards';

describe('identity route guards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('redirects anonymous users to login', async () => {
    const result = TestBed.runInInjectionContext(() =>
      authenticatedGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    const decision = firstValueFrom(result);

    http.expectOne('/api/auth/bootstrap').flush({
      bootstrapRequired: false,
      registrationOpen: true,
      authenticated: false,
      user: null,
    });

    expect(((await decision) as UrlTree).toString()).toBe('/auth');
  });

  it('redirects regular users away from administration', async () => {
    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    const decision = firstValueFrom(result);

    http.expectOne('/api/auth/bootstrap').flush({
      bootstrapRequired: false,
      registrationOpen: true,
      authenticated: true,
      user: {
        id: '6dc26004-382d-4f0a-a838-8614e32da62b',
        email: 'user@example.test',
        displayName: 'Test User',
        role: 'USER',
        active: true,
        createdAt: '2026-07-15T00:00:00Z',
        updatedAt: '2026-07-15T00:00:00Z',
      },
    });

    expect(((await decision) as UrlTree).toString()).toBe('/home');
  });
});
