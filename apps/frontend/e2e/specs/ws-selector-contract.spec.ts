import { test } from '@playwright/test';
import { expectUniqueTestId } from '../utils/selectors';
import { LoginPage } from '../pages/login-page';
import { seedStage2Room } from '../utils/stage2-seeder';

test.describe('frontend websocket selector contract', () => {
  test('login screens expose stable selectors', async ({ page }) => {
    await new LoginPage(page).openTv();

    await expectUniqueTestId(page, 'login-room-code-input');
    await expectUniqueTestId(page, 'login-submit-button');

    await new LoginPage(page).openAdmin();

    await expectUniqueTestId(page, 'login-room-code-input');
    await expectUniqueTestId(page, 'login-submit-button');
  });

  test('stage-2 Admin and TV screens expose websocket-critical selectors', async ({ browser, request }) => {
    const ctx = await seedStage2Room(browser, request);

    try {
      for (const testId of [
        'admin-repeat-button',
        'admin-reveal-button',
        'admin-next-song-button',
        'admin-answer-correct-button',
        'admin-answer-incorrect-button',
        'admin-resolve-error-button',
      ]) {
        // Controls are scenario-dependent. They may not be visible at once, but the element should exist
        // when the frontend has a stable testability contract.
        await ctx.adminPage.locator(`[data-testid="${testId}"]`).first().waitFor({ state: 'attached', timeout: 10_000 });
      }

      for (const testId of [
        'tv-current-question',
        'tv-current-answer',
        'tv-answer-visible',
        'tv-system-error',
        'tv-scoreboard',
      ]) {
        await ctx.tvPage.locator(`[data-testid="${testId}"]`).first().waitFor({ state: 'attached', timeout: 10_000 });
      }
    } finally {
      await ctx.close();
    }
  });
});
