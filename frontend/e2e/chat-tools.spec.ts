import { expect, test } from '@playwright/test';

const admin = {
  id: '6dc26004-382d-4f0a-a838-8614e32da62c',
  email: 'admin-tools@example.test',
  displayName: 'Test Admin',
  role: 'ADMIN',
  active: true,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
};

const openAiProvider = {
  id: 'a1a1a1a1-1111-4111-8111-111111111111',
  displayName: 'OpenAI real',
  providerType: 'OPENAI',
  baseUrl: null,
  region: null,
  modelsPath: null,
  responsesPath: null,
  chatCompletionsPath: null,
  configuredModelId: null,
  defaultModelId: 'gpt-test',
  credentialMasked: '••ab',
  state: 'UP',
  lastErrorCode: null,
  lastTestedAt: '2026-07-15T00:00:00Z',
  lastModelsSyncedAt: null,
  capabilities: {
    chat: 'SUPPORTED',
    streaming: 'SUPPORTED',
    toolCalling: 'SUPPORTED',
    structuredOutput: 'SUPPORTED',
    vision: 'SUPPORTED',
    embeddings: 'UNSUPPORTED',
    modelDiscovery: 'SUPPORTED',
  },
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
};

const openAiModel = {
  id: 'b2b2b2b2-2222-4222-8222-222222222222',
  modelId: 'gpt-test',
  displayName: 'gpt-test',
  origin: 'CONFIGURED',
  capabilities: openAiProvider.capabilities,
  discoveredAt: '2026-07-15T00:00:00Z',
  lastValidatedAt: null,
};

test('shows MCP status and available tools on the read-only panel', async ({ page }) => {
  await page.route('**/api/auth/bootstrap', async (route) => {
    await route.fulfill({ json: bootstrap(admin) });
  });
  await page.route('**/api/mcp/status', async (route) => {
    await route.fulfill({
      json: {
        state: 'UP',
        serverVersion: '0.5.0',
        contractVersion: '1.0.0',
        protocolVersion: '2025-11-25',
        detail: 'Connected',
        fake: false,
      },
    });
  });
  await page.route('**/api/mcp/tools', async (route) => {
    await route.fulfill({
      json: [
        {
          name: 'health_check',
          description: 'Check health',
          inputSchema: { type: 'object' },
          readOnly: true,
        },
      ],
    });
  });

  await page.goto('/settings/mcp');

  await expect(page.getByText('UP', { exact: true })).toBeVisible();
  await expect(page.getByText('health_check')).toBeVisible();
  await expect(page.getByText('Solo lectura', { exact: true })).toBeVisible();
});

test('renders a tool call card and never presents a blocked result as success', async ({
  page,
}) => {
  const conversation = {
    id: 'c3c3c3c3-3333-4333-8333-333333333333',
    title: 'Consulta el estado',
    providerConnectionId: openAiProvider.id,
    modelId: 'gpt-test',
    createdAt: '2026-07-15T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  };
  const userMessage = chatMessage(
    'd4d4d4d4-4444-4444-8444-444444444444',
    conversation.id,
    1,
    'USER',
    'Revisa el estado y borra los usuarios',
    'COMPLETED',
  );
  const streaming = chatMessage(
    'e5e5e5e5-5555-4555-8555-555555555555',
    conversation.id,
    2,
    'ASSISTANT',
    '',
    'STREAMING',
  );
  const completed = {
    ...streaming,
    content: 'Revise el estado; no puedo borrar usuarios.',
    status: 'COMPLETED',
    inputTokens: 12,
    outputTokens: 20,
    finishReason: 'stop',
    providerRequestId: 'req-2',
    updatedAt: '2026-07-15T00:00:02Z',
  };
  const blockedToolCall = {
    id: 'f6f6f6f6-6666-4666-8666-666666666666',
    toolName: 'execute_read_query',
    generationRound: 1,
    sequence: 0,
    status: 'BLOCKED',
    arguments: { sql: 'DELETE FROM users' },
    result: { error: 'Tool is not in the MCP allowlist' },
    isError: true,
    errorCode: 'MCP_TOOL_NOT_ALLOWED',
  };
  let created = false;

  await page.route('**/api/auth/bootstrap', async (route) => {
    await route.fulfill({ json: bootstrap(admin) });
  });
  await page.route('**/api/system/status', async (route) => {
    await route.fulfill({
      json: {
        sprint: 4,
        mode: 'real',
        mcp: { state: 'UP', protocolVersion: '2025-11-25', contractVersion: '1.0.0' },
      },
    });
  });
  await page.route('**/api/providers', async (route) => {
    await route.fulfill({ json: [openAiProvider] });
  });
  await page.route(`**/api/providers/${openAiProvider.id}/models`, async (route) => {
    await route.fulfill({ json: [openAiModel] });
  });
  await page.route('**/api/conversations?**', async (route) => {
    await route.fulfill({
      json: {
        items: created ? [conversation] : [],
        page: 0,
        size: 30,
        totalElements: created ? 1 : 0,
        totalPages: created ? 1 : 0,
      },
    });
  });
  await page.route('**/api/conversations', async (route) => {
    expect(route.request().method()).toBe('POST');
    created = true;
    await route.fulfill({ status: 201, json: conversation });
  });
  await page.route(`**/api/conversations/${conversation.id}`, async (route) => {
    await route.fulfill({ json: conversation });
  });
  await page.route(`**/api/conversations/${conversation.id}/messages`, async (route) => {
    await route.fulfill({
      json: created ? [userMessage, { ...completed, toolCalls: [blockedToolCall] }] : [],
    });
  });
  await page.route(`**/api/conversations/${conversation.id}/messages/stream`, async (route) => {
    const generationId = '07070707-0707-4707-8707-070707070707';
    const events = [
      generationEvent('generation', generationId, userMessage, streaming),
      generationEvent('tool_call', generationId, null, streaming, null, {
        ...blockedToolCall,
        result: null,
      }),
      generationEvent('tool_result', generationId, null, streaming, null, blockedToolCall),
      generationEvent('delta', generationId, null, completed, completed.content),
      generationEvent('complete', generationId, null, completed),
    ];
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: events
        .map((event) => `event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`)
        .join(''),
    });
  });

  await page.goto('/chat');
  const composer = page.getByRole('textbox', { name: 'Mensaje' });
  await expect(composer).toBeEnabled();
  await composer.fill('Revisa el estado y borra los usuarios');
  await page.getByRole('button', { name: /Enviar/ }).click();

  await expect(page).toHaveURL(new RegExp(`/chat/${conversation.id}$`));
  await expect(page.getByText('execute_read_query')).toBeVisible();
  const toolCallCard = page.locator('.tool-call-card');
  await expect(toolCallCard).toHaveAttribute('data-state', 'BLOCKED');
  await expect(toolCallCard).toHaveAttribute('data-error', 'true');
  await expect(page.getByText('Bloqueado')).toBeVisible();
  await expect(page.locator('.markdown')).toContainText('no puedo borrar usuarios');
});

function bootstrap(user: typeof admin | null) {
  return {
    bootstrapRequired: false,
    registrationOpen: true,
    authenticated: user !== null,
    user,
  };
}

function chatMessage(
  id: string,
  conversationId: string,
  position: number,
  role: 'USER' | 'ASSISTANT',
  content: string,
  status: 'STREAMING' | 'COMPLETED',
) {
  return {
    id,
    conversationId,
    position,
    role,
    content,
    providerConnectionId: role === 'ASSISTANT' ? openAiProvider.id : null,
    providerType: role === 'ASSISTANT' ? 'OPENAI' : null,
    modelId: role === 'ASSISTANT' ? 'gpt-test' : null,
    status,
    inputTokens: null,
    outputTokens: null,
    finishReason: null,
    providerRequestId: null,
    regeneratedFromMessageId: null,
    createdAt: '2026-07-15T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
    toolCalls: [],
  };
}

function generationEvent(
  type: 'generation' | 'delta' | 'complete' | 'tool_call' | 'tool_result',
  generationId: string,
  userMessage: ReturnType<typeof chatMessage> | null,
  assistantMessage: ReturnType<typeof chatMessage> | null,
  delta: string | null = null,
  toolCall: Record<string, unknown> | null = null,
) {
  return {
    type,
    generationId,
    userMessage,
    assistantMessage,
    delta,
    errorCode: null,
    retryable: false,
    toolCall,
  };
}
