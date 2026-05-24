import { expect, test } from '@playwright/test';
import { LoginPage } from '../pages/login-page';
import { createRoom, createTeam, deleteTeam, tryCreateInvalidTeam } from '../utils/api-client';
import {
  captureReceivedWebSocketFrames,
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
  observedBackendTypes,
  settle,
} from '../utils/ws-capture';

test.describe('lobby REST to websocket side effects and room isolation', () => {
  test('create then kick team emits correct TV frames in order', async ({ page, request }) => {
    const roomCode = await createRoom(request);
    const frames = captureReceivedWebSocketFrames(page);

    await new LoginPage(page).openTv();
    await new LoginPage(page).login(roomCode);
    await expectBackendWsFrameType(frames, 'welcome');

    const team = await createTeam(request, roomCode, 'E2E WS Team');
    await expectBackendWsFrameType(frames, 'new_team');

    await deleteTeam(request, roomCode, team.id);
    await expectBackendWsFrameType(frames, 'kick_team');

    const types = observedBackendTypes(frames);
    expect(types.lastIndexOf('new_team')).toBeLessThan(types.lastIndexOf('kick_team'));
  });

  test('new_team and kick_team are routed to TV, not Admin', async ({ browser, request }) => {
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

      await expectBackendWsFrameType(adminFrames, 'welcome');
      await expectBackendWsFrameType(tvFrames, 'welcome');

      const team = await createTeam(request, roomCode, 'Routed Team');
      await expectBackendWsFrameType(tvFrames, 'new_team');

      await deleteTeam(request, roomCode, team.id);
      await expectBackendWsFrameType(tvFrames, 'kick_team');

      await settle();

      expect(countBackendWsFramesOfType(adminFrames, 'new_team')).toBe(0);
      expect(countBackendWsFramesOfType(adminFrames, 'kick_team')).toBe(0);
    } finally {
      await adminContext.close();
      await tvContext.close();
    }
  });

  test('room A frames do not leak into room B browser websocket', async ({ browser, request }) => {
    const roomA = await createRoom(request);
    const roomB = await createRoom(request);

    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();

    try {
      const pageA = await ctxA.newPage();
      const pageB = await ctxB.newPage();

      const framesA = captureReceivedWebSocketFrames(pageA);
      const framesB = captureReceivedWebSocketFrames(pageB);

      await new LoginPage(pageA).openTv();
      await new LoginPage(pageB).openTv();

      await new LoginPage(pageA).login(roomA);
      await new LoginPage(pageB).login(roomB);

      await expectBackendWsFrameType(framesA, 'welcome');
      await expectBackendWsFrameType(framesB, 'welcome');

      const beforeB = countBackendWsFramesOfType(framesB, 'new_team');

      await createTeam(request, roomA, 'Room A Only');
      await expectBackendWsFrameType(framesA, 'new_team');

      await settle();

      expect(countBackendWsFramesOfType(framesB, 'new_team')).toBe(beforeB);
    } finally {
      await ctxA.close();
      await ctxB.close();
    }
  });

  test('invalid team creation does not emit false new_team frame', async ({ page, request }) => {
    const roomCode = await createRoom(request);
    const frames = captureReceivedWebSocketFrames(page);

    await new LoginPage(page).openTv();
    await new LoginPage(page).login(roomCode);
    await expectBackendWsFrameType(frames, 'welcome');

    const before = countBackendWsFramesOfType(frames, 'new_team');

    await tryCreateInvalidTeam(request, roomCode);
    await settle();

    expect(countBackendWsFramesOfType(frames, 'new_team')).toBe(before);
  });
});
