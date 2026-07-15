import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    localStorage.clear();
    delete document.documentElement.dataset['theme'];
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('shows the Sprint 3 chat shell and theme control', () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/auth/bootstrap').flush(anonymousBootstrap(true));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AI Data Chat');
    expect(fixture.nativeElement.textContent).toContain('Sprint 3 · Chat');
    expect(fixture.nativeElement.textContent).toContain('Oscuro');

    const toggle = fixture.nativeElement.querySelector('.theme-toggle') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();
    expect(document.documentElement.dataset['theme']).toBe('dark');
    expect(toggle.getAttribute('aria-label')).toBe('Activar modo claro');
    expect(localStorage.getItem('ai-data-chat-theme')).toBe('dark');
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
