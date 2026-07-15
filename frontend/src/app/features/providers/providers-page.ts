import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { apiErrorMessage } from '../../core/http/api-error';
import {
  CapabilityAvailability,
  ProviderCapabilityProfile,
  ProviderConnection,
  ProviderModel,
  ProviderType,
  SaveProviderRequest,
} from '../../core/providers/provider.models';
import { ProviderService } from '../../core/providers/provider.service';

@Component({
  selector: 'app-providers-page',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './providers-page.html',
  styleUrl: './providers-page.scss',
})
export class ProvidersPage {
  private readonly providersApi = inject(ProviderService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly providers = signal<ProviderConnection[]>([]);
  protected readonly selected = signal<ProviderConnection | null>(null);
  protected readonly models = signal<ProviderModel[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly runningAction = signal<string | null>(null);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly deleteConfirmation = signal(false);
  protected readonly editing = computed(() => this.selected() !== null);

  protected readonly providerTypes: ReadonlyArray<{ value: ProviderType; label: string }> = [
    { value: 'OPENAI', label: 'OpenAI' },
    { value: 'ANTHROPIC', label: 'Anthropic' },
    { value: 'BYTEPLUS', label: 'BytePlus ModelArk' },
    { value: 'OPENAI_COMPATIBLE', label: 'OpenAI compatible' },
    { value: 'OLLAMA', label: 'Ollama' },
    { value: 'FAKE', label: 'Fake determinista' },
  ];

  protected readonly form = this.formBuilder.nonNullable.group({
    displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    providerType: ['OPENAI' as ProviderType, Validators.required],
    apiKey: ['', Validators.maxLength(8192)],
    baseUrl: ['', Validators.maxLength(2048)],
    region: ['ap-southeast-1', Validators.maxLength(64)],
    modelsPath: ['/models', Validators.maxLength(255)],
    responsesPath: ['/responses', Validators.maxLength(255)],
    chatCompletionsPath: ['/chat/completions', Validators.maxLength(255)],
    configuredModelId: ['', Validators.maxLength(255)],
  });

  protected readonly manualModelForm = this.formBuilder.nonNullable.group({
    modelId: ['', [Validators.required, Validators.maxLength(255)]],
  });

  constructor() {
    this.loadProviders();
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.clearMessages();
    const request = this.toRequest();
    const selected = this.selected();
    const operation = selected
      ? this.providersApi.update(selected.id, request)
      : this.providersApi.create(request);
    operation.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notice.set(selected ? 'Conexion actualizada.' : 'Conexion creada.');
        this.selected.set(saved);
        this.form.patchValue({ apiKey: '' });
        this.loadProviders(saved.id);
      },
      error: (error) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  protected edit(provider: ProviderConnection): void {
    this.clearMessages();
    this.deleteConfirmation.set(false);
    this.selected.set(provider);
    this.form.controls.providerType.disable();
    this.form.setValue({
      displayName: provider.displayName,
      providerType: provider.providerType,
      apiKey: '',
      baseUrl: provider.baseUrl ?? '',
      region: provider.region ?? 'ap-southeast-1',
      modelsPath: provider.modelsPath ?? '',
      responsesPath: provider.responsesPath ?? '',
      chatCompletionsPath: provider.chatCompletionsPath ?? '',
      configuredModelId: provider.configuredModelId ?? '',
    });
    this.loadModels(provider.id);
  }

  protected newProvider(): void {
    this.selected.set(null);
    this.models.set([]);
    this.deleteConfirmation.set(false);
    this.clearMessages();
    this.form.controls.providerType.enable();
    this.form.reset({
      displayName: '',
      providerType: 'OPENAI',
      apiKey: '',
      baseUrl: '',
      region: 'ap-southeast-1',
      modelsPath: '/models',
      responsesPath: '/responses',
      chatCompletionsPath: '/chat/completions',
      configuredModelId: '',
    });
  }

  protected testConnection(provider: ProviderConnection): void {
    this.run(provider.id, 'test', () =>
      this.providersApi.test(provider.id).subscribe({
        next: (result) => {
          this.finishAction();
          if (result.success) {
            this.notice.set('Conexion verificada correctamente.');
          } else {
            this.error.set(result.message);
          }
          this.loadProviders(provider.id);
        },
        error: (error) => this.failAction(error),
      }),
    );
  }

  protected synchronize(provider: ProviderConnection): void {
    this.run(provider.id, 'sync', () =>
      this.providersApi.synchronizeModels(provider.id).subscribe({
        next: (models) => {
          this.models.set(models);
          this.finishAction();
          this.notice.set(`Catalogo sincronizado: ${models.length} modelo(s).`);
          this.loadProviders(provider.id);
        },
        error: (error) => this.failAction(error),
      }),
    );
  }

  protected addManualModel(): void {
    const provider = this.selected();
    this.manualModelForm.markAllAsTouched();
    if (!provider || this.manualModelForm.invalid || this.runningAction()) {
      return;
    }
    this.run(provider.id, 'manual', () =>
      this.providersApi
        .addManualModel(provider.id, this.manualModelForm.getRawValue().modelId)
        .subscribe({
          next: () => {
            this.finishAction();
            this.manualModelForm.reset();
            this.notice.set('Modelo validado y agregado.');
            this.loadModels(provider.id);
          },
          error: (error) => this.failAction(error),
        }),
    );
  }

  protected selectDefault(provider: ProviderConnection, model: ProviderModel): void {
    this.run(provider.id, `default:${model.modelId}`, () =>
      this.providersApi.selectDefaultModel(provider.id, model.modelId).subscribe({
        next: (updated) => {
          this.finishAction();
          this.selected.set(updated);
          this.notice.set('Modelo predeterminado actualizado.');
          this.loadProviders(updated.id);
        },
        error: (error) => this.failAction(error),
      }),
    );
  }

  protected remove(provider: ProviderConnection): void {
    if (!this.deleteConfirmation()) {
      this.deleteConfirmation.set(true);
      return;
    }
    this.run(provider.id, 'delete', () =>
      this.providersApi.delete(provider.id).subscribe({
        next: () => {
          this.finishAction();
          this.newProvider();
          this.notice.set('Conexion eliminada.');
          this.loadProviders();
        },
        error: (error) => this.failAction(error),
      }),
    );
  }

  protected needsApiKey(type: ProviderType): boolean {
    return type !== 'OLLAMA' && type !== 'FAKE';
  }

  protected needsBaseUrl(type: ProviderType): boolean {
    return type === 'OPENAI_COMPATIBLE' || type === 'OLLAMA';
  }

  protected capabilityEntries(
    profile: ProviderCapabilityProfile,
  ): ReadonlyArray<{ label: string; value: CapabilityAvailability }> {
    return [
      { label: 'Chat', value: profile.chat },
      { label: 'Streaming', value: profile.streaming },
      { label: 'Tools', value: profile.toolCalling },
      { label: 'JSON', value: profile.structuredOutput },
      { label: 'Vision', value: profile.vision },
      { label: 'Embeddings', value: profile.embeddings },
      { label: 'Catalogo', value: profile.modelDiscovery },
    ];
  }

  protected actionRunning(provider: ProviderConnection, action: string): boolean {
    return this.runningAction() === `${provider.id}:${action}`;
  }

  private loadProviders(selectId?: string): void {
    this.loading.set(true);
    this.providersApi.list().subscribe({
      next: (providers) => {
        this.providers.set(providers);
        this.loading.set(false);
        if (selectId) {
          const selected = providers.find((provider) => provider.id === selectId) ?? null;
          this.selected.set(selected);
          if (selected) {
            this.loadModels(selected.id);
          }
        }
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
  }

  private loadModels(providerId: string): void {
    this.providersApi.listModels(providerId).subscribe({
      next: (models) => this.models.set(models),
      error: (error) => this.error.set(apiErrorMessage(error)),
    });
  }

  private toRequest(): SaveProviderRequest {
    const value = this.form.getRawValue();
    const optional = (input: string): string | null => (input.trim() ? input.trim() : null);
    return {
      displayName: value.displayName.trim(),
      providerType: value.providerType,
      apiKey: optional(value.apiKey),
      baseUrl: optional(value.baseUrl),
      region: optional(value.region),
      modelsPath: optional(value.modelsPath),
      responsesPath: optional(value.responsesPath),
      chatCompletionsPath: optional(value.chatCompletionsPath),
      configuredModelId: optional(value.configuredModelId),
    };
  }

  private run(providerId: string, action: string, operation: () => void): void {
    if (this.runningAction()) {
      return;
    }
    this.clearMessages();
    this.runningAction.set(`${providerId}:${action}`);
    operation();
  }

  private finishAction(): void {
    this.runningAction.set(null);
    this.deleteConfirmation.set(false);
  }

  private failAction(error: unknown): void {
    this.finishAction();
    this.error.set(apiErrorMessage(error));
  }

  private clearMessages(): void {
    this.error.set('');
    this.notice.set('');
  }
}
