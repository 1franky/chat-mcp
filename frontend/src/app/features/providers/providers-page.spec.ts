import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProvidersPage } from './providers-page';

const capabilities = {
  chat: 'SUPPORTED',
  streaming: 'SUPPORTED',
  toolCalling: 'UNSUPPORTED',
  structuredOutput: 'UNKNOWN',
  vision: 'UNKNOWN',
  embeddings: 'UNSUPPORTED',
  modelDiscovery: 'SUPPORTED',
} as const;

const provider = {
  id: 'f45f50c8-7fee-49ca-8e8c-a40bbf425f73',
  displayName: 'Fake local',
  providerType: 'FAKE',
  baseUrl: null,
  region: null,
  modelsPath: null,
  responsesPath: null,
  chatCompletionsPath: null,
  configuredModelId: null,
  defaultModelId: null,
  credentialMasked: null,
  state: 'UP',
  lastErrorCode: null,
  lastTestedAt: '2026-07-15T00:00:00Z',
  lastModelsSyncedAt: null,
  capabilities,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
} as const;

describe('ProvidersPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProvidersPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('renders safe connection metadata and loads its models', () => {
    const fixture = TestBed.createComponent(ProvidersPage);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne('/api/providers').flush([provider]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Fake local');
    expect(fixture.nativeElement.textContent).not.toContain('apiKey');

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.connection-item');
    button.click();
    http.expectOne(`/api/providers/${provider.id}/models`).flush([
      {
        id: '7724f8ef-c6c4-4fce-a8c5-dd48feeb037c',
        modelId: 'fake-chat-v1',
        displayName: 'Modelo falso determinista',
        origin: 'DISCOVERED',
        capabilities,
        discoveredAt: '2026-07-15T00:00:00Z',
        lastValidatedAt: '2026-07-15T00:00:00Z',
      },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('fake-chat-v1');
    expect(fixture.nativeElement.textContent).toContain('DISCOVERED');
    http.verify();
  });
});
