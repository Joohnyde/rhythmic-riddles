import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import { connectAdminAndTv } from '../../utils/e2e-session';
import {
  answerInterrupt,
  createInterrupt,
  replaySchedule,
  resolveSystemInterrupt,
  revealSchedule,
} from '../../utils/api-client';
import {
  assertAllBackendFramesHaveFrontendContract,
  knownFrontendWsTypes,
} from '../../utils/ws-contracts';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
} from '../../utils/ws-capture';

test.describe('Runtime contracts', () => {
  test('frontend registry completeness', async () => {
    expect(knownFrontendWsTypes()).toEqual([
      'album_picked',
      'answer',
      'error_solved',
      'kick_team',
      'new_team',
      'pause',
      'song_next',
      'song_repeat',
      'song_reveal',
      'welcome',
    ]);
  });

  test('observed frames match schemas', async ({ browser, request }) => {
    // song_repeat from active listening state
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const repeatBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_repeat');
        expect(await replaySchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_repeat', repeatBefore);

        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    // error_solved from a valid deterministic system-pause fixture with a scenario marker
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const solvedBefore = countBackendWsFramesOfType(clients.tv.frames, 'error_solved');
        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', solvedBefore);

        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    // pause + answer from a valid team-buzz state
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseBefore);

        const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        const answerBefore = countBackendWsFramesOfType(clients.tv.frames, 'answer');
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), false),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'answer', answerBefore);

        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });

    // song_reveal from an expired listening state, not mid-song.
    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -12_000 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);
        try {
          const revealBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');
          expect(
            await revealSchedule(request, seed.roomCode, seed.currentScheduleId!),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', revealBefore);

          assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
          assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
        } finally {
          await clients.close();
        }
      },
    );
  });
});
