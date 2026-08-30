import { APIRequestContext, Browser, BrowserContext, expect, Page } from '@playwright/test';
import { LoginPage } from '../pages/login-page';
import { createRoom } from './api-client';
import { connectRole, ConnectedClient } from './e2e-session';
import { BACKEND_URL } from './env';
import { deleteGameFixture } from './fixture-api';

export type ProductTeam = { id: string; name: string; buttonCode: string };
export type ProductCatalog = { id: string; name: string }[];

const PRODUCT_ALBUM_ASSET_IDS = [
  '3c8ed2c0-df3d-4639-b6a6-81550e473cca',
  '11f109d1-e4ac-4dd8-99fe-108035273677',
  'edf940fb-250d-4f5a-ad61-f1d4f254a435',
  'c18a96dd-4991-4e28-9c04-d61ca05bc81b',
  '6214ed07-03df-41c7-a1fa-b1a9b9e9bd01',
  '6031c504-b9da-4e17-8dd5-58abaeeeb3b3',
] as const;

export type ProductSession = {
  roomCode: string;
  catalog: ProductCatalog;
  admin: Page;
  adminContext: BrowserContext;
  tv: ConnectedClient;
  close(): Promise<void>;
};

export async function createProductSession(
  browser: Browser,
  request: APIRequestContext,
  maxSongs: number,
  maxAlbums: number,
): Promise<ProductSession> {
  // Game creation belongs to the future preparation application, not to the runtime Admin UI.
  // Arrange the finite game over HTTP, then test only event-time behavior through real browsers.
  const roomCode = await createRoom(request, maxSongs, maxAlbums);
  const catalog = await attachCatalog(request, roomCode, maxSongs, maxAlbums);

  const adminContext = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const admin = await adminContext.newPage();
  const login = new LoginPage(admin);
  await login.open('admin');
  await login.login('admin', roomCode);
  await login.expectConnected('admin');
  const tv = await connectRole(browser, 'tv', roomCode, { viewport: { width: 1440, height: 900 } });

  return {
    roomCode,
    catalog,
    admin,
    adminContext,
    tv,
    close: async () => {
      await tv.close().catch(() => undefined);
      await adminContext.close().catch(() => undefined);
      await deleteGameFixture(request, roomCode);
    },
  };
}

export async function attachCatalog(
  request: APIRequestContext,
  roomCode: string,
  maxSongs: number,
  maxAlbums: number,
): Promise<ProductCatalog> {
  if (maxAlbums > PRODUCT_ALBUM_ASSET_IDS.length) {
    throw new Error(
      `Product E2E supports at most ${PRODUCT_ALBUM_ASSET_IDS.length} repository-backed album assets; requested ${maxAlbums}.`,
    );
  }

  const catalog = Array.from({ length: maxAlbums }, (_, index) => ({
    id: crypto.randomUUID(),
    name: `Product Album ${index + 1}`,
  }));
  const response = await request.post(
    `${BACKEND_URL}/api/e2e/v1/game-fixtures/${roomCode}/catalog`,
    {
      data: {
        categories: catalog.map((category, categoryIndex) => ({
          id: category.id,
          pickedByTeamId: null,
          ordinalNumber: null,
          done: false,
          album: {
            // Use real repository-backed cover IDs so product journeys do not hide expected 404s.
            id: PRODUCT_ALBUM_ASSET_IDS[categoryIndex],
            name: category.name,
            customQuestion: `Product question ${categoryIndex + 1}`,
            tracks: Array.from({ length: maxSongs }, (_, songIndex) => ({
              customAnswer: `Product answer ${categoryIndex + 1}.${songIndex + 1}`,
              schedule: null,
            })),
          },
        })),
      },
    },
  );
  expect(
    response.ok(),
    `attach product catalog failed: ${response.status()} ${await response.text()}`,
  ).toBeTruthy();
  return catalog;
}

export async function addTeamThroughUi(
  request: APIRequestContext,
  admin: Page,
  name: string,
  buttonCode: string,
): Promise<ProductTeam> {
  await admin.getByTestId('admin-create-team-name-input').fill(name);
  const endpoint = `${BACKEND_URL}/api/e2e/v1/game-fixtures/receiver/${buttonCode}`;
  const responses = await Promise.all([request.post(endpoint), request.post(endpoint)]);
  for (const response of responses) expect(response.ok()).toBeTruthy();
  await expect(admin.getByTestId('admin-create-team-button')).toBeEnabled();
  await admin.getByTestId('admin-create-team-button').click();
  const row = admin.locator('[data-testid^="admin-team-row-"]').filter({ hasText: name });
  await expect(row).toBeVisible();
  const testId = await row.getAttribute('data-testid');
  return { id: testId!.replace('admin-team-row-', ''), name, buttonCode };
}

export async function startGame(admin: Page): Promise<void> {
  await expect(admin.getByTestId('admin-start-game-button')).toBeEnabled();
  await admin.getByTestId('admin-start-game-button').click();
  const dialog = admin.getByRole('dialog').filter({ hasText: 'Start the game?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'START GAME' }).click();
  await expect(admin.getByTestId('admin-albums-page')).toBeVisible();
}

export async function pickAndStartAlbum(
  admin: Page,
  tv: Page,
  category: { id: string; name: string },
): Promise<void> {
  const card = admin.getByTestId(`admin-album-card-${category.id}`);
  await expect(card).toBeVisible();
  await card.getByRole('button').click();
  const pickDialog = admin.getByRole('dialog').filter({ hasText: `Choose ${category.name}?` });
  await pickDialog.getByRole('button', { name: 'YES' }).click();
  await expect(admin.getByTestId('admin-album-focus')).toHaveAttribute(
    'data-focus-state',
    'settled',
    { timeout: 10_000 },
  );
  await expect(tv.getByTestId('tv-album-focus')).toBeVisible();
  await admin.getByTestId('admin-start-songs-button').click();
  const startDialog = admin.getByRole('dialog').filter({ hasText: 'Start the game?' });
  await startDialog.getByRole('button', { name: 'YES' }).click();
  await expect(admin.getByTestId('admin-song-round-page')).toBeVisible();
  await expect(tv.getByTestId('tv-song-round-page')).toBeVisible();
}

export async function buzz(
  request: APIRequestContext,
  team: ProductTeam,
  admin: Page,
  tv: Page,
): Promise<void> {
  const response = await request.post(
    `${BACKEND_URL}/api/e2e/v1/game-fixtures/receiver/${team.buttonCode}`,
  );
  expect(response.ok()).toBeTruthy();
  await expect(admin.getByTestId('answering-team-name')).toHaveText(team.name);
  await expect(tv.getByTestId('answering-team-name')).toHaveText(team.name);
}

export async function expectScore(page: Page, team: ProductTeam, score: number): Promise<void> {
  await expect(page.getByTestId(`scoreboard-team-score-${team.id}`)).toHaveText(String(score));
}

export async function next(admin: Page): Promise<void> {
  await expect(admin.getByTestId('admin-next-song-after-reveal-button')).toBeEnabled();
  await admin.getByTestId('admin-next-song-after-reveal-button').click();
}
