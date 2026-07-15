import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { authFailureInterceptor } from './auth-failure.interceptor';

describe('authFailureInterceptor', () => {
  it('clears stale identity and redirects after a protected endpoint returns 401', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authFailureInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    const auth = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    auth.ensureBootstrap().subscribe();
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

    const client = TestBed.inject(HttpClient);
    client.get('/api/protected').subscribe({ error: () => undefined });
    http.expectOne('/api/protected').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.currentUser()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/auth']);
    http.verify();
  });
});
