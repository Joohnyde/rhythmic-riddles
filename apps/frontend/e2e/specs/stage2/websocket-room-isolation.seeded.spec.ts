import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  resolveSystemInterrupt,
  revealSchedule,
} from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  expectNoAdditionalFramesOfType,
  lastFrameOfType,
  settle,
} from '../../utils/ws-capture';

test.describe('Room isolation', () => {
  test('pause answer isolation', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'X' }, async (roomA) => {
      await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'Y' }, async (roomB) => {
        const a = await connectAdminAndTv(browser, roomA.roomCode);
        const bTv = await connectRole(browser, 'tv', roomB.roomCode);
        try {
          const bPauseBefore = countBackendWsFramesOfType(bTv.frames, 'pause');
          const bAnswerBefore = countBackendWsFramesOfType(bTv.frames, 'answer');
          const aPauseBefore = countBackendWsFramesOfType(a.tv.frames, 'pause');

          expect(await createInterrupt(request, roomA.roomCode, roomA.teams[0].id)).toBeLessThan(
            400,
          );
          await expectBackendWsFrameTypeAfter(a.tv.frames, 'pause', aPauseBefore);
          await expectNoAdditionalFramesOfType(bTv.frames, 'pause', bPauseBefore);

          const pause = lastFrameOfType(a.tv.frames, 'pause')?.json;
          const aAnswerBefore = countBackendWsFramesOfType(a.tv.frames, 'answer');
          expect(
            await answerInterrupt(request, roomA.roomCode, String(pause?.interruptId), true),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(a.tv.frames, 'answer', aAnswerBefore);
          await settle();

          expect(countBackendWsFramesOfType(bTv.frames, 'answer')).toBe(bAnswerBefore);
        } finally {
          await a.close();
          await bTv.close();
        }
      });
    });
  });

  test('system pause isolation', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'M' }, async (roomA) => {
      await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'N' }, async (roomB) => {
        const a = await connectAdminAndTv(browser, roomA.roomCode);
        const bTv = await connectRole(browser, 'tv', roomB.roomCode);
        try {
          const bPauseBefore = countBackendWsFramesOfType(bTv.frames, 'pause');
          const bSolvedBefore = countBackendWsFramesOfType(bTv.frames, 'error_solved');
          const aPauseBefore = countBackendWsFramesOfType(a.tv.frames, 'pause');

          expect(await createInterrupt(request, roomA.roomCode, null)).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(a.tv.frames, 'pause', aPauseBefore);
          await expectNoAdditionalFramesOfType(bTv.frames, 'pause', bPauseBefore);

          const aSolvedBefore = countBackendWsFramesOfType(a.tv.frames, 'error_solved');
          expect(
            await resolveSystemInterrupt(request, roomA.roomCode, roomA.currentScheduleId!),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(a.tv.frames, 'error_solved', aSolvedBefore);
          await settle();

          expect(countBackendWsFramesOfType(bTv.frames, 'error_solved')).toBe(bSolvedBefore);
        } finally {
          await a.close();
          await bTv.close();
        }
      });
    });
  });

  test('reveal isolation', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'Q' }, async (roomA) => {
      await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'Z' }, async (roomB) => {
        const a = await connectAdminAndTv(browser, roomA.roomCode);
        const bTv = await connectRole(browser, 'tv', roomB.roomCode);
        try {
          const bRevealBefore = countBackendWsFramesOfType(bTv.frames, 'song_reveal');
          const aRevealBefore = countBackendWsFramesOfType(a.tv.frames, 'song_reveal');

          expect(
            await revealSchedule(request, roomA.roomCode, roomA.currentScheduleId!),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(a.tv.frames, 'song_reveal', aRevealBefore);
          await settle();

          expect(countBackendWsFramesOfType(bTv.frames, 'song_reveal')).toBe(bRevealBefore);
        } finally {
          await a.close();
          await bTv.close();
        }
      });
    });
  });
});
