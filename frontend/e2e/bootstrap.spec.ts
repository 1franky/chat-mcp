import { expect, test } from '@playwright/test';

test('renders the Sprint 0 shell even while the backend is unavailable', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('AI Data Chat');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Base técnica');
  await expect(page.getByText('Sprint 0 · Bootstrap')).toBeVisible();
});
