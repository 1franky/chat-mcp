import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { apiErrorMessage } from '../../core/http/api-error';
import { McpConnectionStatus, McpToolDefinition } from '../../core/mcp/mcp.models';
import { McpService } from '../../core/mcp/mcp.service';

@Component({
  selector: 'app-mcp-page',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './mcp-page.html',
  styleUrl: './mcp-page.scss',
})
export class McpPage {
  private readonly mcpApi = inject(McpService);

  protected readonly status = signal<McpConnectionStatus | null>(null);
  protected readonly tools = signal<McpToolDefinition[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  constructor() {
    this.load();
  }

  protected reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set('');
    this.mcpApi.status().subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(error));
      },
    });
    this.mcpApi.tools().subscribe({
      next: (tools) => this.tools.set(tools),
      error: () => this.tools.set([]),
    });
  }
}
