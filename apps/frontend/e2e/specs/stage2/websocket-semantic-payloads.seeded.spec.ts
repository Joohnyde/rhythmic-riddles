import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  nextSchedule,
  replaySchedule,
  resolveSystemInterrupt,
} from '../../utils/api-client';
import { connectAdminAndTv } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
} from '../../utils/ws-capture';
import { expectUuid } from '../../utils/ws-test-assertions';

test.describe('Payload semantics', () => {
  test('pause answer payload ids', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', 0);
        const pause = lastFrameOfType(clients.admin.frames, 'pause')?.json;
        expect(pause?.answeringTeamId).toBe(seed.teams[0].id);
        expectUuid(pause?.interruptId);

        const answerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), false),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'answer', answerBefore);
        const answer = lastFrameOfType(clients.admin.frames, 'answer')?.json;

        expect(answer?.teamId).toBe(seed.teams[0].id);
        expect(answer?.scheduleId).toBe(seed.currentScheduleId);
        expect(answer?.correct).toBe(false);
      } finally {
        await clients.close();
      }
    });
  });

  test('repeat payload remaining', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const repeatBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_repeat');
        expect(await replaySchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_repeat', repeatBefore);

        const repeat = lastFrameOfType(clients.tv.frames, 'song_repeat')?.json;
        expect(typeof repeat?.remaining).toBe('number');
        expect(Number(repeat?.remaining)).toBeGreaterThanOrEqual(0);
      } finally {
        await clients.close();
      }
    });
  });

  test('next payload schedule changes', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const nextBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_next');
        const welcomeBefore = countBackendWsFramesOfType(clients.tv.frames, 'welcome');
        expect(await nextSchedule(request, seed.roomCode)).toBeLessThan(500);

        await expect
          .poll(
            () =>
              countBackendWsFramesOfType(clients.tv.frames, 'song_next') > nextBefore ||
              countBackendWsFramesOfType(clients.tv.frames, 'welcome') > welcomeBefore,
          )
          .toBeTruthy();

        const next = lastFrameOfType(clients.tv.frames, 'song_next')?.json;
        if (next) {
          expect(next.scheduleId).not.toBe(seed.currentScheduleId);
          expect(next.scheduleId).toBe(seed.nextScheduleId);
          expect(typeof next.question).toBe('string');
          expect(typeof next.answer).toBe('string');
        }
      } finally {
        await clients.close();
      }
    });
  });

  test('error solved scenario payload', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const solvedBefore = countBackendWsFramesOfType(clients.tv.frames, 'error_solved');

        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
        ).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', solvedBefore);

        const solved = lastFrameOfType(clients.tv.frames, 'error_solved')?.json;
        expect(typeof solved?.previousScenario).toBe('number');
        expect(Number(solved?.previousScenario)).toBeGreaterThanOrEqual(0);
        expect(Number(solved?.previousScenario)).toBeLessThanOrEqual(4);
        expect(Number(solved?.previousScenario)).not.toBe(3);
      } finally {
        await clients.close();
      }
    });
  });
});
