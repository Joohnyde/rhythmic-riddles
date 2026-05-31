import { expect, test } from '@playwright/test';
import { LoginPage } from '../../pages/login-page';
import { selectors } from '../../utils/selectors';
import { connectAdminAndTv } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';

test.describe('Selector contract', () => {
  for (const role of ['tv', 'admin'] as const) {
    test(`${role} login selectors`, async ({ page }) => {
      await new LoginPage(page).open(role);
      await expect(selectors.roomCodeInput(page, role).first()).toBeVisible();
      await expect(selectors.loginButton(page, role).first()).toBeVisible();
    });
  }

  test('lobby selectors', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        await expect(selectors.adminLobbyPage(clients.admin.page)).toBeVisible();
        await expect(selectors.tvLobbyPage(clients.tv.page)).toBeVisible();
      } finally {
        await clients.close();
      }
    });
  });

  test('stage2 selectors', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        await expect(selectors.adminSongsPage(clients.admin.page)).toBeVisible();
        await expect(selectors.tvSongsPage(clients.tv.page)).toBeVisible();
      } finally {
        await clients.close();
      }
    });
  });
});
