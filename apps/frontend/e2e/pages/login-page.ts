import { expect, Page } from '@playwright/test';
import { connectedRouteFor, pagePathFor, Role } from '../utils/env';
import { loginButton, roomCodeInput } from '../utils/selectors';

export class LoginPage {
  constructor(private readonly page: Page) {}

  async openRole(role: Role): Promise<void> {
    await this.page.goto(pagePathFor(role));
    await this.expectLoginVisible();
  }

  async openTv(): Promise<void> {
    await this.openRole('tv');
  }

  async openAdmin(): Promise<void> {
    await this.openRole('admin');
  }

  async login(roomCode: string): Promise<void> {
    await roomCodeInput(this.page).fill(roomCode);

    await Promise.all([
      this.page.waitForURL(/\/(admin\/)?(lobby|albums|songs|winner)$/, { timeout: 10_000 }).catch(() => null),
      loginButton(this.page).click(),
    ]);
  }

  async expectLoginVisible(): Promise<void> {
    await expect(roomCodeInput(this.page)).toBeVisible();
    await expect(loginButton(this.page)).toBeVisible();
  }

  async expectConnected(role: Role): Promise<void> {
    await expect(this.page).toHaveURL(connectedRouteFor(role));
  }
}
