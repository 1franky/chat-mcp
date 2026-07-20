import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Conversation,
  ConversationMessage,
  ConversationPage,
  CreateConversationRequest,
  GenerationEvent,
  SystemStatus,
} from './chat.models';
import { SseParser } from './sse-parser';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  list(query = '', page = 0, size = 30): Observable<ConversationPage> {
    return this.http.get<ConversationPage>('/api/conversations', {
      params: { query, page, size },
    });
  }

  get(id: string): Observable<Conversation> {
    return this.http.get<Conversation>(`/api/conversations/${id}`);
  }

  create(request: CreateConversationRequest): Observable<Conversation> {
    return this.http.post<Conversation>('/api/conversations', request);
  }

  rename(id: string, title: string): Observable<Conversation> {
    return this.http.put<Conversation>(`/api/conversations/${id}/title`, { title });
  }

  selectModel(id: string, providerConnectionId: string, modelId: string): Observable<Conversation> {
    return this.http.put<Conversation>(`/api/conversations/${id}/selection`, {
      providerConnectionId,
      modelId,
    });
  }

  selectDocuments(id: string, documentIds: string[]): Observable<Conversation> {
    return this.http.put<Conversation>(`/api/conversations/${id}/documents`, { documentIds });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/conversations/${id}`);
  }

  messages(id: string): Observable<ConversationMessage[]> {
    return this.http.get<ConversationMessage[]>(`/api/conversations/${id}/messages`);
  }

  systemStatus(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>('/api/system/status');
  }

  streamMessage(id: string, content: string): Observable<GenerationEvent> {
    return this.stream(`/api/conversations/${id}/messages/stream`, { content });
  }

  regenerate(id: string, messageId: string): Observable<GenerationEvent> {
    return this.stream(`/api/conversations/${id}/messages/${messageId}/regenerate/stream`, {});
  }

  cancel(id: string, generationId: string): Observable<void> {
    return this.http.delete<void>(`/api/conversations/${id}/generations/${generationId}`);
  }

  private stream(url: string, body: unknown): Observable<GenerationEvent> {
    return new Observable<GenerationEvent>((subscriber) => {
      const controller = new AbortController();
      void this.consume(url, body, controller.signal, subscriber);
      return () => controller.abort();
    });
  }

  private async consume(
    url: string,
    body: unknown,
    signal: AbortSignal,
    subscriber: {
      next(value: GenerationEvent): void;
      error(error: unknown): void;
      complete(): void;
    },
  ): Promise<void> {
    try {
      const response = await fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: this.streamHeaders(),
        body: JSON.stringify(body),
        signal,
      });
      if (!response.ok) {
        throw await streamError(response);
      }
      if (!response.body) {
        throw new Error('El navegador no recibio el stream del servidor.');
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parser = new SseParser();
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        for (const event of parser.push(decoder.decode(value, { stream: true }))) {
          subscriber.next(JSON.parse(event.data) as GenerationEvent);
        }
      }
      for (const event of parser.finish()) {
        subscriber.next(JSON.parse(event.data) as GenerationEvent);
      }
      subscriber.complete();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        subscriber.complete();
      } else {
        subscriber.error(error);
      }
    }
  }

  private streamHeaders(): HeadersInit {
    const headers: Record<string, string> = {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    };
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf;
    }
    return headers;
  }
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const prefix = `${name}=`;
  const value = document.cookie
    .split(';')
    .map((entry) => entry.trim())
    .find((entry) => entry.startsWith(prefix));
  return value ? decodeURIComponent(value.slice(prefix.length)) : null;
}

async function streamError(response: Response): Promise<Error> {
  try {
    const problem = (await response.json()) as { detail?: string };
    return new Error(problem.detail || `La generacion fallo (${response.status}).`);
  } catch {
    return new Error(`La generacion fallo (${response.status}).`);
  }
}

export function chatErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && typeof error.error?.detail === 'string') {
    return error.error.detail;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'No fue posible completar la operacion de chat.';
}
