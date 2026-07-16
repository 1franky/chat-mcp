import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { McpConnectionStatus, McpToolDefinition } from './mcp.models';

@Injectable({ providedIn: 'root' })
export class McpService {
  private readonly http = inject(HttpClient);

  status(): Observable<McpConnectionStatus> {
    return this.http.get<McpConnectionStatus>('/api/mcp/status');
  }

  tools(): Observable<McpToolDefinition[]> {
    return this.http.get<McpToolDefinition[]>('/api/mcp/tools');
  }
}
