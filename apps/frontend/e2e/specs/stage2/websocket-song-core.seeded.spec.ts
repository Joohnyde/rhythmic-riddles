import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { createTeam } from '../../utils/api-client';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';
import {
  backendSentApplicationFrames,
  countBackendWsFramesOfType,
  hasExpectedRoleWsUrl,
  settle,
} from '../../utils/ws-capture';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

test.describe('Song core', () => {
  test('song role recovery', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(hasExpectedRoleWsUrl(clients.admin.frames, 'admin', seed.roomCode)).toBeTruthy();
        expect(hasExpectedRoleWsUrl(clients.tv.frames, 'tv', seed.roomCode)).toBeTruthy();
        expectSongsWelcome(clients.admin.frames, seed.currentScheduleId);
        expectSongsWelcome(clients.tv.frames, seed.currentScheduleId);
        expect(backendSentApplicationFrames(clients.admin.frames)).toHaveLength(0);
        expect(backendSentApplicationFrames(clients.tv.frames)).toHaveLength(0);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('revealed recovery schedule', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expectSongsWelcome(clients.admin.frames, seed.currentScheduleId);
        expectSongsWelcome(clients.tv.frames, seed.currentScheduleId);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('lobby frame no pollution', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (songSeed) => {
      await withGameFixture(request, 'LOBBY', async (lobbySeed) => {
        const songTv = await connectRole(browser, 'tv', songSeed.roomCode);
        try {
          const before = countBackendWsFramesOfType(songTv.frames, 'new_team');
          await createTeam(request, lobbySeed.roomCode, 'Other Room Team');
          await settle();
          expect(countBackendWsFramesOfType(songTv.frames, 'new_team')).toBe(before);
        } finally {
          await songTv.close();
        }
      });
    });
  });
});
