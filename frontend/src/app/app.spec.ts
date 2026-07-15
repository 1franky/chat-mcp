import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('shows the Sprint 1 identity shell', () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/auth/bootstrap').flush(anonymousBootstrap(true));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AI Data Chat');
    expect(fixture.nativeElement.textContent).toContain('Sprint 1 · Identidad');
    http.verify();
  });

  it('shows a recoverable alert when the backend is unavailable', () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/auth/bootstrap').flush('unavailable', {
      status: 503,
      statusText: 'Service Unavailable',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('backend no esta disponible');
    http.verify();
  });
});

function anonymousBootstrap(bootstrapRequired: boolean) {
  return {
    bootstrapRequired,
    registrationOpen: true,
    authenticated: false,
    user: null,
  };
}
