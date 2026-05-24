import { test, expect } from '@playwright/test';

test('TV login has stable test ids', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('tv-home-page')).toBeVisible();
  await expect(page.getByTestId('tv-login-form')).toBeVisible();
  await expect(page.getByTestId('tv-room-code-input')).toBeVisible();
});
