import { expect, Locator, Page } from '@playwright/test';
import { ADMIN_CONNECTED_ROUTE } from '../utils/env';
import { byTestIdOrRole } from '../utils/selectors';

export class AdminPage {
  constructor(private readonly page: Page) {}

  async expectConnectedAwayFromLogin(): Promise<void> {
    await expect(this.page).toHaveURL(ADMIN_CONNECTED_ROUTE);
  }

  createTeamNameInput(): Locator {
    return this.page.getByTestId('admin-create-team-name-input').or(
      this.page.getByPlaceholder(/team|ime|name/i),
    );
  }

  createTeamImageInput(): Locator {
    return this.page.getByTestId('admin-create-team-image-input').or(
      this.page.getByPlaceholder(/image|slika|url/i),
    );
  }

  createTeamButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-create-team-button', () =>
      this.page.getByRole('button', { name: /create|dodaj|napravi/i }),
    );
  }

  startGameButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-start-game-button', () =>
      this.page.getByRole('button', { name: /start|pocni|počni/i }),
    );
  }

  repeatButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-repeat-button', () =>
      this.page.getByRole('button', { name: /refresh|repeat|ponovi/i }),
    );
  }

  revealButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-reveal-button', () =>
      this.page.getByRole('button', { name: /reveal|otkrij/i }),
    );
  }

  nextSongButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-next-song-button', () =>
      this.page.getByRole('button', { name: /dalje|next/i }),
    );
  }

  correctAnswerButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-answer-correct-button', () =>
      this.page.getByRole('button', { name: /tacno|tačno|correct/i }),
    );
  }

  incorrectAnswerButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-answer-incorrect-button', () =>
      this.page.getByRole('button', { name: /netacno|netačno|incorrect/i }),
    );
  }

  resolveErrorButton(): Locator {
    return byTestIdOrRole(this.page, 'admin-resolve-error-button', () =>
      this.page.getByRole('button', { name: /nastavi|resolve|continue/i }),
    );
  }
}
