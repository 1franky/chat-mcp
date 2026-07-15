import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatToolbarModule } from '@angular/material/toolbar';

type IntegrationState = 'UP' | 'DEGRADED' | 'DOWN';

interface ProviderStatus {
  displayName: string;
  providerType: string;
  state: IntegrationState;
  fake: boolean;
}

interface McpStatus {
  state: IntegrationState;
  serverVersion: string;
  contractVersion: string;
  protocolVersion: string;
  detail: string;
  fake: boolean;
}

interface BootstrapStatus {
  application: string;
  version: string;
  sprint: number;
  mode: string;
  llmProvider: ProviderStatus;
  mcp: McpStatus;
}

@Component({
  selector: 'app-root',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule, MatToolbarModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly http = inject(HttpClient);

  protected readonly status = signal<BootstrapStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(false);
    this.http.get<BootstrapStatus>('/api/system/status').subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
      },
      error: () => {
        this.status.set(null);
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected statusClass(state: IntegrationState): string {
    return `status status--${state.toLowerCase()}`;
  }
}
