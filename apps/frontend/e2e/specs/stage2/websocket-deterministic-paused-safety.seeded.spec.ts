import { expect, test } from '@playwright/test';
import { answerInterrupt, resolveSystemInterrupt } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  expectNoAdditionalFramesOfType,
  settle,
} from '../../utils/ws-capture';

test.describe('Deterministic paused safety', () => {
  test('seeded team pause tv recovery', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'TEAM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        await clients.tv.close();
        await settle(500);

        const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');
        expect(
          await answerInterrupt(request, seed.roomCode, seed.currentTeamInterruptId!, true),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(clients.admin.frames, 'answer', adminAnswerBefore);

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const adminAnswerAfter = countBackendWsFramesOfType(clients.admin.frames, 'answer');
          const tvAnswerAfter = countBackendWsFramesOfType(replacementTv.frames, 'answer');

          expect(
            await answerInterrupt(request, seed.roomCode, seed.currentTeamInterruptId!, true),
          ).toBeLessThan(400);

          await expectBackendWsFrameTypeAfter(clients.admin.frames, 'answer', adminAnswerAfter);
          await expectBackendWsFrameTypeAfter(replacementTv.frames, 'answer', tvAnswerAfter);
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });

  test('seeded system pause tv recovery', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        await clients.tv.close();
        await settle(500);

        const adminSolvedBefore = countBackendWsFramesOfType(clients.admin.frames, 'error_solved');
        expect(
          await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
        ).toBeGreaterThanOrEqual(400);
        await expectNoAdditionalFramesOfType(
          clients.admin.frames,
          'error_solved',
          adminSolvedBefore,
        );

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const adminSolvedAfter = countBackendWsFramesOfType(clients.admin.frames, 'error_solved');
          const tvSolvedAfter = countBackendWsFramesOfType(replacementTv.frames, 'error_solved');

          expect(
            await resolveSystemInterrupt(request, seed.roomCode, seed.currentScheduleId),
          ).toBeLessThan(400);

          await expectBackendWsFrameTypeAfter(
            clients.admin.frames,
            'error_solved',
            adminSolvedAfter,
          );
          await expectBackendWsFrameTypeAfter(replacementTv.frames, 'error_solved', tvSolvedAfter);
        } finally {
          await replacementTv.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
        await clients.tv.close().catch(() => undefined);
      }
    });
  });
});
