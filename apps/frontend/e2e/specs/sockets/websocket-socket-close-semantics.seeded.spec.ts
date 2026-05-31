import { expect, test } from '@playwright/test';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withDeterministicFixture } from '../../utils/deterministic-fixture-api';
import { countBackendWsFramesOfType, settle } from '../../utils/ws-capture';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

test.describe('Socket close semantics', () => {
  test('close tv during team pause', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'TEAM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const adminAnswerBefore = countBackendWsFramesOfType(clients.admin.frames, 'answer');

        await clients.tv.close();
        await settle(500);

        const replacementTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementTv.frames, seed.currentScheduleId);
          expect(JSON.stringify(welcome)).toContain(seed.teams[0].id);
          expect(countBackendWsFramesOfType(clients.admin.frames, 'answer')).toBe(
            adminAnswerBefore,
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

  test('close admin during system pause', async ({ browser, request }) => {
    await withDeterministicFixture(request, 'SYSTEM_PAUSED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        await clients.admin.close();
        await settle(500);

        const replacementAdmin = await connectRole(browser, 'admin', seed.roomCode);
        try {
          const welcome = expectSongsWelcome(replacementAdmin.frames, seed.currentScheduleId);
          expect(String(welcome.scheduleId)).toBe(seed.currentScheduleId);
        } finally {
          await replacementAdmin.close();
        }
      } finally {
        await clients.tv.close().catch(() => undefined);
        await clients.admin.close().catch(() => undefined);
      }
    });
  });
});
