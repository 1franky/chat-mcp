import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { ChatPage } from './chat-page';

describe('ChatPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ conversationId: 'conversation-1' })) },
        },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows provider/model selectors and deletes an owned conversation with confirmation', () => {
    const fixture = TestBed.createComponent(ChatPage);

    http.expectOne('/api/conversations?query=&page=0&size=30').flush({
      items: [conversation()],
      page: 0,
      size: 30,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne('/api/system/status').flush({
      sprint: 3,
      mode: 'fake',
      mcp: { state: 'UP', protocolVersion: 'test', contractVersion: '1.0.0' },
    });
    const providersRequest = http.expectOne('/api/providers');
    http.expectOne('/api/conversations/conversation-1').flush(conversation());
    http.expectOne('/api/providers/provider-1/models').flush([model()]);
    http.expectOne('/api/conversations/conversation-1/messages').flush([]);
    providersRequest.flush([provider()]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Proveedor');
    expect(fixture.nativeElement.textContent).toContain('fake-chat-v1');
    expect(fixture.nativeElement.textContent).toContain('MCP FAKE UP');

    const deleteButton = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.includes('Eliminar'),
    ) as HTMLButtonElement;
    deleteButton.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirmar borrado');
    deleteButton.click();

    http.expectOne('/api/conversations/conversation-1').flush(null);
  });

  it('keeps the sidebar open when selecting another conversation and deletes it from the sidebar row', () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
      }),
    });

    const fixture = TestBed.createComponent(ChatPage);

    http.expectOne('/api/conversations?query=&page=0&size=30').flush({
      items: [conversation(), conversation('conversation-2', 'Segunda')],
      page: 0,
      size: 30,
      totalElements: 2,
      totalPages: 1,
    });
    http.expectOne('/api/system/status').flush({
      sprint: 3,
      mode: 'fake',
      mcp: { state: 'UP', protocolVersion: 'test', contractVersion: '1.0.0' },
    });
    const providersRequest = http.expectOne('/api/providers');
    http.expectOne('/api/conversations/conversation-1').flush(conversation());
    http.expectOne('/api/providers/provider-1/models').flush([model()]);
    http.expectOne('/api/conversations/conversation-1/messages').flush([]);
    providersRequest.flush([provider()]);
    fixture.detectChanges();

    const shell = fixture.nativeElement.querySelector('.chat-shell') as HTMLElement;
    expect(shell.classList.contains('chat-shell--collapsed')).toBe(false);

    const conversationButtons = [
      ...fixture.nativeElement.querySelectorAll('.conversation-item'),
    ] as HTMLButtonElement[];
    conversationButtons[1].click();
    fixture.detectChanges();
    expect(shell.classList.contains('chat-shell--collapsed')).toBe(false);

    const deleteButtons = [
      ...fixture.nativeElement.querySelectorAll('.conversation-delete'),
    ] as HTMLButtonElement[];
    expect(deleteButtons.length).toBe(2);
    deleteButtons[1].click();
    fixture.detectChanges();
    expect(deleteButtons[1].textContent?.trim()).toBe('✓');
    deleteButtons[1].click();

    http.expectOne('/api/conversations/conversation-2').flush(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.conversation-item').length).toBe(1);
  });
});

function conversation(id = 'conversation-1', title = 'Prueba') {
  return {
    id,
    title,
    providerConnectionId: 'provider-1',
    modelId: 'fake-chat-v1',
    createdAt: '2026-07-15T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  };
}

function provider() {
  return {
    id: 'provider-1',
    displayName: 'Fake local',
    providerType: 'FAKE',
    baseUrl: null,
    region: null,
    modelsPath: null,
    responsesPath: null,
    chatCompletionsPath: null,
    configuredModelId: null,
    defaultModelId: 'fake-chat-v1',
    credentialMasked: null,
    state: 'UP',
    lastErrorCode: null,
    lastTestedAt: null,
    lastModelsSyncedAt: null,
    capabilities: capabilities(),
    createdAt: '2026-07-15T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  };
}

function model() {
  return {
    id: 'model-1',
    modelId: 'fake-chat-v1',
    displayName: 'fake-chat-v1',
    origin: 'DISCOVERED',
    capabilities: capabilities(),
    discoveredAt: '2026-07-15T00:00:00Z',
    lastValidatedAt: null,
  };
}

function capabilities() {
  return {
    chat: 'SUPPORTED',
    streaming: 'SUPPORTED',
    toolCalling: 'UNSUPPORTED',
    structuredOutput: 'UNSUPPORTED',
    vision: 'UNSUPPORTED',
    embeddings: 'UNSUPPORTED',
    modelDiscovery: 'SUPPORTED',
  };
}
