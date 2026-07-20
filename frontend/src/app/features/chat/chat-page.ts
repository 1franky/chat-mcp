import {
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import {
  Conversation,
  ConversationMessage,
  GenerationEvent,
  McpSummary,
  ToolCallView,
} from '../../core/chat/chat.models';
import { chatErrorMessage, ChatService } from '../../core/chat/chat.service';
import { renderSafeMarkdown } from '../../core/chat/safe-markdown';
import { DocumentView } from '../../core/documents/documents.models';
import { DocumentsService } from '../../core/documents/documents.service';
import {
  CapabilityAvailability,
  ProviderConnection,
  ProviderModel,
} from '../../core/providers/provider.models';
import { ProviderService } from '../../core/providers/provider.service';

@Component({
  selector: 'app-chat-page',
  imports: [MatButtonModule, MatProgressSpinnerModule, ReactiveFormsModule, RouterLink],
  templateUrl: './chat-page.html',
  styleUrl: './chat-page.scss',
})
export class ChatPage {
  private readonly chat = inject(ChatService);
  private readonly providerApi = inject(ProviderService);
  private readonly documentsApi = inject(DocumentsService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('messageViewport') private messageViewport?: ElementRef<HTMLElement>;

  protected readonly conversations = signal<Conversation[]>([]);
  protected readonly activeConversation = signal<Conversation | null>(null);
  protected readonly messages = signal<ConversationMessage[]>([]);
  protected readonly providers = signal<ProviderConnection[]>([]);
  protected readonly models = signal<ProviderModel[]>([]);
  protected readonly documents = signal<DocumentView[]>([]);
  protected readonly documentsPanelOpen = signal(false);
  protected readonly mcp = signal<McpSummary | null>(null);
  protected readonly integrationMode = signal('fake');
  protected readonly loadingConversations = signal(true);
  protected readonly loadingConversation = signal(false);
  protected readonly loadingModels = signal(false);
  protected readonly generating = signal(false);
  protected readonly stopping = signal(false);
  protected readonly activeGenerationId = signal<string | null>(null);
  protected readonly sidebarCollapsed = signal(this.isMobileViewport());
  protected readonly renaming = signal(false);
  protected readonly deleteConfirmation = signal(false);
  protected readonly pendingDeleteId = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly notice = signal('');

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly renameControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(160)],
  });
  protected readonly form = this.formBuilder.nonNullable.group({
    providerConnectionId: ['', Validators.required],
    modelId: ['', Validators.required],
    content: ['', [Validators.required, Validators.maxLength(32000)]],
  });

  protected readonly selectedProvider = computed(() =>
    this.providers().find(
      (provider) => provider.id === this.form.controls.providerConnectionId.value,
    ),
  );
  protected readonly selectedModel = computed(() =>
    this.models().find((model) => model.modelId === this.form.controls.modelId.value),
  );

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((query) => this.loadConversations(query));
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = params.get('conversationId');
      if (id) {
        this.loadConversation(id);
      } else {
        this.activeConversation.set(null);
        this.messages.set([]);
        this.renaming.set(false);
        this.deleteConfirmation.set(false);
      }
    });
    this.loadProviders();
    this.loadDocuments();
    this.loadConversations('');
    this.chat
      .systemStatus()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (status) => {
          this.integrationMode.set(status.mode);
          this.mcp.set(status.mcp);
        },
      });
  }

  protected newConversation(): void {
    if (this.generating()) {
      return;
    }
    this.clearMessages();
    this.activeConversation.set(null);
    this.messages.set([]);
    this.form.controls.content.reset();
    this.pendingDeleteId.set(null);
    if (this.isMobileViewport()) {
      this.sidebarCollapsed.set(true);
    }
    void this.router.navigate(['/chat']);
  }

  protected openConversation(conversation: Conversation): void {
    if (this.generating()) {
      return;
    }
    this.pendingDeleteId.set(null);
    if (this.isMobileViewport()) {
      this.sidebarCollapsed.set(true);
    }
    void this.router.navigate(['/chat', conversation.id]);
  }

  protected deleteFromSidebar(conversation: Conversation, event: Event): void {
    event.stopPropagation();
    if (this.generating()) {
      return;
    }
    if (this.pendingDeleteId() !== conversation.id) {
      this.pendingDeleteId.set(conversation.id);
      return;
    }
    this.pendingDeleteId.set(null);
    this.chat
      .delete(conversation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.conversations.update((items) => items.filter((item) => item.id !== conversation.id));
          if (this.activeConversation()?.id === conversation.id) {
            this.newConversation();
          }
        },
        error: (error) => this.fail(error),
      });
  }

  private isMobileViewport(): boolean {
    return typeof matchMedia === 'function' && matchMedia('(max-width: 860px)').matches;
  }

  protected providerChanged(): void {
    const providerId = this.form.controls.providerConnectionId.value;
    const provider = this.providers().find((candidate) => candidate.id === providerId);
    this.form.controls.modelId.setValue('');
    if (!provider) {
      this.models.set([]);
      return;
    }
    this.loadModels(provider.id, provider.defaultModelId ?? undefined, true);
  }

  protected modelChanged(): void {
    this.persistSelection();
  }

  protected isDocumentSelected(documentId: string): boolean {
    return this.activeConversation()?.selectedDocumentIds.includes(documentId) ?? false;
  }

  protected toggleDocument(documentId: string): void {
    const conversation = this.activeConversation();
    if (!conversation) {
      return;
    }
    const next = conversation.selectedDocumentIds.includes(documentId)
      ? conversation.selectedDocumentIds.filter((id) => id !== documentId)
      : [...conversation.selectedDocumentIds, documentId];
    this.chat
      .selectDocuments(conversation.id, next)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.activeConversation.set(updated);
          this.upsertConversation(updated);
        },
        error: (error) => this.fail(error),
      });
  }

  protected send(): void {
    this.form.controls.content.markAsTouched();
    this.form.controls.providerConnectionId.markAsTouched();
    this.form.controls.modelId.markAsTouched();
    if (this.form.invalid || this.generating()) {
      return;
    }
    const value = this.form.getRawValue();
    const content = value.content.trim();
    if (!content) {
      return;
    }
    this.clearMessages();
    const conversation = this.activeConversation();
    if (!conversation) {
      this.chat
        .create({
          title: titleFrom(content),
          providerConnectionId: value.providerConnectionId,
          modelId: value.modelId,
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (created) => {
            this.activeConversation.set(created);
            this.upsertConversation(created);
            void this.router.navigate(['/chat', created.id]);
            this.beginStream(created, content);
          },
          error: (error) => this.fail(error),
        });
      return;
    }
    if (
      conversation.providerConnectionId !== value.providerConnectionId ||
      conversation.modelId !== value.modelId
    ) {
      this.chat
        .selectModel(conversation.id, value.providerConnectionId, value.modelId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (updated) => {
            this.activeConversation.set(updated);
            this.upsertConversation(updated);
            this.beginStream(updated, content);
          },
          error: (error) => this.fail(error),
        });
    } else {
      this.beginStream(conversation, content);
    }
  }

  protected composerKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  protected stop(): void {
    const conversation = this.activeConversation();
    const generationId = this.activeGenerationId();
    if (!conversation || !generationId || this.stopping()) {
      return;
    }
    this.stopping.set(true);
    this.chat
      .cancel(conversation.id, generationId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.stopping.set(false),
        error: (error) => {
          this.stopping.set(false);
          this.fail(error);
        },
      });
  }

  protected regenerate(message: ConversationMessage): void {
    const conversation = this.activeConversation();
    if (!conversation || this.generating()) {
      return;
    }
    this.clearMessages();
    this.generating.set(true);
    this.chat
      .regenerate(conversation.id, message.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => this.handleEvent(event),
        error: (error) => this.failGeneration(error),
        complete: () => this.finishGeneration(),
      });
  }

  protected startRename(): void {
    const conversation = this.activeConversation();
    if (!conversation) {
      return;
    }
    this.renameControl.setValue(conversation.title);
    this.renaming.set(true);
  }

  protected saveRename(): void {
    const conversation = this.activeConversation();
    this.renameControl.markAsTouched();
    if (!conversation || this.renameControl.invalid) {
      return;
    }
    this.chat
      .rename(conversation.id, this.renameControl.value.trim())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.activeConversation.set(updated);
          this.upsertConversation(updated);
          this.renaming.set(false);
        },
        error: (error) => this.fail(error),
      });
  }

  protected removeConversation(): void {
    const conversation = this.activeConversation();
    if (!conversation || this.generating()) {
      return;
    }
    if (!this.deleteConfirmation()) {
      this.deleteConfirmation.set(true);
      return;
    }
    this.chat
      .delete(conversation.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.conversations.update((items) => items.filter((item) => item.id !== conversation.id));
          this.newConversation();
        },
        error: (error) => this.fail(error),
      });
  }

  protected copy(message: ConversationMessage): void {
    void navigator.clipboard
      .writeText(message.content)
      .then(() => this.notice.set('Respuesta copiada.'))
      .catch(() => this.error.set('No fue posible copiar la respuesta.'));
  }

  protected markdown(content: string): string {
    return renderSafeMarkdown(content);
  }

  protected capabilityEntries(): ReadonlyArray<{
    label: string;
    value: CapabilityAvailability;
  }> {
    const profile = this.selectedModel()?.capabilities ?? this.selectedProvider()?.capabilities;
    return profile
      ? [
          { label: 'Chat', value: profile.chat },
          { label: 'Stream', value: profile.streaming },
          { label: 'Tools', value: profile.toolCalling },
        ]
      : [];
  }

  protected statusLabel(status: ConversationMessage['status']): string {
    return {
      STREAMING: 'Generando',
      COMPLETED: 'Completado',
      CANCELLED: 'Cancelado',
      FAILED: 'Error',
    }[status];
  }

  protected toolCallStatusLabel(status: ToolCallView['status']): string {
    return {
      PENDING: 'Pendiente',
      RUNNING: 'Ejecutando…',
      COMPLETED: 'Completado',
      FAILED: 'Error',
      BLOCKED: 'Bloqueado',
      TIMEOUT: 'Tiempo agotado',
    }[status];
  }

  protected toolCallPreview(value: Record<string, unknown> | null): string {
    if (!value || Object.keys(value).length === 0) {
      return '—';
    }
    const text = JSON.stringify(value);
    return text.length > 160 ? text.slice(0, 160) + '…' : text;
  }

  private beginStream(conversation: Conversation, content: string): void {
    this.form.controls.content.reset();
    this.generating.set(true);
    this.chat
      .streamMessage(conversation.id, content)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => this.handleEvent(event),
        error: (error) => this.failGeneration(error),
        complete: () => this.finishGeneration(),
      });
  }

  private handleEvent(event: GenerationEvent): void {
    if (event.type === 'generation') {
      this.activeGenerationId.set(event.generationId);
      if (event.userMessage) {
        this.upsertMessage(event.userMessage);
      }
      if (event.assistantMessage) {
        this.upsertMessage(event.assistantMessage);
      }
    } else if (event.assistantMessage) {
      this.upsertMessage(event.assistantMessage);
    }
    if (event.type === 'tool_call' || event.type === 'tool_result') {
      if (event.assistantMessage && event.toolCall) {
        this.upsertToolCall(event.assistantMessage.id, event.toolCall);
      }
    }
    if (event.type === 'error') {
      this.error.set(
        event.retryable
          ? 'El proveedor interrumpio la respuesta. Puedes reintentar.'
          : 'El proveedor no pudo completar la respuesta.',
      );
    }
    if (event.type === 'cancelled') {
      this.notice.set('Generacion cancelada; se conservo la respuesta parcial.');
    }
    if (event.type === 'complete' || event.type === 'cancelled' || event.type === 'error') {
      this.finishGeneration();
      this.loadConversations(this.searchControl.value);
    }
    if (event.type === 'complete' || event.type === 'cancelled') {
      const conversation = this.activeConversation();
      if (conversation) {
        this.loadMessages(conversation.id);
      }
    }
    this.scrollToBottom();
  }

  private finishGeneration(): void {
    this.generating.set(false);
    this.stopping.set(false);
    this.activeGenerationId.set(null);
  }

  private failGeneration(error: unknown): void {
    this.finishGeneration();
    this.fail(error);
    const conversation = this.activeConversation();
    if (conversation) {
      this.loadMessages(conversation.id);
    }
  }

  private loadConversations(query: string): void {
    this.loadingConversations.set(true);
    this.chat
      .list(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.conversations.set(page.items);
          this.loadingConversations.set(false);
        },
        error: (error) => {
          this.loadingConversations.set(false);
          this.fail(error);
        },
      });
  }

  private loadConversation(id: string): void {
    if (this.activeConversation()?.id === id && this.generating()) {
      return;
    }
    this.loadingConversation.set(true);
    this.chat
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (conversation) => {
          this.activeConversation.set(conversation);
          this.renameControl.setValue(conversation.title);
          this.form.patchValue({
            providerConnectionId: conversation.providerConnectionId ?? '',
            modelId: conversation.modelId,
          });
          if (conversation.providerConnectionId) {
            this.loadModels(conversation.providerConnectionId, conversation.modelId, false);
          }
          this.loadMessages(conversation.id);
        },
        error: (error) => {
          this.loadingConversation.set(false);
          this.fail(error);
        },
      });
  }

  private loadMessages(id: string): void {
    this.chat
      .messages(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (messages) => {
          this.messages.set(messages);
          this.loadingConversation.set(false);
          this.scrollToBottom();
        },
        error: (error) => {
          this.loadingConversation.set(false);
          this.fail(error);
        },
      });
  }

  private loadProviders(): void {
    this.providerApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (providers) => {
          this.providers.set(providers);
          if (!this.form.controls.providerConnectionId.value && providers.length) {
            const preferred = providers.find((provider) => provider.defaultModelId) ?? providers[0];
            this.form.controls.providerConnectionId.setValue(preferred.id);
            this.loadModels(preferred.id, preferred.defaultModelId ?? undefined, false);
          }
        },
        error: (error) => this.fail(error),
      });
  }

  private loadDocuments(): void {
    this.documentsApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => this.documents.set(page.items),
        error: (error) => this.fail(error),
      });
  }

  private loadModels(providerId: string, selected?: string, persist = false): void {
    this.loadingModels.set(true);
    this.providerApi
      .listModels(providerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (models) => {
          this.models.set(models);
          this.loadingModels.set(false);
          const model = models.find((item) => item.modelId === selected) ?? models[0];
          this.form.controls.modelId.setValue(model?.modelId ?? '');
          if (persist && model) {
            this.persistSelection();
          }
        },
        error: (error) => {
          this.loadingModels.set(false);
          this.fail(error);
        },
      });
  }

  private persistSelection(): void {
    const conversation = this.activeConversation();
    const providerId = this.form.controls.providerConnectionId.value;
    const modelId = this.form.controls.modelId.value;
    if (!conversation || !providerId || !modelId || this.generating()) {
      return;
    }
    this.chat
      .selectModel(conversation.id, providerId, modelId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.activeConversation.set(updated);
          this.upsertConversation(updated);
          this.notice.set('Modelo de la conversacion actualizado.');
        },
        error: (error) => this.fail(error),
      });
  }

  private upsertConversation(conversation: Conversation): void {
    this.conversations.update((items) => [
      conversation,
      ...items.filter((item) => item.id !== conversation.id),
    ]);
  }

  private upsertMessage(message: ConversationMessage): void {
    this.messages.update((items) => {
      const previous = items.find((item) => item.id === message.id);
      const merged =
        message.toolCalls.length === 0 && previous && previous.toolCalls.length > 0
          ? { ...message, toolCalls: previous.toolCalls }
          : message;
      return [...items.filter((item) => item.id !== message.id), merged].sort(
        (left, right) => left.position - right.position,
      );
    });
  }

  private upsertToolCall(messageId: string, toolCall: ToolCallView): void {
    this.messages.update((items) =>
      items.map((item) => {
        if (item.id !== messageId) {
          return item;
        }
        const toolCalls = [
          ...item.toolCalls.filter((existing) => existing.id !== toolCall.id),
          toolCall,
        ].sort(
          (left, right) =>
            left.generationRound - right.generationRound || left.sequence - right.sequence,
        );
        return { ...item, toolCalls };
      }),
    );
  }

  private scrollToBottom(): void {
    queueMicrotask(() => {
      const element = this.messageViewport?.nativeElement;
      if (element) {
        element.scrollTop = element.scrollHeight;
      }
    });
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
    this.deleteConfirmation.set(false);
  }

  private fail(error: unknown): void {
    this.error.set(chatErrorMessage(error));
  }
}

function titleFrom(content: string): string {
  const normalized = content.replace(/\s+/g, ' ').trim();
  return normalized.length <= 80 ? normalized : `${normalized.slice(0, 77)}...`;
}
