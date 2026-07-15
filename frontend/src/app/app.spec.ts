import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows the bounded Sprint 0 status returned by the backend', () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne('/api/system/status');

    request.flush({
      application: 'AI Data Chat',
      version: '0.1.0-SNAPSHOT',
      sprint: 0,
      mode: 'fake',
      llmProvider: {
        displayName: 'Proveedor determinista de pruebas',
        providerType: 'FAKE',
        state: 'UP',
        fake: true,
      },
      mcp: {
        state: 'UP',
        serverVersion: '0.0.0-fake',
        contractVersion: '1.0.0',
        protocolVersion: '2025-11-25',
        detail: 'Fake local',
        fake: true,
      },
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('AI Data Chat');
    expect(fixture.nativeElement.textContent).toContain('Proveedor determinista de pruebas');
    expect(fixture.nativeElement.textContent).toContain('2025-11-25');
    http.verify();
  });

  it('shows a recoverable state when the backend is unavailable', () => {
    const fixture = TestBed.createComponent(App);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/system/status').flush('unavailable', {
      status: 503,
      statusText: 'Service Unavailable',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Backend no disponible');
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Reintentar');
    http.verify();
  });
});
