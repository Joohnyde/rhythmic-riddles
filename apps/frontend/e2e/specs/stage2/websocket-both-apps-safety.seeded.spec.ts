import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  revealSchedule,
  resolveSystemInterrupt,
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

test.describe('Both-apps safety', () => {
  test('missing tv blocks answer', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', 0);
        const pause = lastFrameOfType(clients.admin.frames, 'pause')?.json;

        await clients.tv.close();
        await settle(500);

        const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), true),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.admin.frames, 'answer', adminAnswerBefore);
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('tv reconnect allows answer', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', 0);
        const pause = lastFrameOfType(clients.admin.frames, 'pause')?.json;

        await clients.tv.close();
        await settle(500);
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), true),
        ).toBeGreaterThanOrEqual(400);

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const replacementAnswerBefore = countBackendWsFramesOfType(
            replacementTv.frames,
            'answer',
          );
          const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
          expect(
            await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), true),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.admin.frames, 'answer', adminAnswerBefore);
          await expectBackendWsFrameTypeAfter(
            replacementTv.frames,
            'answer',
            replacementAnswerBefore,
          );
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('missing tv blocks continue', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', 0);

        await clients.tv.close();
        await settle(500);

        const solvedBefore = countBackendWsFramesOfType(clients.admin.frames, 'error_solved');
        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId!),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.admin.frames, 'error_solved', solvedBefore);
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('missing tv blocks reveal', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        await clients.tv.close();
        await settle(500);

        const adminRevealBefore = countBackendWsFramesOfType(clients.admin.frames, 'song_reveal');
        expect(
          await revealSchedule(request, seed.roomCode, seed.currentScheduleId!),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(
          clients.admin.frames,
          'song_reveal',
          adminRevealBefore,
        );
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });
});
