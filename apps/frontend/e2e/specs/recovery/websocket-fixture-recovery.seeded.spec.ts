import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv } from '../../utils/e2e-session';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';
import { lastFrameOfType } from '../../utils/ws-capture';

function expectedStageAliases(type: string): Array<string | number> {
  if (type === 'LOBBY') return [0, '0', 'lobby', 'teams'];
  if (type === 'ALBUMS') return [1, '1', 'albums', 'album', 'categories'];
  if (type === 'WINNER') return [3, '3', 'winner', 'winners', 'finished'];
  return [2, '2', 'songs', 'song', 'listening'];
}

function normalized(value: unknown): string {
  return String(value)
    .toLowerCase()
    .replace(/[_\s-]/g, '');
}

function stageMatches(actual: unknown, expectedAliases: Array<string | number>): boolean {
  return expectedAliases.some((expected) => normalized(actual) === normalized(expected));
}

test.describe('Recovery snapshots', () => {
  for (const type of ['LOBBY', 'ALBUMS', 'SONGS_LISTENING', 'SONGS_REVEALED', 'WINNER'] as const) {
    test(`${type} recovery welcome`, async ({ browser, request }) => {
      await withGameFixture(request, type, async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode);
        try {
          const adminWelcome = lastFrameOfType(clients.admin.frames, 'welcome')?.json;
          const tvWelcome = lastFrameOfType(clients.tv.frames, 'welcome')?.json;

          expect(adminWelcome, 'Admin must receive welcome frame').toBeTruthy();
          expect(tvWelcome, 'TV must receive welcome frame').toBeTruthy();
          expect(
            stageMatches(adminWelcome?.stage, expectedStageAliases(type)),
            `Admin stage was ${JSON.stringify(adminWelcome?.stage)}`,
          ).toBeTruthy();
          expect(
            stageMatches(tvWelcome?.stage, expectedStageAliases(type)),
            `TV stage was ${JSON.stringify(tvWelcome?.stage)}`,
          ).toBeTruthy();

          if (type === 'SONGS_LISTENING' || type === 'SONGS_REVEALED') {
            expect(JSON.stringify(adminWelcome)).toContain(seed.currentScheduleId!);
            expect(JSON.stringify(tvWelcome)).toContain(seed.currentScheduleId!);
          }

          assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
          assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
        } finally {
          await clients.close();
        }
      });
    });
  }
});
