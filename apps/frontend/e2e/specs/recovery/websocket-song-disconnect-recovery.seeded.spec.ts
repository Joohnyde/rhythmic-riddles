import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { createInterrupt } from '../../utils/api-client';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
  settle,
} from '../../utils/ws-capture';
import { expectSongsWelcome } from '../../utils/ws-test-assertions';

test.describe('Disconnect recovery', () => {
  test('tv replacement recovery', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        await clients.tv.close();
        await settle(1000);
        const replacement = await connectRole(browser, 'tv', seed.roomCode);
        try {
          await expectBackendWsFrameType(replacement.frames, 'welcome');
          expectSongsWelcome(replacement.frames, seed.currentScheduleId);
        } finally {
          await replacement.close();
        }
      } finally {
        await clients.admin.close().catch(() => undefined);
      }
    });
  });

  test('admin paused recovery', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_LISTENING', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const tvPauseBefore = countBackendWsFramesOfType(clients.tv.frames, 'pause');
        expect(await createInterrupt(request, seed.roomCode, null)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'pause', tvPauseBefore);
        await clients.admin.close();
        await settle(1000);
        const replacementAdmin = await connectRole(browser, 'admin', seed.roomCode);
        try {
          const welcome = lastFrameOfType(replacementAdmin.frames, 'welcome')?.json;
          expectSongsWelcome(replacementAdmin.frames, seed.currentScheduleId);
          expect(JSON.stringify(welcome)).toContain(seed.currentScheduleId!);
        } finally {
          await replacementAdmin.close();
        }
      } finally {
        await clients.tv.close().catch(() => undefined);
      }
    });
  });
});
