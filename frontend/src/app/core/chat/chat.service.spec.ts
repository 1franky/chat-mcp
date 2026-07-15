import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom, toArray } from 'rxjs';
import { ChatService } from './chat.service';

describe('ChatService', () => {
  let service: ChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    http.verify();
  });

  it('parses a streamed generation response', async () => {
    const payload = {
      type: 'complete',
      generationId: 'generation-1',
      userMessage: null,
      assistantMessage: null,
      delta: null,
      errorCode: null,
      retryable: false,
    };
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          new TextEncoder().encode(`event: complete\ndata: ${JSON.stringify(payload)}\n\n`),
        );
        controller.close();
      },
    });
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } }),
    );

    const events = await firstValueFrom(
      service.streamMessage('conversation-1', 'hola').pipe(toArray()),
    );

    expect(events).toEqual([payload]);
    expect(fetch).toHaveBeenCalledWith(
      '/api/conversations/conversation-1/messages/stream',
      expect.objectContaining({ method: 'POST', credentials: 'same-origin' }),
    );
  });

  it('calls the explicit cancellation endpoint', () => {
    service.cancel('conversation-1', 'generation-1').subscribe();

    const request = http.expectOne('/api/conversations/conversation-1/generations/generation-1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
