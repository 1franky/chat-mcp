import { ProviderCapabilityProfile, ProviderType } from '../providers/provider.models';

export interface Conversation {
  id: string;
  title: string;
  providerConnectionId: string | null;
  modelId: string;
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
}

export interface CreateConversationRequest {
  title: string;
  providerConnectionId: string;
  modelId: string;
}

export interface GenerationEvent {
  type: 'generation' | 'delta' | 'complete' | 'cancelled' | 'error';
  generationId: string;
  userMessage: ConversationMessage | null;
  assistantMessage: ConversationMessage | null;
  delta: string | null;
  errorCode: string | null;
  retryable: boolean;
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
