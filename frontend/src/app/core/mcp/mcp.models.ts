export interface McpToolDefinition {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  readOnly: boolean;
}

export type McpIntegrationState = 'UP' | 'DEGRADED' | 'DOWN';

export interface McpConnectionStatus {
  state: McpIntegrationState;
  serverVersion: string;
  contractVersion: string;
  protocolVersion: string;
  detail: string;
  fake: boolean;
}
