import { expect, test } from '@playwright/test';
import {
  answerInterrupt,
  createInterrupt,
  replaySchedule,
  revealSchedule,
} from '../../utils/api-client';
import { attemptRoleConnection, connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
  expectBackendWsFrameTypeAfter,
  expectNoAdditionalFramesOfType,
  lastFrameOfType,
  settle,
} from '../../utils/ws-capture';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';

test.describe('Socket uniqueness', () => {
  test('duplicate tv ignores reveal', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      const duplicateTv = await attemptRoleConnection(browser, 'tv', seed.roomCode);

      try {
        const originalRevealBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');
        const duplicateRevealBefore = countBackendWsFramesOfType(duplicateTv.frames, 'song_reveal');

        expect(await revealSchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );

        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', originalRevealBefore);
        await expectNoAdditionalFramesOfType(
          duplicateTv.frames,
          'song_reveal',
          duplicateRevealBefore,
        );

        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await duplicateTv.close();
        await clients.close();
      }
    });
  });

  test('duplicate admin ignores answer', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', 0);

        const pause = lastFrameOfType(clients.tv.frames, 'pause')?.json;
        const duplicateAdmin = await attemptRoleConnection(browser, 'admin', seed.roomCode);

        try {
          const originalAdminAnswerBefore = countBackendWsFramesOfType(
            clients.admin.frames,
            'answer',
          );
          const duplicateAdminAnswerBefore = countBackendWsFramesOfType(
            duplicateAdmin.frames,
            'answer',
          );

          expect(
            await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), true),
          ).toBeLessThan(400);

          await expectBackendWsFrameTypeAfter(
            clients.admin.frames,
            'answer',
            originalAdminAnswerBefore,
          );
          await expectNoAdditionalFramesOfType(
            duplicateAdmin.frames,
            'answer',
            duplicateAdminAnswerBefore,
          );
        } finally {
          await duplicateAdmin.close();
        }
      } finally {
        await clients.close();
      }
    });
  });

  test('close duplicate tv safe', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      const duplicateTv = await attemptRoleConnection(browser, 'tv', seed.roomCode);

      try {
        await duplicateTv.close();
        await settle(500);

        const originalRevealBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');
        expect(await revealSchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', originalRevealBefore);
      } finally {
        await clients.close();
      }
    });
  });

  test('replace original tv', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        await clients.tv.close();
        await settle(500);

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          await expectBackendWsFrameType(replacementTv.frames, 'welcome');
          expect(countBackendWsFramesOfType(replacementTv.frames, 'welcome')).toBe(1);

          const welcome = lastFrameOfType(replacementTv.frames, 'welcome')?.json;
          expect(String(welcome?.scheduleId)).toBe(seed.currentScheduleId);
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('replace original admin', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        await clients.admin.close();
        await settle(500);

        const replacementAdmin = await connectRole(browser, 'admin', seed.roomCode);
        try {
          await expectBackendWsFrameType(replacementAdmin.frames, 'welcome');
          expect(countBackendWsFramesOfType(replacementAdmin.frames, 'welcome')).toBe(1);

          const welcome = lastFrameOfType(replacementAdmin.frames, 'welcome')?.json;
          expect(String(welcome?.scheduleId)).toBe(seed.currentScheduleId);
          expect(JSON.stringify(welcome).toLowerCase()).toContain('reveal');
        } finally {
          await replacementAdmin.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('replacement tv restores answer', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        expect(await createInterrupt(request, seed.roomCode, seed.teams[0].id)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'pause', 0);

        const pause = lastFrameOfType(clients.admin.frames, 'pause')?.json;

        await clients.tv.close();
        await settle(500);

        const adminAnswerBeforeMissingTv = countBackendWsFramesOfType(
          clients.admin.frames,
          'answer',
        );
        expect(
          await answerInterrupt(request, seed.roomCode, String(pause?.interruptId), true),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(
          clients.admin.frames,
          'answer',
          adminAnswerBeforeMissingTv,
        );

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
          const replacementAnswerBefore = countBackendWsFramesOfType(
            replacementTv.frames,
            'answer',
          );

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

  test('one frame per active client', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const adminRepeatBefore = countBackendWsFramesOfType(clients.admin.frames, 'song_repeat');
        const tvRepeatBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_repeat');

        expect(await replaySchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );

        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'song_repeat', adminRepeatBefore);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_repeat', tvRepeatBefore);
        await settle(500);

        expect(countBackendWsFramesOfType(clients.admin.frames, 'song_repeat')).toBe(
          adminRepeatBefore + 1,
        );
        expect(countBackendWsFramesOfType(clients.tv.frames, 'song_repeat')).toBe(
          tvRepeatBefore + 1,
        );

        const adminRevealBefore = countBackendWsFramesOfType(clients.admin.frames, 'song_reveal');
        const tvRevealBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');

        expect(await revealSchedule(request, seed.roomCode, seed.currentScheduleId!)).toBeLessThan(
          400,
        );

        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'song_reveal', adminRevealBefore);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', tvRevealBefore);
        await settle(500);

        expect(countBackendWsFramesOfType(clients.admin.frames, 'song_reveal')).toBe(
          adminRevealBefore + 1,
        );
        expect(countBackendWsFramesOfType(clients.tv.frames, 'song_reveal')).toBe(
          tvRevealBefore + 1,
        );
      } finally {
        await clients.close();
      }
    });
  });

  test('duplicates keep isolation', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'A' }, async (roomA) => {
      await withGameFixture(request, 'SONGS_LISTENING', { roomPrefix: 'B' }, async (roomB) => {
        const roomAClients = await connectAdminAndTv(browser, roomA.roomCode);
        const roomBClients = await connectAdminAndTv(browser, roomB.roomCode);
        const roomBDuplicateTv = await attemptRoleConnection(browser, 'tv', roomB.roomCode);

        try {
          const bActiveRevealBefore = countBackendWsFramesOfType(
            roomBClients.tv.frames,
            'song_reveal',
          );
          const bDuplicateRevealBefore = countBackendWsFramesOfType(
            roomBDuplicateTv.frames,
            'song_reveal',
          );

          expect(
            await revealSchedule(request, roomA.roomCode, roomA.currentScheduleId!),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(roomAClients.tv.frames, 'song_reveal', 0);

          await expectNoAdditionalFramesOfType(
            roomBClients.tv.frames,
            'song_reveal',
            bActiveRevealBefore,
          );
          await expectNoAdditionalFramesOfType(
            roomBDuplicateTv.frames,
            'song_reveal',
            bDuplicateRevealBefore,
          );
        } finally {
          await roomBDuplicateTv.close();
          await roomAClients.close();
          await roomBClients.close();
        }
      });
    });
  });
});
