import { expect, Page } from '@playwright/test';
import { connectedRouteFor, loginPath, Role } from '../utils/env';
import { selectors } from '../utils/selectors';

export class LoginPage {
  constructor(private readonly page: Page) {}

  async open(role: Role): Promise<void> {
    await this.page.goto(loginPath(role));
    await this.expectVisible(role);
  }

  async login(role: Role, roomCode: string): Promise<void> {
    await selectors.roomCodeInput(this.page, role).fill(roomCode);
    await Promise.all([
      this.page.waitForURL(connectedRouteFor(role), { timeout: 10_000 }).catch(() => null),
      selectors.loginButton(this.page, role).click(),
    ]);
  }

  async expectVisible(role: Role): Promise<void> {
    await expect(selectors.roomCodeInput(this.page, role)).toBeVisible();
    await expect(selectors.loginButton(this.page, role)).toBeVisible();
  }

  async expectConnected(role: Role): Promise<void> {
    await expect(this.page).toHaveURL(connectedRouteFor(role));
  }
}
