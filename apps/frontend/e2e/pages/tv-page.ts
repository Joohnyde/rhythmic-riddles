import { expect, Page } from '@playwright/test';
import { TV_CONNECTED_ROUTE } from '../utils/env';

export class TvPage {
  constructor(private readonly page: Page) {}

  async expectConnectedAwayFromLogin(): Promise<void> {
    await expect(this.page).toHaveURL(TV_CONNECTED_ROUTE);
  }

  async expectQuestionVisible(): Promise<void> {
    await expect(this.page.getByTestId('tv-current-question')).toBeVisible();
  }

  async expectAnswerVisible(): Promise<void> {
    await expect(this.page.getByTestId('tv-answer-visible').or(this.page.getByTestId('tv-current-answer'))).toBeVisible();
  }

  async expectSystemErrorVisible(): Promise<void> {
    await expect(this.page.getByTestId('tv-system-error')).toBeVisible();
  }
}
