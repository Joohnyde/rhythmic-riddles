import { expect, Page } from '@playwright/test';
import { selectors } from '../utils/selectors';

export class AdminPage {
  constructor(private readonly page: Page) {}

  async expectLobby(): Promise<void> {
    await expect(selectors.adminLobbyPage(this.page)).toBeVisible();
  }
  async expectAlbums(): Promise<void> {
    await expect(selectors.adminAlbumsPage(this.page)).toBeVisible();
  }
  async expectSongs(): Promise<void> {
    await expect(selectors.adminSongsPage(this.page)).toBeVisible();
  }
  async expectWinner(): Promise<void> {
    await expect(selectors.adminWinnerPage(this.page)).toBeVisible();
  }
}
