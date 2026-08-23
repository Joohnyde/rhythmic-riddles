import { expect, test } from '@playwright/test';
import { withGameFixture } from '../../utils/fixture-api';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import { pickAlbum, startCategory } from '../../utils/api-client';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
  settle,
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
    await withGameFixture(request, 'ALBUMS', async (seed) => {
      const clients = await connectAdminAndTv(browser, seed.roomCode);
      try {
        const categoryId = seed.categories[0].id;
        const beforePicked = countBackendWsFramesOfType(clients.tv.frames, 'album_picked');
        expect(await pickAlbum(request, seed.roomCode, categoryId, seed.teams[0].id)).toBeLessThan(
          400,
        );
        await expectBackendWsFrameTypeAfter(clients.tv.frames, 'album_picked', beforePicked);
        await clients.tv.close();
        await settle(500);

        const recoveredTv = await connectRole(browser, 'tv', seed.roomCode);
        try {
          const welcome = lastFrameOfType(recoveredTv.frames, 'welcome')?.json;
          expect(welcome?.stage).toBe('albums');
          expect(JSON.stringify(welcome?.albums)).toContain(categoryId);
          expect(JSON.stringify(welcome?.selected)).toContain(categoryId);
          assertAllBackendFramesHaveFrontendContract(recoveredTv.frames);
          await expect(recoveredTv.page.getByTestId('tv-selected-album')).toBeVisible();
        } finally {
          await recoveredTv.close();
        }
      } finally {
        await clients.close();
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
