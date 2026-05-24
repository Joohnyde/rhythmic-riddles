import { Locator, Page, expect } from '@playwright/test';

export function byTestIdOrRole(
  page: Page,
  testId: string,
  fallback: () => Locator,
): Locator {
  return page.getByTestId(testId).or(fallback());
}

export function roomCodeInput(page: Page): Locator {
  return page.getByTestId('login-room-code-input').or(page.getByPlaceholder(/room code/i));
}

export function loginButton(page: Page): Locator {
  return page.getByTestId('login-submit-button').or(page.getByRole('button', { name: /log in/i }));
}

export async function expectUniqueTestId(page: Page, testId: string): Promise<void> {
  await expect(page.locator(`[data-testid="${testId}"]`), `missing or duplicated data-testid="${testId}"`).toHaveCount(1);
}
