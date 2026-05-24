import { APIRequestContext, Browser, expect, Page } from '@playwright/test';
import { LoginPage } from '../pages/login-page';
import {
  changeStage,
  createTeam,
  pickAlbum,
  startCategory,
} from './api-client';
import {
  CapturedWsFrame,
  captureReceivedWebSocketFrames,
  expectBackendWsFrameType,
  lastFrameOfType,
} from './ws-capture';

export type Stage2Context = {
  roomCode: string;
  teamId: string;
  categoryId: string;
  scheduleId: string;
  adminPage: Page;
  tvPage: Page;
  adminFrames: CapturedWsFrame[];
  tvFrames: CapturedWsFrame[];
  close: () => Promise<void>;
};

function extractFirstAvailableCategoryId(welcome: Record<string, unknown>): string {
  const albums = welcome.albums;

  expect(Array.isArray(albums), 'albums welcome should include albums array').toBeTruthy();

  const firstAvailable = (albums as Array<Record<string, unknown>>).find((album) =>
    album.chosenBy === null || album.chosenBy === undefined || album.chosenBy === 'null'
  ) ?? (albums as Array<Record<string, unknown>>)[0];

  expect(firstAvailable, 'at least one album/category is required in e2e DB').toBeTruthy();

  const id = firstAvailable.id ?? firstAvailable.categoryId;
  expect(typeof id, 'album/category id should be present in welcome').toBe('string');

  return id as string;
}

function extractScheduleId(frame: Record<string, unknown>): string {
  const scheduleId = frame.scheduleId;
  expect(typeof scheduleId, 'songs welcome/song_next frame should include scheduleId').toBe('string');
  return scheduleId as string;
}

export async function seedStage2Room(
  browser: Browser,
  request: APIRequestContext,
): Promise<Stage2Context> {
  const { createRoom } = await import('./api-client');

  const roomCode = await createRoom(request);
  const team = await createTeam(request, roomCode, `Stage2 Team ${Date.now()}`);

  const adminContext = await browser.newContext();
  const tvContext = await browser.newContext();

  const adminPage = await adminContext.newPage();
  const tvPage = await tvContext.newPage();

  const adminFrames = captureReceivedWebSocketFrames(adminPage);
  const tvFrames = captureReceivedWebSocketFrames(tvPage);

  await new LoginPage(adminPage).openAdmin();
  await new LoginPage(tvPage).openTv();
  await new LoginPage(adminPage).login(roomCode);
  await new LoginPage(tvPage).login(roomCode);

  await expectBackendWsFrameType(adminFrames, 'welcome');
  await expectBackendWsFrameType(tvFrames, 'welcome');

  const stageChangeStatus = await changeStage(request, roomCode, 1);
  expect(stageChangeStatus, 'stage change to albums should be accepted').toBeLessThan(400);

  await expect
    .poll(() => {
      const welcome = lastFrameOfType(adminFrames, 'welcome')?.json;
      return welcome?.stage === 'albums';
    }, { message: 'admin should receive albums welcome after stage change' })
    .toBeTruthy();

  const albumsWelcome = lastFrameOfType(adminFrames, 'welcome')!.json!;
  const categoryId = extractFirstAvailableCategoryId(albumsWelcome);

  await pickAlbum(request, roomCode, categoryId, null);
  await expectBackendWsFrameType(tvFrames, 'album_picked');

  await startCategory(request, roomCode, categoryId);

  await expect
    .poll(() => {
      const latestWelcome = lastFrameOfType(adminFrames, 'welcome')?.json;
      const latestNext = lastFrameOfType(adminFrames, 'song_next')?.json;

      return latestWelcome?.stage === 'songs' || latestNext?.type === 'song_next';
    }, { timeout: 15_000, message: 'admin should enter songs stage after category start' })
    .toBeTruthy();

  await expect
    .poll(() => {
      const latestWelcome = lastFrameOfType(tvFrames, 'welcome')?.json;
      const latestNext = lastFrameOfType(tvFrames, 'song_next')?.json;

      return latestWelcome?.stage === 'songs' || latestNext?.type === 'song_next';
    }, { timeout: 15_000, message: 'tv should enter songs stage after category start' })
    .toBeTruthy();

  const scheduleFrame =
    lastFrameOfType(adminFrames, 'song_next')?.json ??
    lastFrameOfType(adminFrames, 'welcome')?.json;

  const scheduleId = extractScheduleId(scheduleFrame!);

  return {
    roomCode,
    teamId: team.id,
    categoryId,
    scheduleId,
    adminPage,
    tvPage,
    adminFrames,
    tvFrames,
    close: async () => {
      await adminContext.close();
      await tvContext.close();
    },
  };
}
