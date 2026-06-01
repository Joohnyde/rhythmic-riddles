import { expect, test } from '@playwright/test';
import { LoginPage } from '../../pages/login-page';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import {
  captureReceivedWebSocketFrames,
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
  hasExpectedRoleWsUrl,
  settle,
} from '../../utils/ws-capture';

test.describe('Login boundaries', () => {
  for (const role of ['tv', 'admin'] as const) {
    test(`${role} role url welcome`, async ({ browser, request }) => {
      await withGameFixture(request, 'LOBBY', async (seed) => {
        const client = await connectRole(browser, role, seed.roomCode);
        try {
          expect(hasExpectedRoleWsUrl(client.frames, role, seed.roomCode)).toBeTruthy();
          const welcomeCount = countBackendWsFramesOfType(client.frames, 'welcome');
          await settle(750);
          expect(countBackendWsFramesOfType(client.frames, 'welcome')).toBe(welcomeCount);
        } finally {
          await client.close();
        }
      });
    });
  }

  test('admin tv same room', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(hasExpectedRoleWsUrl(clients.admin.frames, 'admin', seed.roomCode)).toBeTruthy();
        expect(hasExpectedRoleWsUrl(clients.tv.frames, 'tv', seed.roomCode)).toBeTruthy();
        await expectBackendWsFrameType(clients.admin.frames, 'welcome');
        await expectBackendWsFrameType(clients.tv.frames, 'welcome');
      } finally {
        await clients.close();
      }
    });
  });

  test('duplicate role blocked', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (seed) => {
      const original = await connectRole(browser, 'tv', seed.roomCode);
      const duplicate = await connectRole(browser, 'tv', seed.roomCode).catch(() => undefined);
      try {
        await settle(1000);
        expect(countBackendWsFramesOfType(original.frames, 'welcome')).toBe(1);
        if (duplicate) {
          expect(countBackendWsFramesOfType(duplicate.frames, 'welcome')).toBe(0);
        }
      } finally {
        await original.close().catch(() => undefined);
        if (duplicate) await duplicate.close().catch(() => undefined);
      }
    });
  });

  test('invalid room no welcome', async ({ page }) => {
    const frames = captureReceivedWebSocketFrames(page);
    const login = new LoginPage(page);
    await login.open('tv');
    await login.login('tv', 'MISS');
    await login.expectVisible('tv');
    await settle(750);
    expect(countBackendWsFramesOfType(frames, 'welcome')).toBe(0);
  });
});
