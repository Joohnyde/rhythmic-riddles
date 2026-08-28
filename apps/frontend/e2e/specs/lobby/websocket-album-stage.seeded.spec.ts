import { expect, test } from '@playwright/test';
import { pickAlbum } from '../../utils/api-client';
import { connectAdminAndTv, connectRole } from '../../utils/e2e-session';
import type { E2eFixtureSeed } from '../../utils/fixture-api';
import { withGameFixture } from '../../utils/fixture-api';
import {
  countBackendWsFramesOfType,
  expectBackendWsFrameTypeAfter,
  lastFrameOfType,
} from '../../utils/ws-capture';
import { assertAllBackendFramesHaveFrontendContract } from '../../utils/ws-contracts';

const SCRAMBLED_ALBUM_NAMES = ['Zulu', 'Alpha', 'Mike', 'Bravo', 'Echo', 'Charlie'] as const;

function compareCanonicalText(left: string, right: string): number {
  const maxIndex = Math.min(left.length, right.length);
  for (let index = 0; index < maxIndex; index += 1) {
    const difference = left.charCodeAt(index) - right.charCodeAt(index);
    if (difference !== 0) return difference;
  }
  return left.length - right.length;
}

function canonicalCategoryIds(seed: E2eFixtureSeed): string[] {
  return [...seed.categories]
    .sort((left, right) => {
      const byName = compareCanonicalText(
        left.name.normalize('NFKD').toLowerCase(),
        right.name.normalize('NFKD').toLowerCase(),
      );
      return byName !== 0 ? byName : compareCanonicalText(left.id, right.id);
    })
    .map((category) => category.id);
}

async function adminRenderedAlbumIds(page: import('@playwright/test').Page): Promise<string[]> {
  return page
    .locator('[data-testid="admin-album-list"] .stage1-album-card[data-album-id]')
    .evaluateAll((cards) => cards.map((card) => (card as HTMLElement).dataset['albumId'] ?? ''));
}

async function tvMarqueeFirstGroupAlbumIds(
  page: import('@playwright/test').Page,
): Promise<string[]> {
  return page
    .locator('.stage1-tv-album-group')
    .first()
    .locator('.stage1-album-card[data-album-id]')
    .evaluateAll((cards) => cards.map((card) => (card as HTMLElement).dataset['albumId'] ?? ''));
}

async function focusAlbumIds(
  page: import('@playwright/test').Page,
  testId: string,
): Promise<string[]> {
  return page
    .getByTestId(testId)
    .locator('.stage1-focus-album-card[data-album-id]')
    .evaluateAll((cards) => cards.map((card) => (card as HTMLElement).dataset['albumId'] ?? ''));
}

test.describe('Album stage', () => {
  test('Admin selects and starts a Stage 1 album through the real UI', async ({
    browser,
    request,
  }) => {
    await withGameFixture(
      request,
      'ALBUMS',
      {
        categoryCount: 6,
        maxAlbums: 6,
        categoryNames: SCRAMBLED_ALBUM_NAMES,
        forceNonCanonicalBackendAlbumOrder: true,
      },
      async (seed) => {
        const clients = await connectAdminAndTv(browser, seed.roomCode, {
          viewport: { width: 1440, height: 900 },
        });
        try {
          const canonicalIds = canonicalCategoryIds(seed);
          const adminWelcome = lastFrameOfType(clients.admin.frames, 'welcome')?.json as
            | {
                albums?: Array<{ id: string }>;
                team?: { id: string; name: string; image: string } | null;
              }
            | undefined;
          const rawWelcomeIds = adminWelcome?.albums?.map((album) => album.id) ?? [];
          expect(new Set(rawWelcomeIds)).toEqual(new Set(canonicalIds));
          expect(rawWelcomeIds).not.toEqual(canonicalIds);

          // Picker rotation is backend-authoritative and ordered by pick counts/UUID, so fixture
          // creation order must not be treated as chooser order. Validate the UI against the actual
          // Stage 1 welcome picker instead of hard-coding Team A or Team B.
          const expectedPicker = adminWelcome?.team;
          expect(expectedPicker).toBeTruthy();
          expect(seed.teams.map((team) => team.id)).toContain(expectedPicker?.id);
          await expect(clients.admin.page.getByTestId('admin-album-list')).toBeVisible();
          expect(await adminRenderedAlbumIds(clients.admin.page)).toEqual(canonicalIds);

          const selectedId = canonicalIds[2];
          const selectedCategory = seed.categories.find((category) => category.id === selectedId)!;
          let pickRequests = 0;
          let startRequests = 0;
          clients.admin.page.on('request', (httpRequest) => {
            if (
              httpRequest.method() === 'PUT' &&
              httpRequest.url().includes(`/categories/${selectedId}/pick`)
            ) {
              pickRequests += 1;
            }
            if (
              httpRequest.method() === 'POST' &&
              httpRequest.url().includes(`/categories/${selectedId}/start`)
            ) {
              startRequests += 1;
            }
          });

          const albumPickedBefore = countBackendWsFramesOfType(clients.tv.frames, 'album_picked');
          await clients.admin.page
            .getByTestId(`admin-album-card-${selectedId}`)
            .getByRole('button')
            .click();
          const pickDialog = clients.admin.page
            .getByRole('dialog')
            .filter({ hasText: `Choose ${selectedCategory.name}?` });
          await expect(pickDialog).toBeVisible();
          await pickDialog.getByRole('button', { name: 'YES' }).click();

          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'album_picked', albumPickedBefore);
          expect(pickRequests).toBe(1);
          assertAllBackendFramesHaveFrontendContract(clients.tv.frames);

          await expect(clients.admin.page.getByTestId('admin-albums-page')).toHaveAttribute(
            'data-focus-phase',
            'animating',
          );
          const focus = clients.admin.page.getByTestId('admin-album-focus');
          await expect(focus).toBeVisible();
          const selectedFocusCard = focus.getByTestId(`admin-album-focus-card-${selectedId}`);
          await expect(selectedFocusCard).toHaveAttribute('data-selected', 'true');
          await expect(selectedFocusCard.locator('.stage1-album-team-icon')).toHaveAttribute(
            'src',
            expectedPicker!.image,
          );
          await expect(focus).toHaveAttribute('data-focus-state', 'settled', { timeout: 8_000 });

          const beforeAdminSongsWelcome = countBackendWsFramesOfType(
            clients.admin.frames,
            'welcome',
          );
          const beforeTvSongsWelcome = countBackendWsFramesOfType(clients.tv.frames, 'welcome');
          const play = clients.admin.page.getByTestId('admin-start-songs-button');
          await expect(play).toBeEnabled();
          await play.click();
          const startDialog = clients.admin.page
            .getByRole('dialog')
            .filter({ hasText: 'Start the game?' });
          await expect(startDialog).toBeVisible();
          await startDialog.getByRole('button', { name: 'YES' }).click();

          await expectBackendWsFrameTypeAfter(
            clients.admin.frames,
            'welcome',
            beforeAdminSongsWelcome,
          );
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'welcome', beforeTvSongsWelcome);
          const adminSongsWelcome = lastFrameOfType(clients.admin.frames, 'welcome')?.json as
            { stage?: string } | undefined;
          const tvSongsWelcome = lastFrameOfType(clients.tv.frames, 'welcome')?.json as
            { stage?: string } | undefined;
          expect(adminSongsWelcome?.stage).toBe('songs');
          expect(tvSongsWelcome?.stage).toBe('songs');
          await expect(clients.admin.page).toHaveURL(/\/admin\/songs(?:$|[/?#])/, {
            timeout: 8_000,
          });
          expect(startRequests).toBe(1);
        } finally {
          await clients.close();
        }
      },
    );
  });

  test('selected album recovery replays focus and preserves canonical rendered position', async ({
    browser,
    request,
  }) => {
    await withGameFixture(
      request,
      'ALBUMS',
      {
        categoryCount: 6,
        maxAlbums: 6,
        categoryNames: SCRAMBLED_ALBUM_NAMES,
        forceNonCanonicalBackendAlbumOrder: true,
      },
      async (seed) => {
        const viewport = { width: 1440, height: 900 };
        const clients = await connectAdminAndTv(browser, seed.roomCode, { viewport });
        const canonicalIds = canonicalCategoryIds(seed);
        const initialWelcome = lastFrameOfType(clients.tv.frames, 'welcome')?.json as
          { albums?: Array<{ id: string }> } | undefined;
        const rawInitialIds = initialWelcome?.albums?.map((album) => album.id) ?? [];
        expect(new Set(rawInitialIds)).toEqual(new Set(canonicalIds));
        expect(rawInitialIds).not.toEqual(canonicalIds);
        const categoryId = seed.categories[5].id;
        const expectedIndex = canonicalIds.indexOf(categoryId);
        try {
          await expect(clients.tv.page.locator('.stage1-tv-album-group').first()).toBeVisible();
          expect(await tvMarqueeFirstGroupAlbumIds(clients.tv.page)).toEqual(canonicalIds);
          expect((await tvMarqueeFirstGroupAlbumIds(clients.tv.page)).indexOf(categoryId)).toBe(
            expectedIndex,
          );

          const beforePicked = countBackendWsFramesOfType(clients.tv.frames, 'album_picked');
          expect(
            await pickAlbum(request, seed.roomCode, categoryId, seed.teams[0].id),
          ).toBeLessThan(400);
          await expectBackendWsFrameTypeAfter(clients.tv.frames, 'album_picked', beforePicked);
          assertAllBackendFramesHaveFrontendContract(clients.tv.frames);
        } finally {
          await clients.close();
        }

        const recoveredTv = await connectRole(browser, 'tv', seed.roomCode, { viewport });
        try {
          const welcome = lastFrameOfType(recoveredTv.frames, 'welcome')?.json as
            { albums: Array<{ id: string }>; selected?: { categoryId?: string } } | undefined;
          expect(welcome?.selected?.categoryId).toBe(categoryId);
          // The fixture guarantees a non-canonical backend transport order. Membership remains
          // authoritative, while the browser must normalize visual positions independently.
          const rawRecoveredIds = welcome?.albums.map((album) => album.id) ?? [];
          expect(new Set(rawRecoveredIds)).toEqual(new Set(canonicalIds));
          expect(rawRecoveredIds).not.toEqual(canonicalIds);

          const page = recoveredTv.page;
          await expect(page.getByTestId('tv-albums-page')).toHaveAttribute(
            'data-focus-phase',
            'animating',
            { timeout: 4_000 },
          );
          const focus = page.getByTestId('tv-album-focus');
          await expect(focus).toBeVisible();
          await expect(focus).toHaveAttribute('data-focus-state', /ready|animating/);
          const selectedCard = focus.getByTestId(`tv-album-focus-card-${categoryId}`);
          await expect(selectedCard).toHaveAttribute('data-selected', 'true');
          await expect(selectedCard.locator('.stage1-album-team-icon')).toHaveAttribute(
            'src',
            /e2e-a\.png/,
          );

          await expect(focus).toHaveAttribute('data-focus-state', 'settled', { timeout: 8_000 });
          await expect(page.getByTestId('tv-albums-page')).toHaveAttribute(
            'data-focus-phase',
            'settled',
          );

          const recoveredOrder = await focusAlbumIds(page, 'tv-album-focus');
          expect(recoveredOrder).toEqual(canonicalIds);
          expect(recoveredOrder.indexOf(categoryId)).toBe(expectedIndex);
          await expect(focus.locator('.stage1-focus-album-card[data-selected="true"]')).toHaveCount(
            1,
          );
          assertAllBackendFramesHaveFrontendContract(recoveredTv.frames);
        } finally {
          await recoveredTv.close();
        }
      },
    );
  });
});
