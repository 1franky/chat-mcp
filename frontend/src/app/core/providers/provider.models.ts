export type ProviderType =
  | 'OPENAI'
  | 'ANTHROPIC'
  | 'BYTEPLUS'
  | 'OPENAI_COMPATIBLE'
  | 'OLLAMA'
  | 'FAKE';

export type CapabilityAvailability = 'SUPPORTED' | 'UNSUPPORTED' | 'UNKNOWN';

export interface ProviderCapabilityProfile {
  chat: CapabilityAvailability;
  streaming: CapabilityAvailability;
  toolCalling: CapabilityAvailability;
  structuredOutput: CapabilityAvailability;
  vision: CapabilityAvailability;
  embeddings: CapabilityAvailability;
  modelDiscovery: CapabilityAvailability;
}

export interface ProviderConnection {
  id: string;
  displayName: string;
  providerType: ProviderType;
  baseUrl: string | null;
  region: string | null;
  modelsPath: string | null;
  responsesPath: string | null;
  chatCompletionsPath: string | null;
  configuredModelId: string | null;
  defaultModelId: string | null;
  credentialMasked: string | null;
  state: 'NOT_TESTED' | 'UP' | 'DOWN';
  lastErrorCode: string | null;
  lastTestedAt: string | null;
  lastModelsSyncedAt: string | null;
  capabilities: ProviderCapabilityProfile;
  createdAt: string;
  updatedAt: string;
}

export interface ProviderModel {
  id: string;
  modelId: string;
  displayName: string;
  origin: 'DISCOVERED' | 'MANUAL' | 'CONFIGURED';
  capabilities: ProviderCapabilityProfile;
  discoveredAt: string;
  lastValidatedAt: string | null;
}

export interface SaveProviderRequest {
  displayName: string;
  providerType: ProviderType;
  apiKey: string | null;
  baseUrl: string | null;
  region: string | null;
  modelsPath: string | null;
  responsesPath: string | null;
  chatCompletionsPath: string | null;
  configuredModelId: string | null;
}

export interface ProviderTestResult {
  success: boolean;
  code: string;
  message: string;
  providerRequestId: string | null;
  retryable: boolean;
  testedAt: string;
}
