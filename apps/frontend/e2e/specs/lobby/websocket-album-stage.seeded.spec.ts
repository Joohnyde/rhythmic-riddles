import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { pickAlbum, startCategory } from '../../utils/api-client';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
} from '../../utils/ws-capture';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';

test.describe('Album stage', () => {
  test('album recovery welcome', async ({ browser, request }) => {
    await withGameFixture(request, 'ALBUMS', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const tvWelcome = lastFrameOfType(clients.tv.frames, 'welcome')?.json;
        const adminWelcome = lastFrameOfType(clients.admin.frames, 'welcome')?.json;
        expect(JSON.stringify(tvWelcome)).toContain(seed.categories[0].id);
        expect(JSON.stringify(adminWelcome)).toContain(seed.categories[0].id);
        assertAllBackendFramesHaveFrontendContract(clients.admin.frames);
        assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
      } finally {
        await clients.close();
      }
    });
  });

  test('selected album recovery restores the reveal state', async ({ browser, request }) => {
    await withGameFixture(request, 'ALBUMS', { categoryCount: 6, maxAlbums: 6 }, async (seed) => {
      const tv = await connectRole(browser, 'tv', seed.roomCode, {
        viewport: { width: 260, height: 720 },
      });
      try {
        const categoryId = seed.categories[5].id;
        const beforePicked = countBackendWsFramesOfType(tv.frames, 'album_picked');
        expect(await pickAlbum(request, seed.roomCode, categoryId, seed.teams[0].id)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(tv.frames, 'album_picked', beforePicked);
      } finally {
        await tv.close();
      }

      const recoveredTv = await connectRole(browser, 'tv', seed.roomCode, {
        viewport: { width: 260, height: 720 },
      });
      try {
        const welcome = lastFrameOfType(recoveredTv.frames, 'welcome')?.json as
          | {
              albums?: Array<{ id: string }>;
              selected?: { categoryId?: string };
            }
          | undefined;
        const categoryId = seed.categories[5].id;

        expect(welcome?.selected?.categoryId).toBe(categoryId);
        expect(welcome?.albums?.map((album) => album.id)).toEqual(
          seed.categories.map((category) => category.id),
        );
        await expect(recoveredTv.page.getByTestId('tv-album-focus')).toBeVisible();
        await expect(
          recoveredTv.page.getByTestId(`tv-album-focus-card-${categoryId}`),
        ).toHaveAttribute('data-selected', 'true');
      } finally {
        await recoveredTv.close();
      }
    });
  });

  test('album pick starts songs', async ({ browser, request }) => {
    await withGameFixture(request, 'ALBUMS', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const categoryId = seed.categories[0].id;
        const beforePicked = countBackendWsFramesOfType(clients.tv.frames, 'album_picked');
        expect(await pickAlbum(request, seed.roomCode, categoryId, seed.teams[0].id)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'album_picked', beforePicked);

        const beforeAdminWelcome = countBackendWsFramesOfType(clients.admin.frames, 'welcome');
        const beforeTvWelcome = countBackendWsFramesOfType(clients.tv.frames, 'welcome');
        expect(await startCategory(request, seed.roomCode, categoryId)).toBeLessThan(400);
        await expectBackendWsFrameTypeAfter(clients.admin.frames, 'welcome', beforeAdminWelcome);
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'welcome', beforeTvWelcome);
      } finally {
        await clients.close();
      }
    });
  });
});
