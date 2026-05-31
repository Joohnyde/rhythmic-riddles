import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  nextSchedule,
  replaySchedule,
  resolveSystemInterrupt,
  revealSchedule,
} from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
  settle,
} from '../../utils/ws-capture';
import { expectFrameOrder, expectUuid } from '../../utils/ws-test-assertions';

test.describe('Stage2 events', () => {
  test('repeat reveal order', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const aRepeat = countBackendWsFramesOfType(clients.admin.frames, 'song_repeat');
        const tRepeat = countBackendWsFramesOfType(clients.tv.frames, 'song_repeat');
        expect(await replaySchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'song_repeat', aRepeat);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_repeat', tRepeat);

        const aReveal = countBackendWsFramesOfType(clients.admin.frames, 'song_reveal');
        const tReveal = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');
        expect(await revealSchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'song_reveal', aReveal);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', tReveal);

        expectFrameOrder(clients.admin.frames, 'song_repeat', 'song_reveal');
        expectFrameOrder(clients.tv.frames, 'song_repeat', 'song_reveal');
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('pause answer sequence', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const adminPauseBefore = countBackendWsFramesOfType(clients.admin.frames, 'pause');
        const tvPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', adminPauseBefore);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', tvPauseBefore);

        const pause = lastFrameOfType(clients.admin.frames, 'pause')?.json;
        expect(pause?.answeringTeamId).toBe(seed.teams[0].id);
        expectUuid(pause?.interruptId);

        const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
        const tvAnswerBefore = countBackendWsFramesOfType(clients.tv.frames, 'answer');
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), true),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'answer', adminAnswerBefore);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'answer', tvAnswerBefore);

        expectFrameOrder(clients.admin.frames, 'pause', 'answer');
        expectFrameOrder(clients.tv.frames, 'pause', 'answer');
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('system pause resolve sequence', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const tvSolvedBefore = countBackendWsFramesOfType(clients.tv.frames, 'error_solved');

        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', tvSolvedBefore);

        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('revealed advances next', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const tvNextBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_next');
        const tvWelcomeBefore = countBackendWsFramesOfType(clients.tv.frames, 'welcome');
        const adminNextBefore = countBackendWsFramesOfType(clients.admin.frames, 'song_next');
        const adminWelcomeBefore = countBackendWsFramesOfType(clients.admin.frames, 'welcome');

        expect(await nextSchedule(request, seed.roomCode)).toBeLessThan(500);

        await expect
          .poll(
            () =>
              countBackendWsFramesOfType(clients.tv.frames, 'song_next') > tvNextBefore ||
              countBackendWsFramesOfType(clients.tv.frames, 'welcome') > tvWelcomeBefore,
          )
          .toBeTruthy();
        await expect
          .poll(
            () =>
              countBackendWsFramesOfType(clients.admin.frames, 'song_next') > adminNextBefore ||
              countBackendWsFramesOfType(clients.admin.frames, 'welcome') > adminWelcomeBefore,
          )
          .toBeTruthy();

        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('invalid ids emit no frames', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const tv = await connectRole(browser, 'tv', seed.roomCode);
      try {
        const repeatBefore = countBackendWsFramesOfType(tv.frames, 'song_repeat');
        const revealBefore = countBackendWsFramesOfType(tv.frames, 'song_reveal');
        const answerBefore = countBackendWsFramesOfType(tv.frames, 'answer');
        await replaySchedule(request, seed.roomCode, '00000000-0000-0000-0000-000000000000');
        await revealSchedule(request, seed.roomCode, '00000000-0000-0000-0000-000000000000');
        await answerInterrupt(request, seed.roomCode, '00000000-0000-0000-0000-000000000000', true);
        await settle();
        expect(countBackendWsFramesOfType(tv.frames, 'song_repeat')).toBe(repeatBefore);
        expect(countBackendWsFramesOfType(tv.frames, 'song_reveal')).toBe(revealBefore);
        expect(countBackendWsFramesOfType(tv.frames, 'answer')).toBe(answerBefore);
      } finally {
        await tv.close();
      }
    });
  });
});
