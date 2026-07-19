import { ProviderCapabilityProfile, ProviderType } from '../providers/provider.models';

export interface Conversation {
  id: string;
  title: string;
  providerConnectionId: string | null;
  modelId: string;
  selectedDocumentIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface ConversationPage {
  items: Conversation[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type MessageRole = 'USER' | 'ASSISTANT';
export type MessageStatus = 'STREAMING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';
export type ToolCallStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'BLOCKED' | 'TIMEOUT';

export interface ToolCallView {
  id: string;
  toolName: string;
  generationRound: number;
  sequence: number;
  status: ToolCallStatus;
  arguments: Record<string, unknown>;
  result: Record<string, unknown> | null;
  isError: boolean | null;
  errorCode: string | null;
}

export interface CitationView {
  documentId: string;
  documentName: string;
  chunkId: string;
  pageNumber: number | null;
  sectionLabel: string | null;
  snippet: string;
  score: number;
}

export interface ConversationMessage {
  id: string;
  conversationId: string;
  position: number;
  role: MessageRole;
  content: string;
  providerConnectionId: string | null;
  providerType: ProviderType | null;
  modelId: string | null;
  status: MessageStatus;
  inputTokens: number | null;
  outputTokens: number | null;
  finishReason: string | null;
  providerRequestId: string | null;
  regeneratedFromMessageId: string | null;
  createdAt: string;
  updatedAt: string;
  toolCalls: ToolCallView[];
  citations: CitationView[];
}

export interface CreateConversationRequest {
  title: string;
  providerConnectionId: string;
  modelId: string;
}

export interface GenerationEvent {
  type: 'generation' | 'delta' | 'complete' | 'cancelled' | 'error' | 'tool_call' | 'tool_result';
  generationId: string;
  userMessage: ConversationMessage | null;
  assistantMessage: ConversationMessage | null;
  delta: string | null;
  errorCode: string | null;
  retryable: boolean;
  toolCall: ToolCallView | null;
}

export interface McpSummary {
  state: 'UP' | 'DEGRADED' | 'DOWN';
  protocolVersion: string;
  contractVersion: string;
}

export interface SystemStatus {
  sprint: number;
  mode: string;
  mcp: McpSummary;
}

export interface SelectedModelCapabilities {
  providerName: string;
  modelName: string;
  profile: ProviderCapabilityProfile;
}
