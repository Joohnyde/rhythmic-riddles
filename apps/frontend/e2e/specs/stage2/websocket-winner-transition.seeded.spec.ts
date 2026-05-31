import { expect, test } from '@playwright/test';
import { nextSchedule, revealSchedule } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { withGameFixture } from '../../utils/fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
  observedBackendTypes,
  settle,
} from '../../utils/ws-capture';
import { normalized } from '../../utils/ws-test-assertions';

function isWinnerStage(stage: unknown): boolean {
  return ['winner', '3'].includes(normalized(stage));
}

function latestWelcome(
  frames: Parameters<typeof lastFrameOfType>[0],
): Record<string, unknown> | undefined {
  return lastFrameOfType(frames, 'welcome')?.json;
}

test.describe('Winner transition', () => {
  test('advance to winner', async ({ browser, request }) => {
    await withGameFixture(request, 'SONGS_REVEALED', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);

      try {
        const tvNextBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_next');
        const tvWelcomeBefore = countBackendWsFramesOfType(clients.tv.frames, 'welcome');

        expect(await nextSchedule(request, seed.roomCode)).toBeLessThan(400);

        await expect
          .poll(
            () =>
              countBackendWsFramesOfType(clients.tv.frames, 'song_next') > tvNextBefore ||
              countBackendWsFramesOfType(clients.tv.frames, 'welcome') > tvWelcomeBefore,
          )
          .toBeTruthy();

        const revealBefore = countBackendWsFramesOfType(clients.tv.frames, 'song_reveal');
        expect(await revealSchedule(request, seed.roomCode, seed.nextScheduleId!)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'song_reveal', revealBefore);

        const welcomeBeforeContinue = countBackendWsFramesOfType(clients.tv.frames, 'welcome');
        const nextBeforeContinue = countBackendWsFramesOfType(clients.tv.frames, 'song_next');

        expect(await nextSchedule(request, seed.roomCode)).toBeLessThan(500);

        await expect
          .poll(
            () =>
              countBackendWsFramesOfType(clients.tv.frames, 'welcome') > welcomeBeforeContinue ||
              countBackendWsFramesOfType(clients.tv.frames, 'song_next') > nextBeforeContinue,
          )
          .toBeTruthy();

        const tvTypes = observedBackendTypes(clients.tv.frames);
        expect(tvTypes).toContain('song_reveal');

        // With maxAlbums=3, finishing the first category should transition out of the
        // current schedule, but not necessarily to winner. Winner is covered by the
        // dedicated WINNER recovery fixture below.
        expect(
          tvTypes.includes('welcome') || tvTypes.includes('song_next'),
          `expected transition frame after final song of current category; got ${tvTypes.join(',')}`,
        ).toBeTruthy();
      } finally {
        await clients.close();
      }
    });
  });

  test('winner recovery', async ({ browser, request }) => {
    await withGameFixture(request, 'WINNER', async (seed) => {
      const admin = await connectRole(browser, 'admin', seed.roomCode);
      const tv = await connectRole(browser, 'tv', seed.roomCode);

      try {
        const adminWelcome = latestWelcome(admin.frames);
        const tvWelcome = latestWelcome(tv.frames);

        expect(isWinnerStage(adminWelcome?.stage)).toBeTruthy();
        expect(isWinnerStage(tvWelcome?.stage)).toBeTruthy();

        const adminSongFrameCount =
          countBackendWsFramesOfType(admin.frames, 'song_next') +
          countBackendWsFramesOfType(admin.frames, 'song_reveal') +
          countBackendWsFramesOfType(admin.frames, 'song_repeat');

        const tvSongFrameCount =
          countBackendWsFramesOfType(tv.frames, 'song_next') +
          countBackendWsFramesOfType(tv.frames, 'song_reveal') +
          countBackendWsFramesOfType(tv.frames, 'song_repeat');

        await settle(750);

        expect(
          countBackendWsFramesOfType(admin.frames, 'song_next') +
            countBackendWsFramesOfType(admin.frames, 'song_reveal') +
            countBackendWsFramesOfType(admin.frames, 'song_repeat'),
        ).toBe(adminSongFrameCount);

        expect(
          countBackendWsFramesOfType(tv.frames, 'song_next') +
            countBackendWsFramesOfType(tv.frames, 'song_reveal') +
            countBackendWsFramesOfType(tv.frames, 'song_repeat'),
        ).toBe(tvSongFrameCount);
      } finally {
        await admin.close();
        await tv.close();
      }
    });
  });
});
