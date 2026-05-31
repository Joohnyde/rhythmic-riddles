import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { createTeam, deleteTeam, tryCreateInvalidTeam } from '../../utils/api-client';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  observedBackendTypes,
  settle,
} from '../../utils/ws-capture';

test.describe('Lobby side effects', () => {
  test('team create kick order', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (seed) => {
      const tv = await connectRole(browser, 'tv', seed.roomCode);
      try {
        const beforeNew = countBackendWsFramesOfType(tv.frames, 'new_team');
        const team = await createTeam(request, seed.roomCode, 'Lobby WS Team');
        await expectBackendWsFrameTypeAfter(tv.frames, 'new_team', beforeNew);

        const beforeKick = countBackendWsFramesOfType(tv.frames, 'kick_team');
        await deleteTeam(request, seed.roomCode, team.id);
        await expectBackendWsFrameTypeAfter(tv.frames, 'kick_team', beforeKick);

        const types = observedBackendTypes(tv.frames);
        expect(types.lastIndexOf('new_team')).toBeLessThan(types.lastIndexOf('kick_team'));
      } finally {
        await tv.close();
      }
    });
  });

  test('tv-only frames stay on tv', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const adminNewBefore = countBackendWsFramesOfType(clients.admin.frames, 'new_team');
        const tvNewBefore = countBackendWsFramesOfType(clients.tv.frames, 'new_team');
        await createTeam(request, seed.roomCode, 'TV Only Frame');
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'new_team', tvNewBefore);
        await settle();
        expect(countBackendWsFramesOfType(clients.admin.frames, 'new_team')).toBe(adminNewBefore);
      } finally {
        await clients.close();
      }
    });
  });

  test('room activity isolation', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (roomA) => {
      await withGameFixture(request, 'LOBBY', async (roomB) => {
        const tvA = await connectRole(browser, 'tv', roomA.roomCode);
        const tvB = await connectRole(browser, 'tv', roomB.roomCode);
        try {
          const beforeB = countBackendWsFramesOfType(tvB.frames, 'new_team');
          const beforeA = countBackendWsFramesOfType(tvA.frames, 'new_team');
          await createTeam(request, roomA.roomCode, 'Room A Only');
          await expectBackendWsFrameTypeAfter(tvA.frames, 'new_team', beforeA);
          await settle();
          expect(countBackendWsFramesOfType(tvB.frames, 'new_team')).toBe(beforeB);
        } finally {
          await tvA.close();
          await tvB.close();
        }
      });
    });
  });

  test('invalid team emits no frame', async ({ browser, request }) => {
    await withGameFixture(request, 'LOBBY', async (seed) => {
      const tv = await connectRole(browser, 'tv', seed.roomCode);
      try {
        const before = countBackendWsFramesOfType(tv.frames, 'new_team');
        await tryCreateInvalidTeam(request, seed.roomCode);
        await settle();
        expect(countBackendWsFramesOfType(tv.frames, 'new_team')).toBe(before);
      } finally {
        await tv.close();
      }
    });
  });
});
