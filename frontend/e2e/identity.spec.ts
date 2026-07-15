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
