import { expect, Page } from '@playwright/test';
import { selectors } from '../utils/selectors';

export class TvPage {
  constructor(private readonly page: Page) {}

  async expectLobby(): Promise<void> {
    await expect(selectors.tvLobbyPage(this.page)).toBeVisible();
  }
  async expectAlbums(): Promise<void> {
    await expect(selectors.tvAlbumsPage(this.page)).toBeVisible();
  }
  async expectSongs(): Promise<void> {
    await expect(selectors.tvSongsPage(this.page)).toBeVisible();
  }
  async expectWinner(): Promise<void> {
    await expect(selectors.tvWinnerPage(this.page)).toBeVisible();
  }
}
