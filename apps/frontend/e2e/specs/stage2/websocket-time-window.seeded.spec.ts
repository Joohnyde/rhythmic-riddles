import { expect, test } from '@playwright/test';
import { answerInterrupt, createInterrupt, resolveSystemInterrupt } from '../../utils/api-client';
import { connectAdminAndTv } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  expectNoAdditionalFramesOfType,
  lastFrameOfType,
} from '../../utils/ws-capture';
import { expectUuid } from '../../utils/ws-test-assertions';

test.describe('Buzz window', () => {
  test('buzz inside window', async ({ browser, request }) => {
    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -500 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);
        try {
          const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
          expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', pauseBefore);

          const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
          expect(pause?.answeringTeamId).toBe(seed.teams[0].id);
          expectUuid(pause?.interruptId);
        } finally {
          await clients.close();
        }
      },
    );
  });

  test('buzz after window rejected', async ({ browser, request }) => {
    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -12_000 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);
        try {
          const pauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
          expect(
            await createInterrupt(request, seed.roomCode, seed.teams[0].id),
          ).toBeGreaterThanOrEqual(400);
          await expectNoAdditionalFramesOfType(clients.tv.frames, 'pause', pauseBefore);
        } finally {
          await clients.close();
        }
      },
    );
  });

  test('resolved pause keeps window', async ({ browser, request }) => {
    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -1_000 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);
        try {
          const systemPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
          expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', systemPauseBefore);

          const solvedBefore = countBackendWsFramesOfType(clients.tv.frames, 'error_solved');
          expect(
            await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId!),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'error_solved', solvedBefore);

          const teamPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
          expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', teamPauseBefore);
        } finally {
          await clients.close();
        }
      },
    );
  });

  test('incorrect answer resumes listening', async ({ browser, request }) => {
    await withGameFixture(
      request,
      'SONGS_LISTENING',
      { activeStartedOffsetMillis: -500 },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);
        try {
          expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);
          const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;

          const revealBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');
          const nextBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_next');
          const answerBefore = countBackendWsFramesOfType(clients.tv.frames, 'answer');

          expect(
            await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), false),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'answer', answerBefore);
          await expectNoAdditionalFramesOfType(clients.tv.frames, 'song_reveal', revealBefore);
          await expectNoAdditionalFramesOfType(clients.tv.frames, 'song_next', nextBefore);

          const answer = lastFrameOfType(clients.tv.frames, 'answer')?.json;
          expect(answer?.correct).toBe(false);
        } finally {
          await clients.close();
        }
      },
    );
  });
});
