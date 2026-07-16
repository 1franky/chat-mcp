import { expect, test } from '@playwright/test';

const admin = {
  id: '6dc26004-382d-4f0a-a838-8614e32da62b',
  email: 'admin@example.test',
  displayName: 'Test Admin',
  role: 'ADMIN',
  active: true,
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
};

test('registers the first administrator without accepting a role from the browser', async ({
  page,
}) => {
  let registered = false;
  await page.route('**/api/auth/bootstrap', async (route) => {
    await route.fulfill({
      json: registered
        ? bootstrap(admin)
        : {
            ...bootstrap(null),
            bootstrapRequired: true,
            registrationOpen: true,
          },
    });
  });
  await page.route('**/api/auth/register', async (route) => {
    const request = route.request().postDataJSON() as Record<string, unknown>;
    expect(request['role']).toBeUndefined();
    registered = true;
    await route.fulfill({ status: 201, json: admin });
  });

  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Crea el primer administrador' })).toBeVisible();
  await page.getByLabel('Nombre visible').fill(admin.displayName);
  await page.getByLabel('Correo').fill(admin.email);
  await page.getByLabel('Contrasena').fill('correct-horse-battery-staple');
  await page.getByRole('button', { name: 'Crear cuenta' }).click();

  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByRole('heading', { name: `Hola, ${admin.displayName}` })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Administrar usuarios' })).toBeVisible();
});

test('logs in through a server-side session flow', async ({ page }) => {
  let authenticated = false;
  await page.route('**/api/auth/bootstrap', async (route) => {
    await route.fulfill({ json: bootstrap(authenticated ? admin : null) });
  });
  await page.route('**/api/auth/login', async (route) => {
    authenticated = true;
    await route.fulfill({ json: admin });
  });

  await page.goto('/auth');
  await page.getByLabel('Correo').fill(admin.email);
  await page.getByLabel('Contrasena').fill('correct-horse-battery-staple');
  await page.getByRole('button', { name: 'Iniciar sesion' }).last().click();

  await expect(page).toHaveURL(/\/home$/);
  await expect(page.getByText(admin.email)).toBeVisible();
});

test('shows a safe provider catalog and verifies the fake connection', async ({ page }) => {
  await page.route('**/api/auth/bootstrap', async (route) => {
    await route.fulfill({ json: bootstrap(admin) });
  });
  await page.route('**/api/providers', async (route) => {
    await route.fulfill({ json: [fakeProvider] });
  });
  await page.route(`**/api/providers/${fakeProvider.id}/models`, async (route) => {
    await route.fulfill({ json: [] });
  });
  await page.route(`**/api/providers/${fakeProvider.id}/test`, async (route) => {
    await route.fulfill({
      json: {
        success: true,
        code: 'CONNECTION_OK',
        message: 'Conexion verificada.',
        providerRequestId: 'fake-request',
        retryable: false,
        testedAt: '2026-07-15T00:00:00Z',
      },
    });
  });

  await page.goto('/settings/providers');
  await page.getByRole('button', { name: /Fake local/ }).click();
  await page.getByRole('button', { name: 'Probar conexion' }).click();

  await expect(page.getByText('Conexion verificada correctamente.')).toBeVisible();
  await expect(page.getByText('Sin credencial')).toBeVisible();
});

test('creates a conversation and renders a fake streamed response', async ({ page }) => {
  const conversation = {
    id: '9ce23b08-c5bb-40b0-962d-67bd2417eb3d',
    title: 'Explica el resultado',
    providerConnectionId: fakeProvider.id,
    modelId: 'fake-chat-v1',
    createdAt: '2026-07-15T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  };
  const userMessage = chatMessage(
    '1831213d-2cf9-4410-acb1-5f070233e505',
    conversation.id,
    1,
    'USER',
    'Explica el resultado',
    'COMPLETED',
  );
  const partial = chatMessage(
    '43b9d6fc-3fdf-4ba9-bfad-72c84f880685',
    conversation.id,
    2,
    'ASSISTANT',
    'Respuesta ',
    'STREAMING',
  );
  const completed = {
    ...partial,
    content: 'Respuesta **fake** en streaming.',
    status: 'COMPLETED',
    inputTokens: 4,
    outputTokens: 6,
    finishReason: 'stop',
    providerRequestId: 'fake-request',
    updatedAt: '2026-07-15T00:00:01Z',
  };
  let created = false;

  await page.route('**/api/auth/bootstrap', async (route) => {
    await route.fulfill({ json: bootstrap(admin) });
  });
  await page.route('**/api/system/status', async (route) => {
    await route.fulfill({
      json: {
        sprint: 3,
        mode: 'fake',
        mcp: { state: 'UP', protocolVersion: 'test', contractVersion: '1.0.0' },
      },
    });
  });
  await page.route('**/api/providers', async (route) => {
    await route.fulfill({ json: [{ ...fakeProvider, defaultModelId: 'fake-chat-v1' }] });
  });
  await page.route(`**/api/providers/${fakeProvider.id}/models`, async (route) => {
    await route.fulfill({ json: [fakeModel] });
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
    const request = route.request().postDataJSON() as Record<string, unknown>;
    expect(request['providerConnectionId']).toBe(fakeProvider.id);
    expect(request['modelId']).toBe('fake-chat-v1');
    created = true;
    await route.fulfill({ status: 201, json: conversation });
  });
  await page.route(`**/api/conversations/${conversation.id}`, async (route) => {
    await route.fulfill({ json: conversation });
  });
  await page.route(`**/api/conversations/${conversation.id}/messages`, async (route) => {
    await route.fulfill({ json: created ? [userMessage, completed] : [] });
  });
  await page.route(`**/api/conversations/${conversation.id}/messages/stream`, async (route) => {
    const generationId = '790d8f07-2ea0-42d8-9b29-2c5e5fb3a1a7';
    const events = [
      generationEvent('generation', generationId, userMessage, partial),
      generationEvent('delta', generationId, null, partial, 'Respuesta '),
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
  await composer.fill('Explica el resultado');
  await page.getByRole('button', { name: /Enviar/ }).click();

  await expect(page).toHaveURL(new RegExp(`/chat/${conversation.id}$`));
  await expect(page.locator('.markdown')).toContainText('Respuesta fake en streaming.');
  await expect(page.getByText('Completado')).toBeVisible();
  await expect(page.getByText(/Tokens 4 \/ 6/)).toBeVisible();
  await expect(page.getByText(/MCP FAKE UP/)).toBeVisible();
});

function bootstrap(user: typeof admin | null) {
  return {
    bootstrapRequired: false,
    registrationOpen: true,
    authenticated: user !== null,
    user,
  };
}

const fakeProvider = {
  id: 'f45f50c8-7fee-49ca-8e8c-a40bbf425f73',
  displayName: 'Fake local',
  providerType: 'FAKE',
  baseUrl: null,
  region: null,
  modelsPath: null,
  responsesPath: null,
  chatCompletionsPath: null,
  configuredModelId: null,
  defaultModelId: null,
  credentialMasked: null,
  state: 'UP',
  lastErrorCode: null,
  lastTestedAt: '2026-07-15T00:00:00Z',
  lastModelsSyncedAt: null,
  capabilities: {
    chat: 'SUPPORTED',
    streaming: 'SUPPORTED',
    toolCalling: 'UNSUPPORTED',
    structuredOutput: 'UNSUPPORTED',
    vision: 'UNSUPPORTED',
    embeddings: 'UNSUPPORTED',
    modelDiscovery: 'SUPPORTED',
  },
  createdAt: '2026-07-15T00:00:00Z',
  updatedAt: '2026-07-15T00:00:00Z',
};

const fakeModel = {
  id: '6d989f42-8944-492c-ae91-c5859ec24140',
  modelId: 'fake-chat-v1',
  displayName: 'fake-chat-v1',
  origin: 'DISCOVERED',
  capabilities: fakeProvider.capabilities,
  discoveredAt: '2026-07-15T00:00:00Z',
  lastValidatedAt: null,
};

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
    providerConnectionId: role === 'ASSISTANT' ? fakeProvider.id : null,
    providerType: role === 'ASSISTANT' ? 'FAKE' : null,
    modelId: role === 'ASSISTANT' ? 'fake-chat-v1' : null,
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
