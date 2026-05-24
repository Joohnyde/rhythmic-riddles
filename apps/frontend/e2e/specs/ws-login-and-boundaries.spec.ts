import { expect, test } from '@playwright/test';
import { LoginPage } from '../pages/login-page';
import { createRoom } from '../utils/api-client';
import {
  backendSentApplicationFrames,
  captureReceivedWebSocketFrames,
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
  expectNoAdditionalFramesOfType,
  hasExpectedRoleWsUrl,
  settle,
} from '../utils/ws-capture';

test.describe('websocket login, routing, and boundaries', () => {
  for (const role of ['tv', 'admin'] as const) {
    test(`${role} login opens correct websocket URL and receives stable welcome`, async ({ page, request }) => {
      const roomCode = await createRoom(request);
      const frames = captureReceivedWebSocketFrames(page);

      const login = new LoginPage(page);
      await login.openRole(role);
      await login.login(roomCode);
      await login.expectConnected(role);

      await expectBackendWsFrameType(frames, 'welcome');

      const welcomeCount = countBackendWsFramesOfType(frames, 'welcome');
      await expectNoAdditionalFramesOfType(frames, 'welcome', welcomeCount);

      expect(hasExpectedRoleWsUrl(frames, role, roomCode)).toBeTruthy();
      expect(backendSentApplicationFrames(frames)).toHaveLength(0);
    });
  }

  test('Admin and TV can connect to the same room simultaneously', async ({ browser, request }) => {
    const roomCode = await createRoom(request);
    const adminContext = await browser.newContext();
    const tvContext = await browser.newContext();

    try {
      const adminPage = await adminContext.newPage();
      const tvPage = await tvContext.newPage();
      const adminFrames = captureReceivedWebSocketFrames(adminPage);
      const tvFrames = captureReceivedWebSocketFrames(tvPage);

      await new LoginPage(adminPage).openAdmin();
      await new LoginPage(tvPage).openTv();

      await new LoginPage(adminPage).login(roomCode);
      await new LoginPage(tvPage).login(roomCode);

      await new LoginPage(adminPage).expectConnected('admin');
      await new LoginPage(tvPage).expectConnected('tv');

      await expectBackendWsFrameType(adminFrames, 'welcome');
      await expectBackendWsFrameType(tvFrames, 'welcome');
    } finally {
      await adminContext.close();
      await tvContext.close();
    }
  });

  for (const role of ['tv', 'admin'] as const) {
    test(`duplicate ${role} socket does not produce two active welcome winners`, async ({ browser, request }) => {
      const roomCode = await createRoom(request);
      const first = await browser.newContext();
      const second = await browser.newContext();

      try {
        const firstPage = await first.newPage();
        const secondPage = await second.newPage();

        const firstFrames = captureReceivedWebSocketFrames(firstPage);
        const secondFrames = captureReceivedWebSocketFrames(secondPage);

        await new LoginPage(firstPage).openRole(role);
        await new LoginPage(firstPage).login(roomCode);
        await expectBackendWsFrameType(firstFrames, 'welcome');

        await new LoginPage(secondPage).openRole(role);
        await new LoginPage(secondPage).login(roomCode).catch(() => undefined);
        await settle();

        const totalDuplicateWelcomes = countBackendWsFramesOfType(secondFrames, 'welcome');
        expect(totalDuplicateWelcomes).toBe(0);
      } finally {
        await first.close();
        await second.close();
      }
    });
  }

  test('representative invalid room code stays on login and receives no welcome', async ({ page }) => {
    const frames = captureReceivedWebSocketFrames(page);
    const login = new LoginPage(page);

    await login.openTv();
    await login.login('MISS');

    await login.expectLoginVisible();
    await settle();

    expect(countBackendWsFramesOfType(frames, 'welcome')).toBe(0);
  });

  test('refresh currently returns to login instead of silently recovering in memory socket state', async ({ page, request }) => {
    const roomCode = await createRoom(request);
    const frames = captureReceivedWebSocketFrames(page);
    const login = new LoginPage(page);

    await login.openTv();
    await login.login(roomCode);
    await login.expectConnected('tv');
    await expectBackendWsFrameType(frames, 'welcome');

    await page.reload();

    await login.expectLoginVisible();
  });
});
